package io.github.xalrk.nudge.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SNOOZE -> {
                        val id = intent.getLongExtra(Notifier.EXTRA_REMINDER_ID, -1)
                        val minutes = intent.getIntExtra(Notifier.EXTRA_SNOOZE_MINUTES, 10)
                        if (id >= 0) {
                            if (minutes == Notifier.SNOOZE_MORNING) ReminderEngine.snoozeUntilMorning(app, id)
                            else ReminderEngine.snooze(app, id, minutes)
                        }
                    }
                    else -> ReminderEngine.fireDue(app)
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "io.github.xalrk.nudge.FIRE"
        const val ACTION_SNOOZE = "io.github.xalrk.nudge.SNOOZE"
    }
}
