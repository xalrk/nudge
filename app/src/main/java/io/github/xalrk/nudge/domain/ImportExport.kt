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
 * Import/export format: CSV with one reminder per row.
 *
 *   title,details,date,time,repeat,every,weekdays,until,zone,follow_device_zone
 *   Drink some water,,,,,,,,,
 *   Call mom,she is home on Sundays,2026-09-14,18:00,weekly,1,sun,,,
 *   Standup,,2026-09-07,09:30,weekly,1,mon;tue;wed;thu;fri,2026-12-19,,
 *   Flight,,2026-10-20,06:30,,,,,America/Denver,no
 *
 * - The header row is optional; without it the columns are read in the order above.
 *   With it, columns may appear in any order and unknown columns are ignored.
 * - Only `title` is required. A row with no date and no time is a random reminder.
 * - Weekdays are separated by ";" or spaces (commas work too if the cell is quoted).
 *
 * JSON in the same shape ({"reminders": [{"title": ..., "at": "2026-09-10T14:30", ...}]})
 * is still accepted for people who prefer it.
 */
object ImportExport {
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFmt = Reminder.DT_FORMAT

    val CSV_COLUMNS = listOf("title", "details", "date", "time", "repeat", "every", "weekdays", "until", "zone", "follow_device_zone")

    private val columnAliases = mapOf(
        "title" to "title", "message" to "title", "text" to "title", "name" to "title", "reminder" to "title",
        "details" to "details", "body" to "details", "description" to "details", "notes" to "details", "note" to "details",
        "date" to "date", "day" to "date", "start" to "date", "start_date" to "date",
        "time" to "time", "at" to "time", "start_time" to "time",
        "repeat" to "repeat", "recurrence" to "repeat", "frequency" to "repeat",
        "every" to "every", "interval" to "every",
        "weekdays" to "weekdays", "days" to "weekdays", "on" to "weekdays",
        "until" to "until", "end" to "until", "end_date" to "until", "ends" to "until",
        "zone" to "zone", "timezone" to "zone", "time_zone" to "zone", "tz" to "zone",
        "follow_device_zone" to "follow_device_zone", "floating" to "follow_device_zone", "follow_device_timezone" to "follow_device_zone",
    )

    fun parse(content: String, now: LocalDateTime = LocalDateTime.now()): ParseResult {
        val trimmed = content.trim().removePrefix("﻿")
        return if (trimmed.startsWith("[") || trimmed.startsWith("{")) parseJson(trimmed) else parseCsv(trimmed, now)
    }

    // ----------------------------------------------------------------- csv

    fun parseCsv(content: String, now: LocalDateTime = LocalDateTime.now()): ParseResult {
        val rows = readCsv(content.removePrefix("﻿"))
        val out = ArrayList<Reminder>()
        val errors = ArrayList<String>()
        if (rows.isEmpty()) return ParseResult(out, errors)

        val first = rows.first()
        val hasHeader = first.isNotEmpty() && columnAliases[normHeader(first[0])] == "title" && first.size > 1 ||
            first.isNotEmpty() && first.all { columnAliases.containsKey(normHeader(it)) || it.isBlank() } && first.any { it.isNotBlank() } && first.size > 1
        val columns: List<String?> = if (hasHeader) first.map { columnAliases[normHeader(it)] } else CSV_COLUMNS

        rows.drop(if (hasHeader) 1 else 0).forEachIndexed { i, cells ->
            val lineNo = i + 1 + (if (hasHeader) 1 else 0)
            if (cells.all { it.isBlank() }) return@forEachIndexed
            if (cells.firstOrNull()?.trimStart()?.startsWith("#") == true) return@forEachIndexed
            val field = HashMap<String, String>()
            columns.forEachIndexed { c, name -> if (name != null && c < cells.size) field[name] = cells[c].trim() }
            runCatching { fromFields(field, now) }
                .onSuccess { out += it }
                .onFailure { errors += "Row $lineNo: ${it.message ?: "could not read"}" }
        }
        return ParseResult(out, errors)
    }

    private fun normHeader(s: String) = s.trim().lowercase(Locale.ROOT).replace(Regex("[\\s-]+"), "_").removePrefix("﻿")

