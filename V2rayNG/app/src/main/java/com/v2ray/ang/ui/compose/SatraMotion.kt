package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object SatraMotion {
    val snappy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val soft = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessLow
    )
    fun fade(ms: Int = 280) = tween<Float>(durationMillis = ms)
    fun slide(ms: Int = 320) = tween<Float>(durationMillis = ms)
}
