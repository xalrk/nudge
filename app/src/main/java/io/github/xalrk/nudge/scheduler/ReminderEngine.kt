package io.github.xalrk.nudge.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.xalrk.nudge.NudgeApp
import io.github.xalrk.nudge.data.FiredEvent
import io.github.xalrk.nudge.data.FiredEventDao
import io.github.xalrk.nudge.data.Kind
import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.data.ReminderDao
import io.github.xalrk.nudge.data.Repeat
import io.github.xalrk.nudge.data.SettingsSnapshot
import io.github.xalrk.nudge.domain.RandomScheduler
import io.github.xalrk.nudge.domain.Recurrence
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Single place that decides *when* reminders fire. Everything funnels through here:
 * saving a reminder, an alarm going off, a reboot, a time-zone change, a settings change.
 *
 * Strategy: keep `nextAt` up to date on every row and hold exactly one AlarmManager
 * alarm for the earliest one. When it fires, deliver everything that is due, advance
 * those rows, and re-arm.
 */
object ReminderEngine {
    private const val TAG = "ReminderEngine"
    private const val ALARM_REQUEST = 1001
    private val lock = Mutex()

    private fun dao(context: Context): ReminderDao = (context.applicationContext as NudgeApp).database.reminders()
    private fun events(context: Context): FiredEventDao = (context.applicationContext as NudgeApp).database.firedEvents()
    private const val HISTORY_MILLIS = 400L * 24 * 3_600_000L

    /** Post the notification and record it in the history the calendar shows. */
    private suspend fun deliver(context: Context, r: Reminder, at: Long) {
        Notifier.show(context, r)
        events(context).insert(FiredEvent(reminderId = r.id, title = r.title, body = r.body, kind = r.kind, firedAt = at))
    }
    private fun settings(context: Context): SettingsSnapshot = (context.applicationContext as NudgeApp).settings.snapshot()

    /** Compute nextAt for a new/edited reminder and persist it. Returns the saved row id. */
    suspend fun save(context: Context, reminder: Reminder): Long = lock.withLock {
        val dao = dao(context)
        val s = settings(context)
        val pool = dao.countEnabledRandom() + (if (reminder.isRandom && reminder.enabled && reminder.id == 0L) 1 else 0)
        val prepared = prepare(reminder.withDedupeKey(), s, pool, Instant.now())
        val id = if (prepared.id == 0L) dao.insert(prepared) else { dao.update(prepared); prepared.id }
        if (reminder.isRandom && s.frequencyMode == io.github.xalrk.nudge.data.FrequencyMode.WHOLE_POOL) resampleAllRandom(context)
        armAlarm(context)
        id
    }

    /** Insert many (import). Returns (inserted, skippedDuplicates). */
    suspend fun importAll(context: Context, reminders: List<Reminder>): Pair<Int, Int> = lock.withLock {
        val dao = dao(context)
        val s = settings(context)
        var pool = dao.countEnabledRandom()
        var inserted = 0
        var skipped = 0
        val seen = HashSet<String>()
        val now = Instant.now()
        for (r in reminders) {
            val keyed = r.withDedupeKey()
            if (!seen.add(keyed.dedupeKey)) { skipped++; continue }
            if (keyed.isRandom && keyed.enabled) pool++
            val id = dao.insertIgnore(prepare(keyed, s, pool, now))
            if (id == -1L) { skipped++; if (keyed.isRandom && keyed.enabled) pool-- } else inserted++
        }
        if (s.frequencyMode == io.github.xalrk.nudge.data.FrequencyMode.WHOLE_POOL) resampleAllRandom(context)
        armAlarm(context)
        inserted to skipped
    }

    enum class SeriesScope { THIS, FOLLOWING, ALL }

