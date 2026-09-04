package io.github.xalrk.nudge.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class Kind { SCHEDULED, RANDOM }

enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A reminder. SCHEDULED reminders have a wall-clock date/time (stored as a local
 * date-time plus the zone it was created in) and an optional repeat rule.
 * RANDOM reminders have no time; the app fires them at random moments inside the
 * user's active hours.
 *
 * [nextAt] is the pre-computed next firing instant (epoch millis) for both kinds so the
 * alarm scheduler only has to look at one column. [dedupeKey] is a normalised signature
 * enforced unique by the database so imports never create duplicates.
 */
@Entity(tableName = "reminders", indices = [Index(value = ["dedupeKey"], unique = true)])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String = "",
    val kind: Kind,
    /** ISO-8601 local date-time (e.g. 2026-09-10T14:30). Scheduled only. */
    val localDateTime: String? = null,
    /** Zone id the reminder was created in (e.g. America/Denver). Scheduled only. */
    val zoneId: String? = null,
    /** true = fire at the wall-clock time in whatever zone the device is in (like an alarm clock).
     *  false = pin to [zoneId] so the instant stays fixed when travelling. */
    val floating: Boolean = true,
    val repeat: Repeat = Repeat.NONE,
    val interval: Int = 1,
    /** Bitmask of weekdays for WEEKLY repeats: Mon = 1, Tue = 2, ... Sun = 64. 0 = weekday of start date. */
    val weekdays: Int = 0,
    /** ISO local date after which a repeating reminder stops. */
    val endDate: String? = null,
    val enabled: Boolean = true,
    val nextAt: Long? = null,
    val snoozeAt: Long? = null,
    val lastFiredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dedupeKey: String = "",
    /** ARGB notification color; null = complementary of the app accent. */
    val color: Int? = null,
    @ColumnInfo(defaultValue = "1") val sound: Boolean = true,
    @ColumnInfo(defaultValue = "1") val vibrate: Boolean = true,
    /** Comma-separated ISO dates of occurrences removed from a repeating series. */
    @ColumnInfo(defaultValue = "") val excludedDates: String = "",
    /** Random reminders only: personal average interval in millis, overriding the global slider. */
    val meanOverrideMillis: Long? = null,
) {
    /** Random reminders: the weekdays this one may fire on, or null to use the global active days. */
    fun randomDaysOrNull(): Set<DayOfWeek>? = if (isRandom && weekdays != 0) weekdaySet() else null

    fun excludedDateSet(): Set<LocalDate> =
        excludedDates.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.toSet()

    fun withExcluded(date: LocalDate): Reminder = copy(excludedDates = (excludedDateSet() + date).sorted().joinToString(","))

    val isScheduled get() = kind == Kind.SCHEDULED
    val isRandom get() = kind == Kind.RANDOM

    fun localDateTimeOrNull(): LocalDateTime? = localDateTime?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    fun endDateOrNull(): LocalDate? = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Effective zone: device zone when floating, the stored zone otherwise. */
    fun effectiveZone(): ZoneId {
        if (!floating && zoneId != null) runCatching { return ZoneId.of(zoneId) }
        return ZoneId.systemDefault()
    }

    fun weekdaySet(): Set<DayOfWeek> =
        DayOfWeek.entries.filter { weekdays and (1 shl (it.value - 1)) != 0 }.toSet()

    fun withDedupeKey(): Reminder = copy(dedupeKey = Dedupe.keyFor(this))

    companion object {
        /** Storage format for [localDateTime]: minutes precision, e.g. 2026-09-10T14:30. */
        val DT_FORMAT: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        fun maskOf(days: Collection<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
    }
}

object Dedupe {
    fun normalize(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

    fun keyFor(r: Reminder): String = buildString {
        append(normalize(r.title)); append('|')
        append(normalize(r.body)); append('|')
        append(r.kind.name); append('|')
        if (r.isScheduled) {
            append(r.localDateTime ?: ""); append('|')
            append(r.repeat.name); append('|')
            append(r.interval); append('|')
            append(r.weekdays); append('|')
            append(r.endDate ?: "")
        }
    }
}

class Converters {
    @TypeConverter fun kindToString(k: Kind): String = k.name
    @TypeConverter fun stringToKind(s: String): Kind = Kind.valueOf(s)
    @TypeConverter fun repeatToString(r: Repeat): String = r.name
    @TypeConverter fun stringToRepeat(s: String): Repeat = Repeat.valueOf(s)
}
