package io.github.xalrk.nudge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import io.github.xalrk.nudge.data.NudgeDatabase
import io.github.xalrk.nudge.data.Settings
import io.github.xalrk.nudge.update.UpdateWorker

class NudgeApp : Application() {
    val database: NudgeDatabase by lazy { NudgeDatabase.get(this) }
    val settings: Settings by lazy { Settings(this) }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        // Android decides sound and vibration per channel, so one channel per combination
        // lets each reminder choose. The user can still fine-tune any of them in system settings.
        val reminderChannels = listOf(
            Triple(CHANNEL_REMINDERS, getString(R.string.channel_reminders), true to true),
            Triple(CHANNEL_REMINDERS_SOUND, getString(R.string.channel_reminders_sound), true to false),
            Triple(CHANNEL_REMINDERS_VIBRATE, getString(R.string.channel_reminders_vibrate), false to true),
            Triple(CHANNEL_REMINDERS_SILENT, getString(R.string.channel_reminders_silent), false to false),
        )
        for ((id, name, sv) in reminderChannels) {
            val (sound, vibrate) = sv
            nm.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = getString(R.string.channel_reminders_desc)
                    if (!sound) setSound(null, null)
                    enableVibration(vibrate)
                }
            )
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = getString(R.string.channel_updates_desc) }
        )
        if (settings.autoUpdateCheck) UpdateWorker.schedule(this)
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_REMINDERS_SOUND = "reminders_sound"
        const val CHANNEL_REMINDERS_VIBRATE = "reminders_vibrate"
        const val CHANNEL_REMINDERS_SILENT = "reminders_silent"

        fun reminderChannel(sound: Boolean, vibrate: Boolean): String = when {
            sound && vibrate -> CHANNEL_REMINDERS
            sound -> CHANNEL_REMINDERS_SOUND
            vibrate -> CHANNEL_REMINDERS_VIBRATE
            else -> CHANNEL_REMINDERS_SILENT
        }
        const val CHANNEL_UPDATES = "updates"
    }
}
