package com.v2ray.ang.ui.compose.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion

/**
 * Simple chrome surface (no liquid glass). Soft fill + hairline border matching the
 * preview shell cards.
 */
@Composable
fun SatraGlassSurface(
    modifier: Modifier = Modifier,
    corner: Dp = SatraTokens.RadiusLg,
    capsule: Boolean = false,
    onClick: (() -> Unit)? = null,
    connectedTint: Boolean = false,
    showBorder: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.98f else 1f,
        animationSpec = SatraMotionSpec.pressGel,
        label = "chromePress",
    )
    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = ripple(color = SatraTokens.Amber.copy(alpha = 0.28f)),
            onClick = onClick,
        )
    } else {
        Modifier
    }

    val clipShape = if (capsule) {
        RoundedCornerShape(SatraTokens.RadiusCapsule)
    } else {
        RoundedCornerShape(corner)
    }
    val dark = LocalDarkTheme.current
    val fill = when {
        connectedTint -> SatraTokens.TealDeep.copy(alpha = if (dark) 0.18f else 0.12f)
        dark -> Color.White.copy(alpha = 0.06f)
        else -> Color.Black.copy(alpha = 0.04f)
    }
    val border = when {
        connectedTint -> SatraTokens.TealDeep.copy(alpha = 0.45f)
        dark -> Color.White.copy(alpha = 0.10f)
        else -> Color.Black.copy(alpha = 0.06f)
    }
    val chrome = Modifier
        .clip(clipShape)
        .background(fill, clipShape)
        .then(if (showBorder) Modifier.border(1.dp, border, clipShape) else Modifier)

    Box(
        modifier
            .scale(scale)
            .then(chrome)
            .then(clickMod),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun SatraGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    connectedTint: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    SatraGlassSurface(
        modifier = modifier.fillMaxWidth(),
        corner = SatraTokens.RadiusLg,
        onClick = onClick,
        connectedTint = connectedTint,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(SatraTokens.SpaceLg),
            content = content,
        )
    }
}

@Composable
fun SatraGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    SatraGlassSurface(
        modifier = modifier,
        corner = SatraTokens.RadiusMd,
        onClick = onClick,
        content = content,
    )
}

@Composable
fun SatraGlassSheetContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SatraGlassSurface(
        modifier = modifier.fillMaxWidth(),
        corner = SatraTokens.RadiusXl,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(SatraTokens.SpaceXl),
            content = content,
        )
    }
}

@Composable
fun onGlassPrimary(): Color = MaterialTheme.colorScheme.onSurface

@Composable
fun onGlassSecondary(): Color = MaterialTheme.colorScheme.onSurfaceVariant