    /**
     * Apply an edit to a repeating series.
     * THIS: the occurrence on [occDate] is removed from the series and [edited] becomes a standalone one-off.
     * FOLLOWING: the series ends the day before [occDate]; [edited] starts a new series.
     * ALL: [edited] replaces the series, keeping its original start date shifted by the same
     *      number of days the user moved the occurrence.
     */
    suspend fun editSeries(context: Context, original: Reminder, edited: Reminder, occDate: LocalDate, scope: SeriesScope): Long = lock.withLock {
        val dao = dao(context)
        val s = settings(context)
        val now = Instant.now()
        val editedStart = edited.localDateTimeOrNull() ?: return@withLock original.id
        val id: Long = when (scope) {
            SeriesScope.THIS -> {
                dao.update(prepare(original.withExcluded(occDate).withDedupeKey(), s, 0, now))
                val single = edited.copy(id = 0, repeat = Repeat.NONE, interval = 1, weekdays = 0, endDate = null, excludedDates = "",
                    nextAt = null, snoozeAt = null, lastFiredAt = null, createdAt = System.currentTimeMillis()).withDedupeKey()
                dao.insert(prepare(single, s, 0, now))
            }
            SeriesScope.FOLLOWING -> {
                val seriesStart = original.localDateTimeOrNull()?.toLocalDate()
                if (seriesStart == null || !occDate.isAfter(seriesStart)) {
                    // Editing from the first occurrence: same as changing everything.
                    val all = edited.copy(id = original.id).withDedupeKey()
                    dao.update(prepare(all, s, 0, now)); original.id
                } else {
                    val cutoff = occDate.minusDays(1)
                    val end = original.endDateOrNull()?.let { if (it.isBefore(cutoff)) it else cutoff } ?: cutoff
                    dao.update(prepare(original.copy(endDate = end.toString()).withDedupeKey(), s, 0, now))
                    val keep = original.excludedDateSet().filter { !it.isBefore(editedStart.toLocalDate()) }.sorted().joinToString(",")
                    val next = edited.copy(id = 0, excludedDates = keep, nextAt = null, snoozeAt = null, lastFiredAt = null,
                        createdAt = System.currentTimeMillis()).withDedupeKey()
                    dao.insert(prepare(next, s, 0, now))
                }
            }
            SeriesScope.ALL -> {
                val origStart = original.localDateTimeOrNull()
                val shift = java.time.temporal.ChronoUnit.DAYS.between(occDate, editedStart.toLocalDate())
                val newStart = (origStart?.toLocalDate()?.plusDays(shift) ?: editedStart.toLocalDate()).atTime(editedStart.toLocalTime())
                val all = edited.copy(id = original.id, localDateTime = newStart.format(Reminder.DT_FORMAT), excludedDates = original.excludedDates).withDedupeKey()
                dao.update(prepare(all, s, 0, now)); original.id
            }
        }
        armAlarm(context)
        id
    }

    suspend fun deleteFromSeries(context: Context, original: Reminder, occDate: LocalDate, scope: SeriesScope) = lock.withLock {
        val dao = dao(context)
        val s = settings(context)
        val now = Instant.now()
        when (scope) {
            SeriesScope.THIS -> dao.update(prepare(original.withExcluded(occDate).withDedupeKey(), s, 0, now))
            SeriesScope.FOLLOWING -> {
                val seriesStart = original.localDateTimeOrNull()?.toLocalDate()
                if (seriesStart == null || !occDate.isAfter(seriesStart)) { dao.deleteById(original.id); Notifier.cancel(context, original.id) }
                else dao.update(prepare(original.copy(endDate = occDate.minusDays(1).toString()).withDedupeKey(), s, 0, now))
            }
            SeriesScope.ALL -> { dao.deleteById(original.id); Notifier.cancel(context, original.id) }
        }
        armAlarm(context)
    }

    suspend fun delete(context: Context, id: Long) = lock.withLock {
        dao(context).deleteById(id)
        Notifier.cancel(context, id)
        armAlarm(context)
    }

    suspend fun setEnabled(context: Context, id: Long, enabled: Boolean) = lock.withLock {
        val dao = dao(context)
        val r = dao.byId(id) ?: return@withLock
        val s = settings(context)
        val pool = dao.countEnabledRandom()
        val updated = if (enabled) prepare(r.copy(enabled = true, nextAt = null, snoozeAt = null), s, pool + 1, Instant.now())
                      else r.copy(enabled = false, nextAt = null, snoozeAt = null)
        dao.update(updated)
        if (r.isRandom && s.frequencyMode == io.github.xalrk.nudge.data.FrequencyMode.WHOLE_POOL) resampleAllRandom(context)
        armAlarm(context)
    }

    /** Recompute every scheduled reminder's nextAt (after zone/time changes, reboot, app open) and re-arm. */
    suspend fun refresh(context: Context, resampleRandom: Boolean = false) = lock.withLock {
        val dao = dao(context)
        val s = settings(context)
        val now = Instant.now()
        val all = dao.all()
        val pool = all.count { it.isRandom && it.enabled }
        val updated = all.mapNotNull { r ->
            if (!r.enabled) return@mapNotNull null
            when (r.kind) {
                Kind.SCHEDULED -> {
                    // Keep an already-due (missed) reminder due so it still gets delivered.
                    val stillDue = r.nextAt != null && r.nextAt <= now.toEpochMilli()
                    if (stillDue) null else {
                        val next = Recurrence.nextOccurrenceAfter(r, now)?.toInstant()?.toEpochMilli()
                        if (next != r.nextAt) r.copy(nextAt = next) else null
                    }
                }
                Kind.RANDOM -> {
                    if (resampleRandom || r.nextAt == null) r.copy(nextAt = sampleRandom(s, pool, now)) else null
                }
            }
        }
        if (updated.isNotEmpty()) dao.updateAll(updated)
        events(context).deleteOlderThan(now.toEpochMilli() - HISTORY_MILLIS)
        armAlarm(context)
    }