    /** Minimal RFC 4180 reader: quoted cells, doubled quotes, newlines inside quotes. Auto-detects ";" delimiters. */
    fun readCsv(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull() ?: ""
        val delim = if (!firstLine.contains(',') && firstLine.contains(';')) ';' else ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                    } else cell.append(ch)
                }
                ch == '"' -> inQuotes = true
                ch == delim -> { row += cell.toString(); cell.setLength(0) }
                ch == '\r' -> {}
                ch == '\n' -> { row += cell.toString(); cell.setLength(0); rows += row; row = ArrayList() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    private fun fromFields(f: Map<String, String>, now: LocalDateTime): Reminder {
        val title = f["title"].orEmpty().trim()
        require(title.isNotEmpty()) { "missing title" }
        val body = f["details"].orEmpty().trim()
        val dateStr = f["date"].orEmpty()
        val timeStr = f["time"].orEmpty()
        if (dateStr.isBlank() && timeStr.isBlank()) {
            require(f["repeat"].isNullOrBlank()) { "a repeating reminder needs a date or time" }
            return Reminder(title = title, body = body, kind = Kind.RANDOM).withDedupeKey()
        }

        val date = dateStr.takeIf { it.isNotBlank() }?.let { parseDate(it) }
        val time = timeStr.takeIf { it.isNotBlank() }?.let { parseTime(it) } ?: LocalTime.of(9, 0)
        val repeat = parseRepeat(f["repeat"].orEmpty())
        var weekdays = repeat.second
        val rep = repeat.first
        f["weekdays"]?.takeIf { it.isNotBlank() }?.let { s ->
            val days = s.split(Regex("[;,/|\\s]+")).filter { it.isNotBlank() }.map { parseDay(it) ?: throw IllegalArgumentException("unknown weekday \"$it\"") }
            weekdays = Reminder.maskOf(days)
        }
        val every = f["every"]?.takeIf { it.isNotBlank() }?.let { it.toIntOrNull() ?: throw IllegalArgumentException("\"every\" must be a number") } ?: 1
        val until = f["until"]?.takeIf { it.isNotBlank() }?.let { parseDate(it) }
        val zone = f["zone"]?.takeIf { it.isNotBlank() }?.let { z ->
            runCatching { ZoneId.of(z.trim()) }.getOrElse { throw IllegalArgumentException("unknown zone \"$z\"") }
        } ?: ZoneId.systemDefault()
        val floating = f["follow_device_zone"]?.takeIf { it.isNotBlank() }?.let { parseBool(it) } ?: true

        val effectiveRepeat = if (rep == Repeat.NONE && weekdays != 0) Repeat.WEEKLY else rep
        var start = (date ?: now.toLocalDate()).atTime(time)
        if (date == null) {
            if (!start.isAfter(now)) start = start.plusDays(1)
            if (effectiveRepeat == Repeat.WEEKLY && weekdays != 0) {
                val set = DayOfWeek.entries.filter { weekdays and (1 shl (it.value - 1)) != 0 }.toSet()
                var guard = 0
                while (start.dayOfWeek !in set && guard++ < 7) start = start.plusDays(1)
            }
        }
        return Reminder(
            title = title, body = body, kind = Kind.SCHEDULED,
            localDateTime = start.format(dateTimeFmt), zoneId = zone.id, floating = floating,
            repeat = effectiveRepeat, interval = every.coerceAtLeast(1),
            weekdays = if (effectiveRepeat == Repeat.WEEKLY) weekdays else 0,
            endDate = until?.format(dateFmt),
        ).withDedupeKey()
    }

    private fun parseDate(s: String): LocalDate {
        val t = s.trim()
        runCatching { return LocalDate.parse(t) }
        for (p in listOf("yyyy/MM/dd", "yyyy.MM.dd", "d/M/yyyy", "M/d/yyyy", "d.M.yyyy", "MMM d yyyy", "d MMM yyyy")) {
            runCatching { return LocalDate.parse(t, DateTimeFormatter.ofPattern(p, Locale.ENGLISH)) }
        }
        throw IllegalArgumentException("bad date \"$s\" (use YYYY-MM-DD)")
    }

    private val timeRe = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(am|pm|a|p)?$""", RegexOption.IGNORE_CASE)
    private fun parseTime(s: String): LocalTime {
        val m = timeRe.find(s.trim()) ?: throw IllegalArgumentException("bad time \"$s\" (use HH:MM)")
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[3].lowercase().take(1)
        if (ap == "p" && h < 12) h += 12
        if (ap == "a" && h == 12) h = 0
        require(h in 0..23 && min in 0..59) { "bad time \"$s\"" }
        return LocalTime.of(h, min)
    }

    /** Returns repeat + a weekday mask for shorthand values such as "weekdays". */
    private fun parseRepeat(s: String): Pair<Repeat, Int> = when (s.trim().lowercase(Locale.ROOT)) {
        "", "none", "no", "once", "never", "0" -> Repeat.NONE to 0
        "daily", "day", "days", "every day" -> Repeat.DAILY to 0
        "weekly", "week", "weeks", "every week" -> Repeat.WEEKLY to 0
        "weekday", "weekdays" -> Repeat.WEEKLY to Reminder.maskOf(DayOfWeek.entries.filter { it.value <= 5 })
        "weekend", "weekends" -> Repeat.WEEKLY to Reminder.maskOf(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        "monthly", "month", "months", "every month" -> Repeat.MONTHLY to 0
        "yearly", "year", "years", "annually", "annual", "every year" -> Repeat.YEARLY to 0
        else -> throw IllegalArgumentException("unknown repeat \"$s\" (use daily, weekly, monthly, yearly)")
    }

    private fun parseBool(s: String): Boolean = when (s.trim().lowercase(Locale.ROOT)) {
        "yes", "y", "true", "1", "on" -> true
        "no", "n", "false", "0", "off" -> false
        else -> throw IllegalArgumentException("\"follow_device_zone\" must be yes or no")
    }

    private fun parseDay(s: String): DayOfWeek? {
        val k = s.trim().lowercase(Locale.ROOT)
        if (k.length < 2) return null
        return DayOfWeek.entries.firstOrNull { it.name.lowercase(Locale.ROOT).startsWith(k) || k.startsWith(it.name.lowercase(Locale.ROOT).take(3)) }
    }

    fun toCsv(reminders: List<Reminder>): String = buildString {
        append(CSV_COLUMNS.joinToString(",")).append("\r\n")
        for (r in reminders) {
            val ldt = r.localDateTimeOrNull()
            val cells = listOf(
                r.title,
                r.body,
                ldt?.toLocalDate()?.toString() ?: "",
                ldt?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                if (r.isScheduled && r.repeat != Repeat.NONE) r.repeat.name.lowercase() else "",
                if (r.isScheduled && r.repeat != Repeat.NONE) r.interval.toString() else "",
                if (r.isScheduled && r.repeat == Repeat.WEEKLY && r.weekdays != 0) r.weekdaySet().joinToString(";") { it.name.take(3).lowercase() } else "",
                r.endDate ?: "",
                if (r.isScheduled) (r.zoneId ?: "") else "",
                if (r.isScheduled) (if (r.floating) "yes" else "no") else "",
            )
            append(cells.joinToString(",") { csvCell(it) }).append("\r\n")
        }
    }

    private fun csvCell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' } || s.startsWith(" ") || s.endsWith(" "))
            "\"" + s.replace("\"", "\"\"") + "\"" else s

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
        val body = o.optString("body", o.optString("details", ""))
        val at = o.optString("at", "").trim()
        val enabled = o.optBoolean("enabled", true)
        if (at.isEmpty()) return Reminder(title = title, body = body, kind = Kind.RANDOM, enabled = enabled).withDedupeKey()

        val ldt = runCatching { LocalDateTime.parse(at) }.getOrElse {
            runCatching { LocalDate.parse(at).atTime(9, 0) }.getOrElse { throw IllegalArgumentException("bad \"at\" value \"$at\" (use 2026-09-10T14:30)") }
        }
        val (repeat, shorthandDays) = parseRepeat(o.optString("repeat", "none"))
        val weekdays = o.optJSONArray("weekdays")?.let { a ->
            Reminder.maskOf((0 until a.length()).mapNotNull { parseDay(a.getString(it)) })
        } ?: shorthandDays
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
}
