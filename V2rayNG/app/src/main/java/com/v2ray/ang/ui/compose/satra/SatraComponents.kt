package com.v2ray.ang.ui.compose.satra

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import com.v2ray.ang.ui.compose.LocalDarkTheme
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

/** @deprecated ExpressVPN-style orb — Liquid Glass uses [com.v2ray.ang.ui.compose.liquid.SatraGlassCapsule]. */
@Deprecated("Use SatraGlassCapsule on Home; orb is removed from primary UX")
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
    val connecting = state == SatraOrbState.Connecting
    val connected = state == SatraOrbState.Connected
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = when {
            reduce -> 1f
            connecting -> 1.1f
            connected -> 1.04f
            else -> 1f
        },
        animationSpec = infiniteRepeatable(
            animation = SatraMotionSpec.pulse(),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )
    val ringSweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (connecting && !reduce) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbSweep",
    )
    val core by animateColorAsState(
        when {
            connected -> SatraTokens.TealDeep
            connecting -> SatraTokens.Amber
            else -> SatraColors.Slate
        },
        label = "orbCore",
    )
    val settleScale by animateFloatAsState(
        targetValue = if (connected && !reduce) 1f else if (connecting) 0.94f else 1f,
        animationSpec = SatraMotionSpec.soft,
        label = "orbSettle",
    )
    val glow = core.copy(alpha = if (connecting) 0.5f else 0.35f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(188.dp)
                .scale(pulse * settleScale)
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
            if (connecting || connected) {
                Canvas(Modifier.size(168.dp)) {
                    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = core.copy(alpha = 0.55f),
                        startAngle = ringSweep - 90f,
                        sweepAngle = if (connecting) 110f else 360f,
                        useCenter = false,
                        style = stroke,
                        size = Size(size.minDimension, size.minDimension),
                        topLeft = Offset(
                            (size.width - size.minDimension) / 2f,
                            (size.height - size.minDimension) / 2f,
                        ),
                    )
                    if (connected) {
                        drawCircle(
                            color = SatraTokens.Teal.copy(alpha = 0.2f),
                            radius = size.minDimension / 2f - 6.dp.toPx(),
                            style = Stroke(width = 8.dp.toPx()),
                        )
                    }
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(core, core.copy(alpha = 0.82f))))
                    .border(2.dp, Color.White.copy(alpha = 0.28f), CircleShape),
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
    val dark = LocalDarkTheme.current
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .satraGlass(RoundedCornerShape(SatraLiquid.CapsuleRadius), dark)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .height(64.dp),
    ) {
        val count = items.size.coerceAtLeast(1)
        val slot = maxWidth / count
        val indicatorOffset by animateFloatAsState(
            targetValue = selected.toFloat(),
            animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.liquid,
            label = "navPill",
        )
        Box(
            Modifier
                .offset(x = slot * indicatorOffset)
                .width(slot)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(SatraLiquid.CapsuleRadius))
                .background(SatraTokens.Teal.copy(alpha = if (dark) 0.28f else 0.22f)),
        )
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (index, labelRes, iconRes) ->
                SatraNavItem(
                    selected = selected == index,
                    labelRes = labelRes,
                    iconRes = iconRes,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    },
                )
            }
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
        animationSpec = SatraMotionSpec.short(),
        label = "navTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = SatraMotionSpec.snappy,
        label = "navScale",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(labelRes),
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
