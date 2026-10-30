package com.fredypot.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * On-device cycle prediction: a Bayesian calendar model combined with symptothermal ovulation detection.
 *
 * 1. The user's mean cycle length is a Normal posterior: a population prior (Bull et al., 2019, ~600k cycles)
 *    updated with the user's recent cycles, weighted by recency. With few cycles the prior dominates, so
 *    predictions start sensible and become personal as history grows. Implausible lengths (usually a
 *    forgotten log) are excluded instead of skewing the mean.
 * 2. Ovulation in each cycle is detected from logged signs: a basal temperature shift (3-over-6 rule),
 *    an LH surge, the cervical mucus peak day and ovulation pain, combined by reliability.
 * 3. The luteal phase (ovulation → next period) is far more stable than the follicular phase, so it is learned
 *    from cycles with detected ovulation, and once ovulation is detected in the current cycle the next
 *    period is predicted as ovulation + luteal length instead of from the calendar average.
 * 4. Fertile window = 5 days before ovulation through the day after (sperm and egg survival, Wilcox et al., 1995).
 */
object CyclePredictor {

    const val POPULATION_CYCLE_MEAN = 29.3
    /** Spread of individual mean cycle lengths across the population: prior uncertainty of the user's mean. */
    const val PRIOR_MEAN_SD = 4.0
    /** Typical cycle-to-cycle variability within one person. */
    const val PRIOR_WITHIN_SD = 3.0
    /** Weight of [PRIOR_WITHIN_SD] in pseudo-cycles. */
    const val PRIOR_WITHIN_WEIGHT = 2.0
    const val POPULATION_LUTEAL = 12.4
    const val PRIOR_LUTEAL_WEIGHT = 2.0
    const val POPULATION_PERIOD_LENGTH = 5.0

    const val MIN_CYCLE = 18
    const val MAX_CYCLE = 50
    /** A cycle this many cycles ago has half the weight of the latest one. */
    const val RECENCY_HALF_LIFE = 6.0

    const val FERTILE_DAYS_BEFORE_OVULATION = 5L
    const val FERTILE_DAYS_AFTER_OVULATION = 1L
    const val FUTURE_CYCLES = 6

    private const val MIN_LUTEAL = 7
    private const val MAX_LUTEAL = 18
    private const val MIN_FOLLICULAR = 6
    private const val TEMPERATURE_SHIFT = 0.2

