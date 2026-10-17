package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.tween
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec

/** Compatibility aliases — prefer satra.SatraMotionSpec for new code. */
object SatraMotion {
    val snappy = SatraMotionSpec.snappy
    val soft = SatraMotionSpec.soft
    fun fade(ms: Int = SatraMotionSpec.EnterMs) = tween<Float>(durationMillis = ms)
    fun slide(ms: Int = SatraMotionSpec.SettleMs) = tween<Float>(durationMillis = ms)
}
