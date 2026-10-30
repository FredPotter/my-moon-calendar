package com.fredypot.data

import com.fredypot.domain.DayLog
import com.fredypot.domain.LhTest
import com.fredypot.domain.Mucus
import com.fredypot.domain.Period
import com.fredypot.domain.Symptom
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class Backup(val periods: List<Period>, val logs: List<DayLog>)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** JSON file format used to move data between phones. Dates are ISO-8601, enums are stored by name. */
object BackupCodec {

    private const val FORMAT = "mymooncalendar-backup"
    private const val VERSION = 1

    fun encode(backup: Backup, exportedAt: LocalDate): String {
        val periods = JSONArray()
        backup.periods.forEach { p ->
            periods.put(JSONObject().put("start", p.start.toString()).putOpt("end", p.end?.toString()))
        }
        val logs = JSONArray()
        backup.logs.forEach { log ->
            logs.put(
                JSONObject()
                    .put("date", log.date.toString())
                    .put("symptoms", JSONArray(log.symptoms.map { it.name }))
                    .putOpt("mucus", log.mucus?.name)
                    .putOpt("lhTest", log.lhTest?.name)
                    .putOpt("temperature", log.temperature),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt.toString())
            .put("periods", periods)
            .put("dayLogs", logs)
            .toString(2)
    }

    /** Unknown symptom names (from a newer app version) are skipped instead of failing the whole import. */
    fun decode(json: String): Backup = try {
        val root = JSONObject(json)
        if (root.optString("format") != FORMAT) throw InvalidBackupException("Not a backup file")
        if (root.getInt("version") > VERSION) throw InvalidBackupException("Backup from a newer app version")

        val periodsJson = root.getJSONArray("periods")
        val periods = List(periodsJson.length()) { i ->
            val p = periodsJson.getJSONObject(i)
            Period(start = LocalDate.parse(p.getString("start")), end = p.optStringOrNull("end")?.let(LocalDate::parse))
        }
        val logsJson = root.getJSONArray("dayLogs")
        val logs = List(logsJson.length()) { i ->
            val l = logsJson.getJSONObject(i)
            val symptomsJson = l.optJSONArray("symptoms") ?: JSONArray()
            DayLog(
                date = LocalDate.parse(l.getString("date")),
                symptoms = List(symptomsJson.length()) { symptomsJson.getString(it) }
                    .mapNotNull { name -> Symptom.entries.firstOrNull { it.name == name } }
                    .toSet(),
                mucus = l.optStringOrNull("mucus")?.let { name -> Mucus.entries.firstOrNull { it.name == name } },
                lhTest = l.optStringOrNull("lhTest")?.let { name -> LhTest.entries.firstOrNull { it.name == name } },
                temperature = if (l.has("temperature") && !l.isNull("temperature")) l.getDouble("temperature") else null,
            )
        }
        Backup(periods, logs)
    } catch (e: JSONException) {
        throw InvalidBackupException("Malformed backup", e)
    } catch (e: DateTimeParseException) {
        throw InvalidBackupException("Malformed date in backup", e)
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null
}