    fun predict(periods: List<Period>, logs: List<DayLog>, today: LocalDate): Prediction {
        val sorted = periods.sortedBy { it.start }
        val logsByDate = logs.associateBy { it.date }

        val cycleLengths = sorted.zipWithNext { a, b -> daysBetween(a.start, b.start) }
        val validLengths = cycleLengths.filter { it in MIN_CYCLE..MAX_CYCLE }

        val ovulations = sorted.mapIndexed { i, period ->
            val nextStart = sorted.getOrNull(i + 1)?.start
            val windowEnd = nextStart?.minusDays(1) ?: today
            detectOvulation(period.start, windowEnd, nextStart, logsByDate, isComplete = nextStart != null)
        }

        val lutealLengths = sorted.indices.mapNotNull { i ->
            val nextStart = sorted.getOrNull(i + 1)?.start ?: return@mapNotNull null
            val ovulation = ovulations[i] ?: return@mapNotNull null
            daysBetween(ovulation.date, nextStart).takeIf { it in MIN_LUTEAL..MAX_LUTEAL }
        }

        val stats = estimateStats(validLengths, sorted, lutealLengths)

        val history = sorted.mapIndexed { i, period ->
            val length = cycleLengths.getOrNull(i)
            CycleRecord(
                periodId = period.id,
                start = period.start,
                periodEnd = period.end,
                periodLength = period.end?.let { daysBetween(period.start, it) + 1 },
                cycleLength = length,
                ovulation = ovulations[i],
                isOutlier = length != null && length !in MIN_CYCLE..MAX_CYCLE,
            )
        }.reversed()

        val last = sorted.lastOrNull()
            ?: return Prediction(today, stats, history, emptyMap(), null, null, null, false, 0)

        val meanLength = stats.averageCycleLength.roundToInt()
        val luteal = stats.lutealLength.roundToInt()
        val currentOvulation = ovulations.last()

        val expectedStart = currentOvulation?.date?.plusDays(luteal.toLong())
            ?: last.start.plusDays(meanLength.toLong())
        val delayDays = if (today.isAfter(expectedStart)) daysBetween(expectedStart, today) else 0
        // When the period is late, the best guess is that it starts any day now.
        val nextStart = if (delayDays > 0) today else expectedStart

        val days = HashMap<LocalDate, DayKind>()
        fun mark(from: LocalDate, to: LocalDate, kind: DayKind) {
            var d = from
            while (!d.isAfter(to)) {
                val existing = days[d]
                if (existing == null || existing.ordinal > kind.ordinal) days[d] = kind
                d = d.plusDays(1)
            }
        }
        fun markOvulation(ovulation: LocalDate) {
            mark(
                ovulation.minusDays(FERTILE_DAYS_BEFORE_OVULATION),
                ovulation.plusDays(FERTILE_DAYS_AFTER_OVULATION),
                DayKind.FERTILE,
            )
            mark(ovulation, ovulation, DayKind.OVULATION)
        }

        val periodLength = stats.averagePeriodLength.toLong()
        sorted.forEachIndexed { i, period ->
            val assumedEnd = period.start.plusDays(periodLength - 1)
            when {
                period.end != null -> mark(period.start, period.end, DayKind.PERIOD)
                // Ongoing period without an end mark: logged up to today, predicted for the remaining days.
                !assumedEnd.isBefore(today) -> {
                    mark(period.start, minOf(today, assumedEnd), DayKind.PERIOD)
                    if (assumedEnd.isAfter(today)) mark(today.plusDays(1), assumedEnd, DayKind.PREDICTED_PERIOD)
                }
                else -> mark(period.start, assumedEnd, DayKind.PERIOD)
            }
            val nextPeriodStart = sorted.getOrNull(i + 1)?.start
            val ovulation = ovulations[i]?.date
                ?: nextPeriodStart?.minusDays(luteal.toLong())
                    ?.takeIf { cycleLengths[i] in MIN_CYCLE..MAX_CYCLE }
            if (ovulation != null) markOvulation(ovulation)
        }

        val futureStarts = (0..FUTURE_CYCLES).map { nextStart.plusDays(it.toLong() * meanLength) }
        val currentCycleOvulation = currentOvulation?.date
            ?: expectedStart.minusDays(luteal.toLong())
                .takeIf { daysBetween(last.start, it) >= MIN_FOLLICULAR }
        if (currentOvulation == null && currentCycleOvulation != null) markOvulation(currentCycleOvulation)
        futureStarts.forEachIndexed { k, start ->
            mark(start, start.plusDays(periodLength - 1), DayKind.PREDICTED_PERIOD)
            futureStarts.getOrNull(k + 1)?.let { markOvulation(it.minusDays(luteal.toLong())) }
        }

        val nextOvulation = (listOfNotNull(currentCycleOvulation) + futureStarts.drop(1).map { it.minusDays(luteal.toLong()) })
            .firstOrNull { !it.isBefore(today) }

        val todayKind = days[today]
        return Prediction(
            today = today,
            stats = stats,
            history = history,
            days = days,
            cycleDay = daysBetween(last.start, today) + 1,
            nextPeriodStart = nextStart,
            nextOvulation = nextOvulation,
            isFertileToday = todayKind == DayKind.FERTILE || todayKind == DayKind.OVULATION,
            delayDays = delayDays,
        )
    }

