package com.v2ray.ang.ui.compose.satra

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object SatraMotionSpec {
    /** Apple-like soft settle for chrome / Liquid Glass transitions. */
    const val EnterMs = 320
    const val SettleMs = 480
    const val PulseMs = 1600
    const val ThemeCrossfadeMs = 420
    const val ShortMs = 180
    const val CeremonyMs = 900

    val EnterEasing: Easing = FastOutSlowInEasing
    val PulseEasing: Easing = LinearEasing

    fun <T> enter() = tween<T>(EnterMs, easing = EnterEasing)
    fun <T> settle() = tween<T>(SettleMs, easing = EnterEasing)
    fun <T> short() = tween<T>(ShortMs, easing = EnterEasing)
    fun <T> pulse() = tween<T>(PulseMs, easing = PulseEasing)
    fun <T> ceremony() = tween<T>(CeremonyMs, easing = EnterEasing)

    val snappy = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
    )
    val soft = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessLow,
    )
    /** Tab indicator / liquid pill travel. */
    val liquid = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 380f,
    )
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isReduceMotionEnabled() }
}

fun Context.isReduceMotionEnabled(): Boolean {
    val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    if (am?.isEnabled == true) {
        // Animator duration scale 0 = reduce motion preference on many devices
    }
    val scale = Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}

@Composable
fun satraAnimatedFraction(
    target: Float,
    label: String,
    reduceMotion: Boolean = rememberReduceMotion(),
): Float {
    return animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) SatraMotionSpec.short() else SatraMotionSpec.settle(),
        label = label,
    ).value
}
