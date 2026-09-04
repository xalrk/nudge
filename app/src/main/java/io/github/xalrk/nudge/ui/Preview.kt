package io.github.xalrk.nudge.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/** Plays what a notification would do, so the editor toggles can be tried before saving. */
object Preview {
    fun sound(context: Context) {
        runCatching {
            val ringtone = RingtoneManager.getRingtone(context, Settings.System.DEFAULT_NOTIFICATION_URI) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        }
    }

    fun vibrate(context: Context) {
        runCatching {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            if (!vibrator.hasVibrator()) return
            // The classic two-buzz notification pattern.
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 220), -1))
        }
    }
}
