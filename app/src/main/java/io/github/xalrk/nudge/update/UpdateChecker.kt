package io.github.xalrk.nudge.update

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.xalrk.nudge.BuildConfig
import io.github.xalrk.nudge.NudgeApp
import io.github.xalrk.nudge.R
import io.github.xalrk.nudge.scheduler.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val url: String, val notes: String)

/**
 * Asks GitHub for the latest release and compares it with the installed version.
 * One small HTTPS request; nothing is sent besides the request itself.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    const val REPO = "xalrk/nudge"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val NOTIFICATION_ID = 900_001

    sealed class Result {
        data class Available(val info: UpdateInfo) : Result()
        data object UpToDate : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Nudge/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (conn.responseCode != 200) return@withContext Result.Failed("GitHub answered ${conn.responseCode}")
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name").removePrefix("v")
                val url = json.optString("html_url").ifEmpty { RELEASES_PAGE }
                if (tag.isEmpty()) return@withContext Result.Failed("no release found")
                if (isNewer(tag, BuildConfig.VERSION_NAME)) Result.Available(UpdateInfo(tag, url, json.optString("body")))
                else Result.UpToDate
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "update check failed", e)
            Result.Failed(e.message ?: "network error")
        }
    }

    /** Numeric dotted-version comparison: 1.0.10 > 1.0.9, ignores suffixes like -debug. */
    fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun notify(context: Context, info: UpdateInfo) {
        if (!Notifier.canPost(context)) return
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = "Nudge ${info.version} is available (you have ${BuildConfig.VERSION_NAME}). Tap to open the download page."
        val n = Notification.Builder(context, NudgeApp.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Update available")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }
}
