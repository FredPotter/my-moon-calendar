package com.fredypot.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}

@Database(entities = [PeriodEntity::class, DayLogEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class CycleDatabase : RoomDatabase() {
    abstract fun cycleDao(): CycleDao

    companion object {
        fun create(context: Context): CycleDatabase =
            Room.databaseBuilder(context, CycleDatabase::class.java, "cycle.db").build()
    }
}
