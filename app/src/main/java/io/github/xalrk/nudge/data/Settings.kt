package io.github.xalrk.nudge.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.exp
import kotlin.math.ln

/** How the random-frequency setting is interpreted. */
enum class FrequencyMode {
    /** Each random reminder independently averages one firing per [Settings.meanIntervalMillis]. */
    PER_REMINDER,
    /** The whole pool together averages one firing per interval (one reminder picked at random). */
    WHOLE_POOL
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsSnapshot(
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    /** ARGB accent used for the whole theme when dynamic color is off. */
    val accentColor: Int,
    val meanIntervalMillis: Long,
    val frequencyMode: FrequencyMode,
    val activeStartHour: Int,
    val activeEndHour: Int,
    val showNextRandomTime: Boolean,
    val autoUpdateCheck: Boolean,
) {
    val activeHoursPerDay: Int get() = activeEndHour - activeStartHour
}

class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("nudge_settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = prefs.edit().putString(KEY_THEME, v.name).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, false)
        set(v) = prefs.edit().putBoolean(KEY_DYNAMIC, v).apply()

    var accentColor: Int
        get() = prefs.getInt(KEY_ACCENT, DEFAULT_ACCENT)
        set(v) = prefs.edit().putInt(KEY_ACCENT, v or 0xFF000000.toInt()).apply()

    var meanIntervalMillis: Long
        get() = prefs.getLong(KEY_MEAN, DEFAULT_MEAN_MILLIS)
        set(v) = prefs.edit().putLong(KEY_MEAN, v.coerceIn(MIN_MEAN_MILLIS, MAX_MEAN_MILLIS)).apply()

    var frequencyMode: FrequencyMode
        get() = runCatching { FrequencyMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }.getOrDefault(FrequencyMode.PER_REMINDER)
        set(v) = prefs.edit().putString(KEY_MODE, v.name).apply()

    var activeStartHour: Int
        get() = prefs.getInt(KEY_START, 7)
        set(v) = prefs.edit().putInt(KEY_START, v.coerceIn(0, 23)).apply()

    var activeEndHour: Int
        get() = prefs.getInt(KEY_END, 23)
        set(v) = prefs.edit().putInt(KEY_END, v.coerceIn(1, 24)).apply()

    var showNextRandomTime: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NEXT, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_NEXT, v).apply()

    var autoUpdateCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()

    /** Version we already raised an update notification for, so it fires once per release. */
    var lastNotifiedUpdate: String?
        get() = prefs.getString(KEY_LAST_UPDATE, null)
        set(v) = prefs.edit().putString(KEY_LAST_UPDATE, v).apply()

    fun snapshot() = SettingsSnapshot(themeMode, dynamicColor, accentColor, meanIntervalMillis, frequencyMode, activeStartHour, activeEndHour, showNextRandomTime, autoUpdateCheck)

    fun observe(): Flow<SettingsSnapshot> = callbackFlow {
        trySend(snapshot())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_MEAN = "mean_interval_millis"
        private const val KEY_MODE = "frequency_mode"
        private const val KEY_START = "active_start_hour"
        private const val KEY_END = "active_end_hour"
        private const val KEY_SHOW_NEXT = "show_next_random"
        private const val KEY_AUTO_UPDATE = "auto_update_check"
        private const val KEY_LAST_UPDATE = "last_notified_update"

        const val DEFAULT_ACCENT = 0xFF3D5AFE.toInt()

        /** Hand-picked accents that read well on both white and true black. */
        val ACCENT_PRESETS: List<Pair<String, Int>> = listOf(
            "Indigo" to 0xFF3D5AFE.toInt(),
            "Blue" to 0xFF1E88E5.toInt(),
            "Teal" to 0xFF00897B.toInt(),
            "Green" to 0xFF43A047.toInt(),
            "Amber" to 0xFFF9A825.toInt(),
            "Orange" to 0xFFF4511E.toInt(),
            "Red" to 0xFFE53935.toInt(),
            "Pink" to 0xFFD81B60.toInt(),
            "Purple" to 0xFF8E24AA.toInt(),
            "Violet" to 0xFF5E35B1.toInt(),
            "Slate" to 0xFF546E7A.toInt(),
            "Mono" to 0xFF212121.toInt(),
        )

        /** Parses "#RGB", "#RRGGBB", "RRGGBB"; returns null when invalid. */
        fun parseHex(input: String): Int? {
            val h = input.trim().removePrefix("#")
            if (!h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            val full = when (h.length) { 3 -> h.map { "$it$it" }.joinToString(""); 6 -> h; else -> return null }
            return (0xFF000000L or full.toLong(16)).toInt()
        }

        fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

        const val HOUR_MILLIS = 60L * 60L * 1000L
        const val DAY_MILLIS = 24L * HOUR_MILLIS
        const val DEFAULT_MEAN_MILLIS = 14L * DAY_MILLIS
        const val MIN_MEAN_MILLIS = HOUR_MILLIS
        const val MAX_MEAN_MILLIS = 180L * DAY_MILLIS

        /** Map the 0..1 slider position onto the mean interval (log scale, 1 hour .. 180 days). */
        fun sliderToMillis(t: Float): Long {
            val lo = ln(MIN_MEAN_MILLIS.toDouble())
            val hi = ln(MAX_MEAN_MILLIS.toDouble())
            return exp(lo + (hi - lo) * t.toDouble().coerceIn(0.0, 1.0)).toLong()
        }

        fun millisToSlider(ms: Long): Float {
            val lo = ln(MIN_MEAN_MILLIS.toDouble())
            val hi = ln(MAX_MEAN_MILLIS.toDouble())
            return ((ln(ms.toDouble().coerceIn(MIN_MEAN_MILLIS.toDouble(), MAX_MEAN_MILLIS.toDouble())) - lo) / (hi - lo)).toFloat()
        }

        /** Human wording for an average interval, e.g. "about once every 2 weeks". */
        fun describeInterval(ms: Long): String {
            val hours = ms / HOUR_MILLIS.toDouble()
            val days = ms / DAY_MILLIS.toDouble()
            return when {
                hours < 1.5 -> "about once an hour"
                hours < 36 -> "about once every ${hours.roundNice()} hours"
                days < 1.5 -> "about once a day"
                days < 13 -> "about once every ${days.roundNice()} days"
                days < 60 -> "about once every ${(days / 7).roundNice()} weeks"
                else -> "about once every ${(days / 30.4).roundNice()} months"
            }
        }

        private fun Double.roundNice(): String {
            val r = if (this < 10) Math.round(this * 2) / 2.0 else Math.round(this).toDouble()
            return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
        }
    }
}
