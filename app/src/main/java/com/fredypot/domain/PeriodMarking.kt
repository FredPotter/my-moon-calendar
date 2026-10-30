package com.fredypot.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Turns "period started / ended on this day" taps into changes of the stored periods. */
object PeriodMarking {

    /** Longest period that a single start mark can be stretched to. */
    const val MAX_PERIOD_DAYS = 14

    sealed interface Change {
        data class Save(val period: Period) : Change
        data object NoStartBefore : Change
    }

    /** Period that covers [date], treating an open period as lasting [assumedLength] days. */
    fun periodAt(periods: List<Period>, date: LocalDate, assumedLength: Int): Period? =
        periods.firstOrNull { p ->
            val end = p.end ?: p.start.plusDays(assumedLength - 1L)
            !date.isBefore(p.start) && !date.isAfter(end)
        }

    fun markStart(periods: List<Period>, date: LocalDate, assumedLength: Int): Change {
        periodAt(periods, date, assumedLength)?.let { containing ->
            return Change.Save(containing.copy(start = date))
        }
        // Marking a start a few days before an existing period extends it backwards.
        val soon = periods.firstOrNull { p -> p.start.isAfter(date) && days(date, p.start) <= MAX_PERIOD_DAYS / 2 }
        if (soon != null) return Change.Save(soon.copy(start = date))
        return Change.Save(Period(start = date))
    }

    fun markEnd(periods: List<Period>, date: LocalDate): Change {
        val candidate = periods
            .filter { !it.start.isAfter(date) && days(it.start, date) < MAX_PERIOD_DAYS }
            .maxByOrNull { it.start }
            ?: return Change.NoStartBefore
        return Change.Save(candidate.copy(end = date))
    }

    private fun days(from: LocalDate, to: LocalDate) = ChronoUnit.DAYS.between(from, to)
}
