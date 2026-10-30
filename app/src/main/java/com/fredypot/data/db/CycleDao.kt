package com.fredypot.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CycleDao {
    @Query("SELECT * FROM periods ORDER BY start")
    fun observePeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods ORDER BY start")
    suspend fun getPeriods(): List<PeriodEntity>

    @Query("SELECT * FROM day_logs ORDER BY date")
    suspend fun getDayLogs(): List<DayLogEntity>

    @Query("SELECT * FROM periods WHERE start = :start")
    suspend fun periodByStart(start: LocalDate): PeriodEntity?

    @Upsert
    suspend fun upsertPeriod(period: PeriodEntity)

    /** Merges imported data: periods with the same start date and logs with the same date are replaced. */
    @Transaction
    suspend fun importAll(periods: List<PeriodEntity>, logs: List<DayLogEntity>) {
        periods.forEach { period ->
            upsertPeriod(period.copy(id = periodByStart(period.start)?.id ?: 0))
        }
        logs.forEach { upsertDayLog(it) }
    }

    @Query("DELETE FROM periods WHERE id = :id")
    suspend fun deletePeriodById(id: Long)

    @Query("SELECT * FROM day_logs ORDER BY date")
    fun observeDayLogs(): Flow<List<DayLogEntity>>

    @Upsert
    suspend fun upsertDayLog(log: DayLogEntity)

    @Query("DELETE FROM day_logs WHERE date = :date")
    suspend fun deleteDayLog(date: LocalDate)
}
