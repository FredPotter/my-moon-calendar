package com.fredypot.domain

import java.time.LocalDate

/** Logged menstruation. [end] is null while the period is still ongoing (or the end wasn't marked). */
data class Period(
    val id: Long = 0,
    val start: LocalDate,
    val end: LocalDate? = null,
)

enum class Symptom {
    CRAMPS,
    BREAST_TENDERNESS,
    BLOATING,
    HEADACHE,
    BACK_PAIN,
    ACNE,
    MOOD_SWINGS,
    FATIGUE,
    NAUSEA,
    SPOTTING,
    HIGH_LIBIDO,
    OVULATION_PAIN,
}

/** Cervical mucus observation, ordered from least to most fertile. */
enum class Mucus(val fertile: Boolean) {
    DRY(false),
    STICKY(false),
    CREAMY(false),
    WATERY(true),
    EGG_WHITE(true),
}

enum class LhTest { NEGATIVE, POSITIVE }

data class DayLog(
    val date: LocalDate,
    val symptoms: Set<Symptom> = emptySet(),
    val mucus: Mucus? = null,
    val lhTest: LhTest? = null,
    /** Basal body temperature, °C. */
    val temperature: Double? = null,
) {
    val isEmpty: Boolean
        get() = symptoms.isEmpty() && mucus == null && lhTest == null && temperature == null
}

enum class OvulationSignal { TEMPERATURE, LH_TEST, MUCUS, OVULATION_PAIN }

data class OvulationEstimate(
    val date: LocalDate,
    val signals: Set<OvulationSignal>,
)

enum class Confidence { LOW, MEDIUM, HIGH }

data class CycleStats(
    val averageCycleLength: Double,
    /** Standard deviation of the next cycle length prediction, days. */
    val predictionSd: Double,
    val averagePeriodLength: Int,
    val lutealLength: Double,
    /** Number of completed cycles that were used for the estimate. */
    val cyclesUsed: Int,
    val confidence: Confidence,
)

/** One cycle in the history: from [start] up to the next period start (exclusive). */
data class CycleRecord(
    val periodId: Long,
    val start: LocalDate,
    val periodEnd: LocalDate?,
    val periodLength: Int?,
    /** Null for the current, unfinished cycle. */
    val cycleLength: Int?,
    val ovulation: OvulationEstimate?,
    /** True when the cycle length was excluded from statistics as implausible (e.g. a missed log). */
    val isOutlier: Boolean,
)

enum class DayKind { PERIOD, PREDICTED_PERIOD, OVULATION, FERTILE }

data class Prediction(
    val today: LocalDate,
    val stats: CycleStats,
    /** Newest first. */
    val history: List<CycleRecord>,
    val days: Map<LocalDate, DayKind>,
    val cycleDay: Int?,
    val nextPeriodStart: LocalDate?,
    val nextOvulation: LocalDate?,
    val isFertileToday: Boolean,
    /** How many days the period is late, 0 if not late. */
    val delayDays: Int,
)
