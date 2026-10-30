package com.fredypot.data

import com.fredypot.data.db.CycleDao
import com.fredypot.data.db.DayLogEntity
import com.fredypot.data.db.PeriodEntity
import com.fredypot.domain.DayLog
import com.fredypot.domain.LhTest
import com.fredypot.domain.Mucus
import com.fredypot.domain.Period
import com.fredypot.domain.Symptom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class CycleRepository(private val dao: CycleDao) {

    val periods: Flow<List<Period>> = dao.observePeriods().map { list -> list.map { it.toDomain() } }

    val dayLogs: Flow<List<DayLog>> = dao.observeDayLogs().map { list -> list.map { it.toDomain() } }

    suspend fun savePeriod(period: Period) = dao.upsertPeriod(period.toEntity())

    suspend fun deletePeriod(id: Long) = dao.deletePeriodById(id)

    suspend fun saveDayLog(log: DayLog) {
        if (log.isEmpty) dao.deleteDayLog(log.date) else dao.upsertDayLog(log.toEntity())
    }

    suspend fun exportBackup(): Backup = Backup(
        periods = dao.getPeriods().map { it.toDomain() },
        logs = dao.getDayLogs().map { it.toDomain() },
    )

    suspend fun importBackup(backup: Backup) = dao.importAll(
        periods = backup.periods.map { it.toEntity() },
        logs = backup.logs.filterNot { it.isEmpty }.map { it.toEntity() },
    )
}

private fun PeriodEntity.toDomain() = Period(id = id, start = start, end = end)

private fun Period.toEntity() = PeriodEntity(id = id, start = start, end = end)

private fun DayLogEntity.toDomain() = DayLog(
    date = date,
    symptoms = symptoms.split(',')
        .mapNotNull { name -> Symptom.entries.firstOrNull { it.name == name } }
        .toSet(),
    mucus = Mucus.entries.firstOrNull { it.name == mucus },
    lhTest = LhTest.entries.firstOrNull { it.name == lhTest },
    temperature = temperature,
)

private fun DayLog.toEntity() = DayLogEntity(
    date = date,
    symptoms = symptoms.joinToString(",") { it.name },
    mucus = mucus?.name,
    lhTest = lhTest?.name,
    temperature = temperature,
)
