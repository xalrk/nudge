package io.github.xalrk.nudge.domain

import io.github.xalrk.nudge.data.Kind
import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.data.Repeat
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParseResult(val reminders: List<Reminder>, val errors: List<String>)

/**
 * Two import formats, auto-detected:
 *
 * 1. Plain text, one reminder per line (easy to write by hand):
 *      Drink some water
 *      Call mom @ 2026-09-10 14:30
 *      Stretch @ 09:00 every day
 *      Team sync @ 2026-09-08 10:00 every mon,wed,fri until 2026-12-19
 *      Pay rent @ 2026-10-01 09:00 every month :: transfer before noon
 *    Lines with no "@" become random reminders. "#" starts a comment.
 *
 * 2. JSON, an array (or {"reminders": [...]}) of objects:
 *      {"title": "Call mom", "body": "", "at": "2026-09-10T14:30", "repeat": "weekly",
 *       "interval": 1, "weekdays": ["mon","wed"], "until": "2026-12-31",
 *       "zone": "America/Denver", "floating": true}
 *    Omit "at" for a random reminder. This is also the export format.
 */
object ImportExport {
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFmt = Reminder.DT_FORMAT

    fun parse(content: String, now: LocalDateTime = LocalDateTime.now()): ParseResult {
        val trimmed = content.trim()
        return if (trimmed.startsWith("[") || trimmed.startsWith("{")) parseJson(trimmed) else parseText(trimmed, now)
    }

    // ---------------------------------------------------------------- text

    private val dateRe = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")
    private val timeRe = Regex("""\b(\d{1,2}):(\d{2})\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
    private val everyRe = Regex("""\bevery\s+(?:(\d+)\s+)?([a-z,]+)""", RegexOption.IGNORE_CASE)
    private val untilRe = Regex("""\buntil\s+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
    private val clauseStartRe = Regex("""\s(@|every\s|until\s)""", RegexOption.IGNORE_CASE)

    fun parseText(content: String, now: LocalDateTime = LocalDateTime.now()): ParseResult {
        val out = ArrayList<Reminder>()
        val errors = ArrayList<String>()
        content.lines().forEachIndexed { idx, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            runCatching { parseLine(line, now) }
                .onSuccess { out += it }
                .onFailure { errors += "Line ${idx + 1}: ${it.message ?: "could not parse"}" }
        }
        return ParseResult(out, errors)
    }

    fun parseLine(line: String, now: LocalDateTime = LocalDateTime.now()): Reminder {
        var main = line
        var body = ""
        val sep = line.indexOf("::")
        if (sep >= 0) { main = line.substring(0, sep).trim(); body = line.substring(sep + 2).trim() }

        val clauseAt = clauseStartRe.find(main)?.range?.first
        val title = (if (clauseAt != null) main.substring(0, clauseAt) else main).trim()
        val clauses = if (clauseAt != null) main.substring(clauseAt).trim() else ""
        require(title.isNotEmpty()) { "missing reminder text" }

        if (clauses.isEmpty() || !clauses.contains("@")) {
            require(clauses.isEmpty()) { "repeat rules need a time, e.g. \"@ 09:00 every day\"" }
            return Reminder(title = title, body = body, kind = Kind.RANDOM).withDedupeKey()
        }

        val date = dateRe.find(clauses)?.groupValues?.get(1)?.let { LocalDate.parse(it) }
        val untilMatch = untilRe.find(clauses)
        val until = untilMatch?.groupValues?.get(1)?.let { LocalDate.parse(it) }
        // The "until" date must not be mistaken for the start date.
        val startDate = if (date != null && untilMatch != null && untilMatch.value.contains(date.toString()))
            dateRe.findAll(clauses).map { LocalDate.parse(it.groupValues[1]) }.firstOrNull { it != until } else date

        val time = timeRe.find(clauses.replace(untilRe, ""))?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            val ampm = m.groupValues[3].lowercase()
            if (ampm == "pm" && h < 12) h += 12
            if (ampm == "am" && h == 12) h = 0
            LocalTime.of(h, min)
        } ?: LocalTime.of(9, 0)

        var repeat = Repeat.NONE
        var interval = 1
        var weekdays = 0
        everyRe.find(clauses)?.let { m ->
            interval = m.groupValues[1].toIntOrNull() ?: 1
            val unit = m.groupValues[2].lowercase()
            when {
                unit.startsWith("day") -> repeat = Repeat.DAILY
                unit.startsWith("week") && !unit.startsWith("weekday") && !unit.startsWith("weekend") -> repeat = Repeat.WEEKLY
                unit.startsWith("month") -> repeat = Repeat.MONTHLY
                unit.startsWith("year") -> repeat = Repeat.YEARLY
                unit.startsWith("weekday") -> { repeat = Repeat.WEEKLY; weekdays = Reminder.maskOf(DayOfWeek.entries.filter { it.value <= 5 }) }
                unit.startsWith("weekend") -> { repeat = Repeat.WEEKLY; weekdays = Reminder.maskOf(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) }
                else -> {
                    val days = unit.split(',').mapNotNull { parseDay(it) }
                    require(days.isNotEmpty()) { "unknown repeat unit \"$unit\"" }
                    repeat = Repeat.WEEKLY; weekdays = Reminder.maskOf(days)
                }
            }
        }

        var start = (startDate ?: now.toLocalDate()).atTime(time)
        if (startDate == null) {
            // Only a time was given: use today, or the next matching day if that moment already passed.
            if (!start.isAfter(now)) start = start.plusDays(1)
            if (repeat == Repeat.WEEKLY && weekdays != 0) {
                val set = DayOfWeek.entries.filter { weekdays and (1 shl (it.value - 1)) != 0 }.toSet()
                var guard = 0
                while (start.dayOfWeek !in set && guard++ < 7) start = start.plusDays(1)
            }
        }

        return Reminder(
            title = title, body = body, kind = Kind.SCHEDULED,
            localDateTime = start.format(dateTimeFmt), zoneId = ZoneId.systemDefault().id, floating = true,
            repeat = repeat, interval = interval.coerceAtLeast(1), weekdays = weekdays,
            endDate = until?.format(dateFmt),
        ).withDedupeKey()
    }

