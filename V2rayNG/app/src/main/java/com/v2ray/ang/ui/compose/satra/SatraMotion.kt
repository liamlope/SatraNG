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

/**
 * Named motion tokens (Motion bible). Reduce Motion collapses infinite / shake / breath.
 */
object SatraMotionSpec {
    const val EnterMs = 320
    const val SettleMs = 480
    const val PulseMs = 1600
    const val ThemeCrossfadeMs = 420
    const val ShortMs = 180
    const val CeremonyMs = 900
    const val CapsuleMorphMs = 320
    const val FlagRevealMs = 280
    const val UsageRingMs = 600
    const val LogLineMs = 120
    const val EmptyPlusBreathMs = 1800
    const val PingPulseMs = 1200
    const val TabCrossfadeMs = 220
    const val ToastMs = 180
    const val ErrorShakeMs = 280

    val EnterEasing: Easing = FastOutSlowInEasing
    val PulseEasing: Easing = LinearEasing

    fun <T> enter() = tween<T>(EnterMs, easing = EnterEasing)
    fun <T> settle() = tween<T>(SettleMs, easing = EnterEasing)
    fun <T> short() = tween<T>(ShortMs, easing = EnterEasing)
    fun <T> pulse() = tween<T>(PulseMs, easing = PulseEasing)
    fun <T> ceremony() = tween<T>(CeremonyMs, easing = EnterEasing)
    fun <T> atmosphereLerp(ms: Int = SettleMs) = tween<T>(ms, easing = EnterEasing)
    fun <T> tabContentCrossfade() = tween<T>(TabCrossfadeMs, easing = EnterEasing)
    fun <T> capsuleStateMorph() = tween<T>(CapsuleMorphMs, easing = EnterEasing)
    fun <T> flagReveal() = tween<T>(FlagRevealMs, easing = EnterEasing)
    fun <T> usageRingFill() = tween<T>(UsageRingMs, easing = EnterEasing)
    fun <T> logLineAppend() = tween<T>(LogLineMs, easing = EnterEasing)
    fun <T> toastGlass() = tween<T>(ToastMs, easing = EnterEasing)

    /** pressGel — damping 0.68 · stiffness 500 */
    val pressGel = spring<Float>(dampingRatio = 0.68f, stiffness = 500f)

    /** liquidPill — damping 0.78 · stiffness 380 */
    val liquidPill = spring<Float>(dampingRatio = 0.78f, stiffness = 380f)

    /** sheetPresent — damping 0.86 */
    val sheetPresent = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

    val drillPush = spring<Float>(dampingRatio = 0.8f, stiffness = 420f)
    val drillPop = spring<Float>(dampingRatio = 0.82f, stiffness = 380f)
    val listItemEnter = spring<Float>(dampingRatio = 0.84f, stiffness = 360f)

    // Back-compat aliases used by older satra chrome
    val snappy = pressGel
    val soft = sheetPresent
    val liquid = liquidPill
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isReduceMotionEnabled() }
}

fun Context.isReduceMotionEnabled(): Boolean {
    @Suppress("UNUSED_VARIABLE")
    val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
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
        animationSpec = if (reduceMotion) SatraMotionSpec.short() else SatraMotionSpec.usageRingFill(),
        label = label,
    ).value
}
