package com.fredypot.ui

import androidx.annotation.StringRes
import com.fredypot.R
import com.fredypot.domain.Confidence
import com.fredypot.domain.DayKind
import com.fredypot.domain.LhTest
import com.fredypot.domain.Mucus
import com.fredypot.domain.OvulationSignal
import com.fredypot.domain.Symptom
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RussianLocale: Locale = Locale.forLanguageTag("ru")

/** Russian when the system language is Russian, English otherwise — matches the string resources. */
val AppLocale: Locale
    get() = if (Locale.getDefault().language == RussianLocale.language) RussianLocale else Locale.ENGLISH

private val isRussian: Boolean
    get() = AppLocale == RussianLocale

// Read the locale on every call: the system language can change while the process is alive.
private fun formatter(ru: String, en: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (isRussian) ru else en, AppLocale)

fun LocalDate.dayMonth(): String = format(formatter("d MMMM", "MMMM d"))
fun LocalDate.shortDate(): String = format(formatter("d MMM yyyy", "MMM d, yyyy"))
fun LocalDate.dayTitle(): String = format(formatter("d MMMM, EEEE", "EEEE, MMMM d"))
fun YearMonth.monthTitle(): String = format(formatter("LLLL yyyy", "MMMM yyyy"))

@get:StringRes
val Symptom.label: Int
    get() = when (this) {
        Symptom.CRAMPS -> R.string.symptom_cramps
        Symptom.BREAST_TENDERNESS -> R.string.symptom_breast_tenderness
        Symptom.BLOATING -> R.string.symptom_bloating
        Symptom.HEADACHE -> R.string.symptom_headache
        Symptom.BACK_PAIN -> R.string.symptom_back_pain
        Symptom.ACNE -> R.string.symptom_acne
        Symptom.MOOD_SWINGS -> R.string.symptom_mood_swings
        Symptom.FATIGUE -> R.string.symptom_fatigue
        Symptom.NAUSEA -> R.string.symptom_nausea
        Symptom.SPOTTING -> R.string.symptom_spotting
        Symptom.HIGH_LIBIDO -> R.string.symptom_high_libido
        Symptom.OVULATION_PAIN -> R.string.symptom_ovulation_pain
    }

@get:StringRes
val Mucus.label: Int
    get() = when (this) {
        Mucus.DRY -> R.string.mucus_dry
        Mucus.STICKY -> R.string.mucus_sticky
        Mucus.CREAMY -> R.string.mucus_creamy
        Mucus.WATERY -> R.string.mucus_watery
        Mucus.EGG_WHITE -> R.string.mucus_egg_white
    }

@get:StringRes
val LhTest.label: Int
    get() = when (this) {
        LhTest.NEGATIVE -> R.string.lh_negative
        LhTest.POSITIVE -> R.string.lh_positive
    }

@get:StringRes
val OvulationSignal.label: Int
    get() = when (this) {
        OvulationSignal.TEMPERATURE -> R.string.signal_temperature
        OvulationSignal.LH_TEST -> R.string.signal_lh
        OvulationSignal.MUCUS -> R.string.signal_mucus
        OvulationSignal.OVULATION_PAIN -> R.string.signal_pain
    }

@get:StringRes
val Confidence.label: Int
    get() = when (this) {
        Confidence.LOW -> R.string.confidence_low
        Confidence.MEDIUM -> R.string.confidence_medium
        Confidence.HIGH -> R.string.confidence_high
    }

@get:StringRes
val DayKind.label: Int
    get() = when (this) {
        DayKind.PERIOD -> R.string.kind_period
        DayKind.PREDICTED_PERIOD -> R.string.kind_predicted_period
        DayKind.FERTILE -> R.string.kind_fertile
        DayKind.OVULATION -> R.string.kind_ovulation
    }