    private fun parseDay(s: String): DayOfWeek? {
        val k = s.trim().lowercase(Locale.ROOT)
        if (k.length < 2) return null
        return DayOfWeek.entries.firstOrNull { it.name.lowercase(Locale.ROOT).startsWith(k) || k.startsWith(it.name.lowercase(Locale.ROOT).take(3)) }
    }

    // ---------------------------------------------------------------- json

    fun parseJson(content: String): ParseResult {
        val out = ArrayList<Reminder>()
        val errors = ArrayList<String>()
        val arr: JSONArray = try {
            if (content.startsWith("[")) JSONArray(content) else JSONObject(content).optJSONArray("reminders") ?: JSONArray()
        } catch (e: Exception) {
            return ParseResult(emptyList(), listOf("Invalid JSON: ${e.message}"))
        }
        for (i in 0 until arr.length()) {
            runCatching { fromJson(arr.getJSONObject(i)) }
                .onSuccess { out += it }
                .onFailure { errors += "Item ${i + 1}: ${it.message ?: "could not parse"}" }
        }
        return ParseResult(out, errors)
    }

    private fun fromJson(o: JSONObject): Reminder {
        val title = o.optString("title").trim().ifEmpty { o.optString("message").trim() }
        require(title.isNotEmpty()) { "missing \"title\"" }
        val body = o.optString("body", "")
        val at = o.optString("at", "").trim()
        val enabled = o.optBoolean("enabled", true)
        if (at.isEmpty()) return Reminder(title = title, body = body, kind = Kind.RANDOM, enabled = enabled).withDedupeKey()

        val ldt = runCatching { LocalDateTime.parse(at) }.getOrElse {
            runCatching { LocalDate.parse(at).atTime(9, 0) }.getOrElse { throw IllegalArgumentException("bad \"at\" value \"$at\" (use 2026-09-10T14:30)") }
        }
        val repeat = when (o.optString("repeat", "none").lowercase()) {
            "", "none", "once" -> Repeat.NONE
            "daily", "day" -> Repeat.DAILY
            "weekly", "week" -> Repeat.WEEKLY
            "monthly", "month" -> Repeat.MONTHLY
            "yearly", "year", "annually" -> Repeat.YEARLY
            else -> throw IllegalArgumentException("unknown repeat \"${o.optString("repeat")}\"")
        }
        val weekdays = o.optJSONArray("weekdays")?.let { a ->
            Reminder.maskOf((0 until a.length()).mapNotNull { parseDay(a.getString(it)) })
        } ?: 0
        val zone = o.optString("zone", "").ifEmpty { ZoneId.systemDefault().id }
        runCatching { ZoneId.of(zone) }.getOrElse { throw IllegalArgumentException("unknown zone \"$zone\"") }
        val until = o.optString("until", "").ifEmpty { null }?.let { LocalDate.parse(it).format(dateFmt) }
        return Reminder(
            title = title, body = body, kind = Kind.SCHEDULED,
            localDateTime = ldt.format(dateTimeFmt), zoneId = zone, floating = o.optBoolean("floating", true),
            repeat = repeat, interval = o.optInt("interval", 1).coerceAtLeast(1), weekdays = weekdays,
            endDate = until, enabled = enabled,
        ).withDedupeKey()
    }

    fun toJson(reminders: List<Reminder>): String {
        val arr = JSONArray()
        for (r in reminders) {
            val o = JSONObject()
            o.put("title", r.title)
            if (r.body.isNotEmpty()) o.put("body", r.body)
            if (r.isScheduled) {
                o.put("at", r.localDateTime)
                o.put("zone", r.zoneId ?: ZoneId.systemDefault().id)
                o.put("floating", r.floating)
                o.put("repeat", r.repeat.name.lowercase())
                if (r.repeat != Repeat.NONE) o.put("interval", r.interval)
                if (r.repeat == Repeat.WEEKLY && r.weekdays != 0) {
                    o.put("weekdays", JSONArray(r.weekdaySet().map { it.name.take(3).lowercase() }))
                }
                r.endDate?.let { o.put("until", it) }
            }
            o.put("enabled", r.enabled)
            arr.put(o)
        }
        return JSONObject().put("reminders", arr).toString(2)
    }
}
