package com.fredypot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CyclePredictorTest {

    private val day0: LocalDate = LocalDate.of(2026, 1, 1)

    private fun periodsEvery(lengths: List<Int>, periodLength: Int = 5): List<Period> {
        var start = day0
        return buildList {
            add(Period(id = 1, start = start, end = start.plusDays(periodLength - 1L)))
            lengths.forEachIndexed { i, length ->
                start = start.plusDays(length.toLong())
                add(Period(id = i + 2L, start = start, end = start.plusDays(periodLength - 1L)))
            }
        }
    }

    @Test
    fun `no data gives empty prediction`() {
        val prediction = CyclePredictor.predict(emptyList(), emptyList(), day0)
        assertNull(prediction.nextPeriodStart)
        assertTrue(prediction.days.isEmpty())
        assertEquals(Confidence.LOW, prediction.stats.confidence)
    }

    @Test
    fun `single period falls back to population prior`() {
        val period = Period(start = day0, end = day0.plusDays(4))
        val prediction = CyclePredictor.predict(listOf(period), emptyList(), day0.plusDays(3))
        assertEquals(day0.plusDays(29), prediction.nextPeriodStart)
        assertEquals(4, prediction.cycleDay)
    }

    @Test
    fun `regular cycles move estimate towards user's own length`() {
        val periods = periodsEvery(List(8) { 26 })
        val stats = CyclePredictor.predict(periods, emptyList(), periods.last().start.plusDays(1)).stats
        assertEquals(26.0, stats.averageCycleLength, 0.5)
        assertEquals(Confidence.HIGH, stats.confidence)
        assertEquals(5, stats.averagePeriodLength)
    }

    @Test
    fun `recent cycles weigh more than old ones`() {
        val periods = periodsEvery(List(6) { 32 } + List(6) { 27 })
        val stats = CyclePredictor.predict(periods, emptyList(), periods.last().start).stats
        assertTrue(stats.averageCycleLength < 29.5)
    }

    @Test
    fun `implausibly long cycle is treated as missed log`() {
        val periods = periodsEvery(listOf(28, 28, 56, 28, 28))
        val prediction = CyclePredictor.predict(periods, emptyList(), periods.last().start)
        assertEquals(28.0, prediction.stats.averageCycleLength, 0.6)
        assertTrue(prediction.history.single { it.cycleLength == 56 }.isOutlier)
    }

    @Test
    fun `predicted ovulation is luteal phase before next period with fertile window around it`() {
        val periods = periodsEvery(List(6) { 28 })
        val today = periods.last().start.plusDays(1)
        val prediction = CyclePredictor.predict(periods, emptyList(), today)
        val next = prediction.nextPeriodStart!!
        val ovulation = next.minusDays(prediction.stats.lutealLength.toLong())
        assertEquals(DayKind.OVULATION, prediction.days[prediction.nextOvulation])
        assertEquals(DayKind.FERTILE, prediction.days[prediction.nextOvulation!!.minusDays(5)])
        assertEquals(DayKind.FERTILE, prediction.days[prediction.nextOvulation.plusDays(1)])
        assertEquals(DayKind.PREDICTED_PERIOD, prediction.days[next])
        assertEquals(ovulation, prediction.nextOvulation)
    }

    @Test
    fun `3-over-6 temperature rule detects ovulation`() {
        val start = day0
        val temps = listOf(36.4, 36.3, 36.4, 36.5, 36.4, 36.3, 36.4, 36.4, 36.3, 36.4, 36.5, 36.4, 36.4, 36.6, 36.7, 36.8)
        val logs = temps.mapIndexed { i, t -> DayLog(date = start.plusDays(i.toLong()), temperature = t) }
        // First high temperature is day 13 (36.6), cover line 36.5 → 36.8 is ≥ 0.2 above.
        assertEquals(start.plusDays(12), CyclePredictor.temperatureShiftOvulation(logs))
    }

    @Test
    fun `temperature rise below threshold is not ovulation`() {
        val temps = List(9) { 36.4 } + listOf(36.45, 36.5, 36.5)
        val logs = temps.mapIndexed { i, t -> DayLog(date = day0.plusDays(i.toLong()), temperature = t) }
        assertNull(CyclePredictor.temperatureShiftOvulation(logs))
    }

    @Test
    fun `positive LH test in current cycle shifts next period to ovulation plus luteal phase`() {
        val periods = periodsEvery(List(6) { 28 })
        val last = periods.last().start
        val lhDay = last.plusDays(19)
        val logs = listOf(DayLog(date = lhDay, lhTest = LhTest.POSITIVE))
        val today = lhDay.plusDays(2)
        val prediction = CyclePredictor.predict(periods, logs, today)
        val expectedOvulation = lhDay.plusDays(1)
        val expectedNext = expectedOvulation.plusDays(Math.round(prediction.stats.lutealLength))
        assertEquals(expectedNext, prediction.nextPeriodStart)
        assertEquals(DayKind.OVULATION, prediction.days[expectedOvulation])
        assertTrue(OvulationSignal.LH_TEST in prediction.history.first().ovulation!!.signals)
    }

    @Test
    fun `luteal phase is learned from detected ovulations`() {
        val periods = periodsEvery(List(6) { 28 })
        // LH positive on day 16 → ovulation day 17 (offset 16) → luteal 28 - 16 = 12 days in every cycle.
        val logs = periods.dropLast(1).map { DayLog(date = it.start.plusDays(15), lhTest = LhTest.POSITIVE) }
        val stats = CyclePredictor.predict(periods, logs, periods.last().start).stats
        assertEquals(12.1, stats.lutealLength, 0.2)
    }

    @Test
    fun `mucus peak in open cycle requires dry-up`() {
        val logs = listOf(
            DayLog(day0.plusDays(12), mucus = Mucus.CREAMY),
            DayLog(day0.plusDays(13), mucus = Mucus.EGG_WHITE),
        )
        assertNull(CyclePredictor.mucusPeak(logs, isComplete = false))
        val dried = logs + DayLog(day0.plusDays(15), mucus = Mucus.STICKY)
        assertEquals(day0.plusDays(13), CyclePredictor.mucusPeak(dried, isComplete = false))
    }

    @Test
    fun `implausible ovulation signs are ignored`() {
        val logs = mapOf(day0.plusDays(2) to DayLog(day0.plusDays(2), symptoms = setOf(Symptom.OVULATION_PAIN)))
        assertNull(CyclePredictor.detectOvulation(day0, day0.plusDays(27), day0.plusDays(28), logs, isComplete = true))
    }

    @Test
    fun `late period is reported and predicted to start today`() {
        val periods = periodsEvery(List(6) { 28 })
        val today = periods.last().start.plusDays(32)
        val prediction = CyclePredictor.predict(periods, emptyList(), today)
        assertTrue(prediction.delayDays > 0)
        assertEquals(today, prediction.nextPeriodStart)
    }

    @Test
    fun `ongoing period without end shows remaining days as predicted`() {
        val periods = periodsEvery(List(3) { 28 }).toMutableList()
        val lastStart = periods.last().start
        periods[periods.lastIndex] = periods.last().copy(end = null)
        val prediction = CyclePredictor.predict(periods, emptyList(), lastStart.plusDays(1))
        assertEquals(DayKind.PERIOD, prediction.days[lastStart.plusDays(1)])
        assertEquals(DayKind.PREDICTED_PERIOD, prediction.days[lastStart.plusDays(3)])
        assertNotNull(prediction.nextPeriodStart)
        assertFalse(prediction.isFertileToday)
    }
}
