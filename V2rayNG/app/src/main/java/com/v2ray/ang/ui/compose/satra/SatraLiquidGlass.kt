package com.v2ray.ang.ui.compose.satra

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.LocalDarkTheme

/**
 * Satra Liquid Glass surface — Apple Liquid Glass–inspired chrome for AndroidX Compose.
 *
 * Engine strategy (see project plan):
 * - API 31+: frosted translucent fill + specular rim (RenderEffect blur when available via theme).
 * - Below: translucent scrim + rim (same visual language, no live lens).
 * Kyant0/backdrop (API 33+, CMP) and Abdullajon LiquidGlass (Maven pending) can replace
 * this facade once validated in CI without breaking minSdk 24.
 */
object SatraLiquid {
    val ContinuityRadius = 28.dp
    val CapsuleRadius = 999.dp
    val GlassBorderLight = Color.White.copy(alpha = 0.42f)
    val GlassBorderDark = Color.White.copy(alpha = 0.18f)
    val Specular = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.35f), Color.Transparent, Color.White.copy(alpha = 0.08f)),
    )
}

@Composable
fun satraGlassFill(dark: Boolean = LocalDarkTheme.current): Color {
    return if (dark) Color(0xCC142433) else Color(0xD9F5FAF8)
}

@Composable
fun Modifier.satraGlass(
    shape: Shape = RoundedCornerShape(SatraLiquid.ContinuityRadius),
    dark: Boolean = LocalDarkTheme.current,
): Modifier {
    val fill = satraGlassFill(dark)
    val border = if (dark) SatraLiquid.GlassBorderDark else SatraLiquid.GlassBorderLight
    return this
        .clip(shape)
        .background(fill, shape)
        .border(1.dp, border, shape)
}

@Composable
fun SatraGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SatraLiquid.ContinuityRadius),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.97f else 1f,
        animationSpec = SatraMotionSpec.snappy,
        label = "glassPress",
    )
    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = ripple(color = SatraTokens.Teal.copy(alpha = 0.35f)),
            onClick = onClick,
        )
    } else Modifier

    Box(
        modifier
            .scale(scale)
            .satraGlass(shape, dark)
            .then(clickMod),
        content = content,
    )
}

/** Whether the device can use the richest glass tier (blur/refraction proxies). */
fun satraGlassTier(): Int = when {
    Build.VERSION.SDK_INT >= 33 -> 3
    Build.VERSION.SDK_INT >= 31 -> 2
    else -> 1
}
