package io.github.xalrk.nudge.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-plans everything when the device reboots, the app is updated, the clock or
 * time zone changes, or the exact-alarm permission is toggled. Random reminders are
 * re-rolled on zone changes so they land inside the new local active window.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        val resample = intent.action == Intent.ACTION_TIMEZONE_CHANGED
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderEngine.refresh(app, resampleRandom = resample)
                ReminderEngine.fireDue(app)
            } finally {
                result.finish()
            }
        }
    }
}
