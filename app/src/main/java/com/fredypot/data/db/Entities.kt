package com.fredypot.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "periods", indices = [Index(value = ["start"], unique = true)])
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: LocalDate,
    val end: LocalDate?,
)

@Entity(tableName = "day_logs")
data class DayLogEntity(
    @PrimaryKey val date: LocalDate,
    /** Comma-separated [com.fredypot.domain.Symptom] names. */
    val symptoms: String,
    val mucus: String?,
    @ColumnInfo(name = "lh_test") val lhTest: String?,
    val temperature: Double?,
)
