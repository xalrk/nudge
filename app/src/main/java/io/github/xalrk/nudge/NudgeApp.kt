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
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = getString(R.string.channel_reminders_desc) }
        nm.createNotificationChannel(channel)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = getString(R.string.channel_updates_desc) }
        )
        if (settings.autoUpdateCheck) UpdateWorker.schedule(this)
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_UPDATES = "updates"
    }
}
