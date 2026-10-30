package com.fredypot.data

import com.fredypot.domain.DayLog
import com.fredypot.domain.LhTest
import com.fredypot.domain.Mucus
import com.fredypot.domain.Period
import com.fredypot.domain.Symptom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {

    private val day = LocalDate.of(2026, 10, 12)

    @Test
    fun `round trip keeps all data`() {
        val backup = Backup(
            periods = listOf(Period(start = day, end = day.plusDays(4)), Period(start = day.plusDays(29))),
            logs = listOf(
                DayLog(day.plusDays(1), symptoms = setOf(Symptom.CRAMPS, Symptom.FATIGUE)),
                DayLog(day.plusDays(14), mucus = Mucus.EGG_WHITE, lhTest = LhTest.POSITIVE, temperature = 36.45),
            ),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(backup, day))
        assertEquals(backup, decoded)
    }

    @Test
    fun `unknown symptom from a newer version is skipped`() {
        val json = """
            {"format":"mymooncalendar-backup","version":1,"periods":[],
             "dayLogs":[{"date":"2026-10-13","symptoms":["CRAMPS","SOMETHING_NEW"]}]}
        """.trimIndent()
        assertEquals(setOf(Symptom.CRAMPS), BackupCodec.decode(json).logs.single().symptoms)
    }

    @Test
    fun `foreign or broken files are rejected`() {
        listOf(
            """{"hello":"world"}""",
            "not json at all",
            """{"format":"mymooncalendar-backup","version":1,"periods":[{"start":"12.10.2026"}],"dayLogs":[]}""",
            """{"format":"mymooncalendar-backup","version":99,"periods":[],"dayLogs":[]}""",
        ).forEach { json ->
            val error = runCatching { BackupCodec.decode(json) }.exceptionOrNull()
            assertTrue("expected rejection for $json, got $error", error is InvalidBackupException)
        }
    }
}
