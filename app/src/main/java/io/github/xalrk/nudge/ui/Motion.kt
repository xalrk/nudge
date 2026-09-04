package io.github.xalrk.nudge.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext

/** Coroutine context element that pins animation durations to normal speed. */
val NormalMotion: MotionDurationScale = object : MotionDurationScale { override val scaleFactor: Float get() = 1f }

/** True when the system "remove animations" / animator scale 0 setting is on. */
fun animationsReduced(context: Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f

@Composable
fun rememberAnimationsReduced(): Boolean {
    val ctx = LocalContext.current
    return remember { animationsReduced(ctx) }
}
