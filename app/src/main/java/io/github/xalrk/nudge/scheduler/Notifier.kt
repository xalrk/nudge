package io.github.xalrk.nudge.scheduler

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.github.xalrk.nudge.NudgeApp
import io.github.xalrk.nudge.R
import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.ui.MainActivity

object Notifier {
    const val EXTRA_REMINDER_ID = "reminder_id"

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, r: Reminder) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, r.id.toInt(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_REMINDER_ID, r.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getBroadcast(
            context, r.id.toInt(),
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SNOOZE).putExtra(EXTRA_REMINDER_ID, r.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(context, NudgeApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setShowWhen(true)
            .addAction(Notification.Action.Builder(null, "Snooze 10 min", snooze).build())
        if (r.body.isNotBlank()) {
            builder.setContentText(r.body).setStyle(Notification.BigTextStyle().bigText(r.body))
        }
        context.getSystemService(NotificationManager::class.java).notify(r.id.toInt(), builder.build())
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(id.toInt())
    }
}