    internal fun estimateStats(
        validLengths: List<Int>,
        periods: List<Period>,
        lutealLengths: List<Int>,
    ): CycleStats {
        // Most recent cycle has weight 1, older ones decay exponentially.
        val weights = validLengths.indices.map { i ->
            0.5.pow((validLengths.size - 1 - i) / RECENCY_HALF_LIFE)
        }
        val weightSum = weights.sum()

        val sampleMean = if (weightSum > 0) validLengths.indices.sumOf { weights[it] * validLengths[it] } / weightSum else 0.0
        val squaredDeviations = validLengths.indices.sumOf { weights[it] * (validLengths[it] - sampleMean).pow(2) }
        val withinVariance = (PRIOR_WITHIN_WEIGHT * PRIOR_WITHIN_SD.pow(2) + squaredDeviations) /
            (PRIOR_WITHIN_WEIGHT + weightSum)

        // Normal-Normal conjugate update of the user's mean cycle length.
        val priorPrecision = 1 / PRIOR_MEAN_SD.pow(2)
        val dataPrecision = weightSum / withinVariance
        val posteriorPrecision = priorPrecision + dataPrecision
        val posteriorMean = (POPULATION_CYCLE_MEAN * priorPrecision + sampleMean * dataPrecision) / posteriorPrecision
        val predictionSd = sqrt(withinVariance + 1 / posteriorPrecision)

        val periodLengths = periods.mapNotNull { p -> p.end?.let { daysBetween(p.start, it) + 1 } }
            .filter { it in 1..12 }
            .takeLast(12)
        val periodLength = ((POPULATION_PERIOD_LENGTH + periodLengths.sum()) / (1 + periodLengths.size))
            .roundToInt().coerceIn(2, 10)

        val luteal = (POPULATION_LUTEAL * PRIOR_LUTEAL_WEIGHT + lutealLengths.takeLast(12).sum()) /
            (PRIOR_LUTEAL_WEIGHT + lutealLengths.takeLast(12).size)

        val confidence = when {
            validLengths.size >= 6 && predictionSd <= 3.5 -> Confidence.HIGH
            validLengths.size >= 3 && predictionSd <= 5.0 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        return CycleStats(
            averageCycleLength = posteriorMean,
            predictionSd = predictionSd,
            averagePeriodLength = periodLength,
            lutealLength = luteal,
            cyclesUsed = validLengths.size,
            confidence = confidence,
        )
    }

    /**
     * Combines ovulation signs logged between [cycleStart] and [windowEnd]. Returns null when there are none.
     * For an unfinished cycle the mucus peak is only trusted once drier mucus has been logged after it.
     */
    internal fun detectOvulation(
        cycleStart: LocalDate,
        windowEnd: LocalDate,
        nextStart: LocalDate?,
        logsByDate: Map<LocalDate, DayLog>,
        isComplete: Boolean,
    ): OvulationEstimate? {
        val logs = generateSequence(cycleStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(windowEnd) }
            .mapNotNull { logsByDate[it] }
            .toList()
        if (logs.isEmpty()) return null

        val earliest = cycleStart.plusDays(MIN_FOLLICULAR.toLong())
        val latest = nextStart?.minusDays(MIN_LUTEAL.toLong())
        fun plausible(date: LocalDate) = !date.isBefore(earliest) && (latest == null || !date.isAfter(latest))

        val candidates = buildList {
            temperatureShiftOvulation(logs)?.let { add(Triple(it, OvulationSignal.TEMPERATURE, 3.0)) }
            logs.firstOrNull { it.lhTest == LhTest.POSITIVE }?.let {
                add(Triple(it.date.plusDays(1), OvulationSignal.LH_TEST, 4.0))
            }
            mucusPeak(logs, isComplete)?.let { add(Triple(it, OvulationSignal.MUCUS, 2.0)) }
            logs.firstOrNull { Symptom.OVULATION_PAIN in it.symptoms }?.let {
                add(Triple(it.date, OvulationSignal.OVULATION_PAIN, 1.0))
            }
        }.filter { plausible(it.first) }
        if (candidates.isEmpty()) return null

        val weightSum = candidates.sumOf { it.third }
        val meanOffset = candidates.sumOf { daysBetween(cycleStart, it.first) * it.third } / weightSum
        return OvulationEstimate(
            date = cycleStart.plusDays(meanOffset.roundToInt().toLong()),
            signals = candidates.map { it.second }.toSet(),
        )
    }

    /**
     * 3-over-6 rule: three consecutive temperatures above the highest of the previous six, the third one
     * at least 0.2 °C above it. Ovulation is the day before the first high temperature.
     */
    internal fun temperatureShiftOvulation(logs: List<DayLog>): LocalDate? {
        val temps = logs.filter { it.temperature != null && it.temperature in 35.0..38.5 }
        for (i in 6..temps.size - 3) {
            val coverLine = temps.subList(i - 6, i).maxOf { it.temperature!! }
            val high = temps.subList(i, i + 3).map { it.temperature!! }
            if (high.all { it > coverLine } && high[2] >= coverLine + TEMPERATURE_SHIFT - 1e-9) {
                return temps[i].date.minusDays(1)
            }
        }
        return null
    }

    /** Last day of fertile (watery / egg-white) mucus before it dries up. */
    internal fun mucusPeak(logs: List<DayLog>, isComplete: Boolean): LocalDate? {
        val withMucus = logs.filter { it.mucus != null }
        val peakIndex = withMucus.indexOfLast { it.mucus!!.fertile }
        if (peakIndex < 0) return null
        val driedUp = withMucus.drop(peakIndex + 1).any { !it.mucus!!.fertile }
        return if (isComplete || driedUp) withMucus[peakIndex].date else null
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()
}
