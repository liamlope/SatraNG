package com.v2ray.ang.ui.compose.satra

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.SatraColors
import com.v2ray.ang.ui.main.MainStatus

enum class SatraOrbState { Ready, Connecting, Connected }

@Composable
fun SatraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(SatraTokens.RadiusLg)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                )
            } else Modifier
        )
        .padding(SatraTokens.SpaceLg)
    Box(modifier.then(base)) { content() }
}

@Composable
fun SatraPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        if (selected) SatraTokens.Teal.copy(alpha = 0.22f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "pillBg",
    )
    val fg by animateColorAsState(
        if (selected) SatraTokens.TealDeep else MaterialTheme.colorScheme.onSurface,
        label = "pillFg",
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(SatraTokens.RadiusSm))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun SatraStatusChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = SatraTokens.TealDeep,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(SatraTokens.Teal.copy(0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun SatraPingBadge(delayMs: Long, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        when {
            delayMs <= 0L -> MaterialTheme.colorScheme.onSurfaceVariant
            delayMs < 150L -> SatraColors.TealDeep
            delayMs < 400L -> SatraColors.Amber
            else -> SatraColors.Danger
        },
        label = "pingColor",
    )
    val label = when {
        delayMs <= 0L -> "—"
        delayMs >= 10_000L -> "timeout"
        else -> "${delayMs}ms"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun SatraUsageRing(
    fraction: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 7.dp,
    warn: Boolean = false,
) {
    val reduce = rememberReduceMotion()
    val progress = satraAnimatedFraction(fraction ?: 0f, "usageRing", reduce)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val active = if (warn) SatraColors.Danger else SatraTokens.Teal
    Canvas(modifier = modifier.size(size)) {
        val strokePx = stroke.toPx()
        val arcSize = Size(this.size.minDimension - strokePx, this.size.minDimension - strokePx)
        val topLeft = Offset(strokePx / 2f, strokePx / 2f)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        if (fraction != null) {
            drawArc(
                color = active,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun SatraShimmer(modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.pulse(),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerShift",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        modifier
            .clip(RoundedCornerShape(SatraTokens.RadiusMd))
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(shift * 400f - 200f, 0f),
                    end = Offset(shift * 400f + 200f, 200f),
                )
            )
    )
}

@Composable
fun SatraOrb(
    state: SatraOrbState,
    statusLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (state == SatraOrbState.Connecting && !reduce) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = SatraMotionSpec.pulse(),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )
    val connected = state == SatraOrbState.Connected
    val core by animateColorAsState(
        when {
            connected -> SatraTokens.TealDeep
            state == SatraOrbState.Connecting -> SatraTokens.Amber
            else -> SatraColors.Slate
        },
        label = "orbCore",
    )
    val glow = core.copy(alpha = 0.35f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(168.dp)
                .scale(pulse)
                .semantics {
                    contentDescription = statusLabel
                    role = Role.Button
                }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(glow, Color.Transparent)))
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(core, core.copy(alpha = 0.82f))))
                    .border(2.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_satra_mark),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        Spacer(Modifier.height(SatraTokens.SpaceMd))
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

fun MainStatus.toOrbState(isRunning: Boolean): SatraOrbState = when {
    this == MainStatus.Testing || this is MainStatus.TestProgress -> SatraOrbState.Connecting
    isRunning -> SatraOrbState.Connected
    else -> SatraOrbState.Ready
}

@Composable
fun SatraBottomNavBar(
    selected: Int,
    items: List<Triple<Int, Int, Int>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (index, labelRes, iconRes) ->
            SatraNavItem(
                selected = selected == index,
                labelRes = labelRes,
                iconRes = iconRes,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RowScope.SatraNavItem(
    selected: Boolean,
    labelRes: Int,
    iconRes: Int,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        if (selected) SatraTokens.TealDeep else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navTint",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(if (selected) SatraTokens.Teal.copy(alpha = 0.16f) else Color.Transparent)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SatraSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = SatraTokens.SpaceSm),
    )
}

@Composable
fun SatraRowAction(
    title: String,
    subtitle: String? = null,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SatraTokens.RadiusMd))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = SatraTokens.TealDeep,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .scale(scaleX = -1f, scaleY = 1f),
        )
    }
}
