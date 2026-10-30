package com.fredypot.domain

import com.fredypot.domain.PeriodMarking.Change
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PeriodMarkingTest {

    private val d: LocalDate = LocalDate.of(2026, 3, 10)
    private val existing = Period(id = 7, start = d, end = d.plusDays(4))

    @Test
    fun `start on a free day creates an open period`() {
        assertEquals(Change.Save(Period(start = d)), PeriodMarking.markStart(emptyList(), d, 5))
    }

    @Test
    fun `start inside a period moves its start`() {
        val change = PeriodMarking.markStart(listOf(existing), d.plusDays(1), 5)
        assertEquals(Change.Save(existing.copy(start = d.plusDays(1))), change)
    }

    @Test
    fun `start shortly before a period extends it backwards`() {
        val change = PeriodMarking.markStart(listOf(existing), d.minusDays(2), 5)
        assertEquals(Change.Save(existing.copy(start = d.minusDays(2))), change)
    }

    @Test
    fun `end closes the latest period that started before`() {
        val open = Period(id = 8, start = d.plusDays(28))
        val change = PeriodMarking.markEnd(listOf(existing, open), d.plusDays(33))
        assertEquals(Change.Save(open.copy(end = d.plusDays(33))), change)
    }

    @Test
    fun `end without recent start is rejected`() {
        assertEquals(Change.NoStartBefore, PeriodMarking.markEnd(listOf(existing), d.plusDays(20)))
        assertEquals(Change.NoStartBefore, PeriodMarking.markEnd(emptyList(), d))
    }
}