    /** Called from the alarm receiver. Delivers everything due and advances the rows. */
    suspend fun fireDue(context: Context) = lock.withLock {
        val dao = dao(context)
        val s = settings(context)
        val now = Instant.now()
        val nowMs = now.toEpochMilli()
        val due = dao.due(nowMs)
        val pool = dao.countEnabledRandom()
        for (r in due) {
            var updated = r
            val snoozeDue = r.snoozeAt != null && r.snoozeAt <= nowMs
            val mainDue = r.nextAt != null && r.nextAt <= nowMs
            var fire = snoozeDue
            if (snoozeDue) updated = updated.copy(snoozeAt = null)
            if (mainDue) {
                when (r.kind) {
                    Kind.SCHEDULED -> {
                        fire = true
                        val next = Recurrence.nextOccurrenceAfter(r, now)?.toInstant()?.toEpochMilli()
                        updated = updated.copy(nextAt = next, enabled = if (r.repeat == Repeat.NONE) false else r.enabled)
                    }
                    Kind.RANDOM -> {
                        // Only deliver inside active hours; a random reminder that came due while the
                        // phone was off overnight is simply re-rolled instead of waking anyone up.
                        val z = now.atZone(ZoneId.systemDefault())
                        if (RandomScheduler.isInsideActiveWindow(z, s.activeStartHour, s.activeEndHour)) fire = true
                        updated = updated.copy(nextAt = sampleRandom(s, pool, now))
                    }
                }
            }
            if (fire) {
                deliver(context, r, nowMs)
                updated = updated.copy(lastFiredAt = nowMs)
            }
            dao.update(updated)
        }
        armAlarm(context)
    }

    suspend fun snooze(context: Context, id: Long, minutes: Int = 10) = lock.withLock {
        val dao = dao(context)
        val r = dao.byId(id) ?: return@withLock
        dao.update(r.copy(snoozeAt = System.currentTimeMillis() + minutes * 60_000L, enabled = true))
        Notifier.cancel(context, id)
        armAlarm(context)
    }

    /** Fire one random reminder right now (for testing from Settings). */
    suspend fun fireRandomNow(context: Context): Boolean = lock.withLock {
        val dao = dao(context)
        val pool = dao.enabledRandom()
        val pick = pool.randomOrNull() ?: return@withLock false
        val now = System.currentTimeMillis()
        deliver(context, pick, now)
        dao.update(pick.copy(lastFiredAt = now))
        true
    }

    suspend fun resampleAllRandomLocked(context: Context) = lock.withLock { resampleAllRandom(context); armAlarm(context) }

    private suspend fun resampleAllRandom(context: Context) {
        val dao = dao(context)
        val s = settings(context)
        val pool = dao.enabledRandom()
        val now = Instant.now()
        dao.updateAll(pool.map { it.copy(nextAt = sampleRandom(s, pool.size, now)) })
    }

    private fun prepare(r: Reminder, s: SettingsSnapshot, pool: Int, now: Instant): Reminder {
        if (!r.enabled) return r.copy(nextAt = null)
        return when (r.kind) {
            Kind.SCHEDULED -> {
                val next = Recurrence.nextOccurrenceAfter(r, now)?.toInstant()?.toEpochMilli()
                r.copy(nextAt = next, enabled = next != null)
            }
            Kind.RANDOM -> r.copy(nextAt = r.nextAt ?: sampleRandom(s, pool, now), localDateTime = null, repeat = Repeat.NONE)
        }
    }

    private fun sampleRandom(s: SettingsSnapshot, pool: Int, now: Instant): Long =
        RandomScheduler.sampleNext(now.atZone(ZoneId.systemDefault()), s, pool).toInstant().toEpochMilli()

    // ----------------------------------------------------------------- alarm

    fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private suspend fun armAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context)
        val at = dao(context).earliestTrigger()
        if (at == null) { am.cancel(pi); return }
        val triggerAt = maxOf(at, System.currentTimeMillis() + 1000)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm denied, falling back to inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        Log.d(TAG, "next alarm at ${ZonedDateTime.ofInstant(Instant.ofEpochMilli(triggerAt), ZoneId.systemDefault())} exact=$exact")
    }
}
