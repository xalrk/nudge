package io.github.xalrk.nudge.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.xalrk.nudge.NudgeApp
import java.util.concurrent.TimeUnit

/**
 * Runs about once a day, only when the device is online and the battery is not low.
 * WorkManager batches it with other deferred work, so it costs no extra wake-ups.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NudgeApp
        if (!app.settings.autoUpdateCheck) return Result.success()
        return when (val r = UpdateChecker.check()) {
            is UpdateChecker.Result.Available -> {
                // Notify once per version, not every day.
                if (app.settings.lastNotifiedUpdate != r.info.version) {
                    UpdateChecker.notify(app, r.info)
                    app.settings.lastNotifiedUpdate = r.info.version
                }
                Result.success()
            }
            UpdateChecker.Result.UpToDate -> Result.success()
            is UpdateChecker.Result.Failed -> Result.retry()
        }
    }

    companion object {
        private const val NAME = "update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInitialDelay(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
