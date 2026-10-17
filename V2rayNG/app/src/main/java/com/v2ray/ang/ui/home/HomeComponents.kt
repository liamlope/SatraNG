package com.v2ray.ang.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.ui.compose.SatraColors
import com.v2ray.ang.ui.main.ServerRowUiModel
import java.text.DateFormat
import java.util.Date

@Composable
fun BigConnectOrb(
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val orbColor by animateColorAsState(
        if (isRunning) SatraColors.Teal else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "orbColor"
    )

    Box(modifier = modifier.size(168.dp), contentAlignment = Alignment.Center) {
        if (isRunning) {
            Box(
                Modifier
                    .size(168.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.35f }
                    .border(2.dp, SatraColors.Teal, CircleShape)
            )
        }
        Box(
            Modifier
                .size(132.dp)
                .shadow(24.dp, CircleShape, ambientColor = orbColor.copy(0.45f), spotColor = orbColor.copy(0.55f))
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(orbColor, if (isRunning) SatraColors.TealDeep else Color(0xFF5A7084))
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
                ),
                contentDescription = stringResource(
                    if (isRunning) R.string.acc_stop else R.string.acc_start
                ),
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
fun LocationSelectCard(
    row: ServerRowUiModel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row?.flag ?: "🌐",
            fontSize = 28.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(Modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.satra_choose_location),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = row?.let {
                    listOfNotNull(
                        it.locationLabel.takeIf { l -> l.isNotBlank() },
                        it.remarks
                    ).joinToString(" · ").ifBlank { it.remarks }
                } ?: stringResource(R.string.satra_best_location),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (row != null && row.testDelayMillis != 0L) {
            PingBadge(row.testDelayMillis)
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            painterResource(R.drawable.ic_arrow_back_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = 180f }
        )
    }
}

@Composable
fun PingBadge(delayMillis: Long) {
    val color = when {
        delayMillis < 0L -> SatraColors.Danger
        delayMillis < 200L -> SatraColors.TealDeep
        delayMillis < 500L -> SatraColors.Amber
        else -> SatraColors.Danger
    }
    val label = if (delayMillis < 0L) "—" else "${delayMillis}ms"
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SubscriptionUsageCard(
    subscription: SubscriptionItem?,
    serverCount: Int,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (subscription == null) {
        Box(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onOpen)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.satra_sub_empty),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val used = subscription.usedBytes()
    val fraction = subscription.usageFraction()
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape)
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    subscription.profileTitle?.takeIf { it.isNotBlank() } ?: subscription.remarks,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.satra_sub_servers_count, serverCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(R.string.satra_sub_refresh),
                color = SatraColors.TealDeep,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRefresh)
                    .padding(8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        if (fraction != null) {
            val progress by animateFloatAsState(fraction, label = "usage")
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = when {
                    subscription.isNearQuota(0.9f) -> SatraColors.Danger
                    subscription.isNearQuota(0.75f) -> SatraColors.Amber
                    else -> SatraColors.Teal
                },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${used.toTrafficString()} / ${subscription.total.toTrafficString()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val rem = subscription.remainingBytes()
                Text(
                    if (rem != null) "${stringResource(R.string.satra_sub_remaining)} ${rem.toTrafficString()}"
                    else stringResource(R.string.satra_sub_unlimited),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                "${stringResource(R.string.satra_sub_usage)}: ${used.toTrafficString()} · ${stringResource(R.string.satra_sub_unlimited)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            expireLabel(subscription),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                subscription.isExpired() -> SatraColors.Danger
                subscription.isNearExpire() -> SatraColors.Amber
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        if (subscription.isNearQuota(0.9f)) {
            Text(
                stringResource(R.string.satra_sub_low_quota),
                style = MaterialTheme.typography.labelSmall,
                color = SatraColors.Danger
            )
        } else if (subscription.isNearExpire()) {
            Text(
                stringResource(R.string.satra_sub_near_expire),
                style = MaterialTheme.typography.labelSmall,
                color = SatraColors.Amber
            )
        }

        subscription.announce?.takeIf { it.isNotBlank() }?.let { announce ->
            Spacer(Modifier.height(6.dp))
            Text(announce, style = MaterialTheme.typography.bodySmall, color = SatraColors.TealSoft, maxLines = 2)
        }
    }
}

@Composable
private fun expireLabel(subscription: SubscriptionItem): String {
    if (subscription.expire <= 0L) return stringResource(R.string.satra_sub_no_expiry)
    if (subscription.isExpired()) return stringResource(R.string.satra_sub_expired)
    val ms = if (subscription.expire > 10_000_000_000L) subscription.expire else subscription.expire * 1000L
    val formatted = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    return "${stringResource(R.string.satra_sub_expires)}: $formatted"
}

@Composable
fun LocationRow(
    row: ServerRowUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) SatraColors.Teal.copy(0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "locBg"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(row.flag.ifBlank { GeoIpResolver.flagEmoji(row.countryCode) }, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.remarks,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    row.locationLabel.takeIf { it.isNotBlank() },
                    row.typeDescription
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (row.testDelayMillis != 0L) PingBadge(row.testDelayMillis)
    }
}

@Composable
fun UsageRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = SatraColors.Teal,
) {
    val sweep by animateFloatAsState(fraction * 360f, label = "ring")
    Canvas(modifier = modifier.size(56.dp)) {
        drawArc(
            color = color.copy(alpha = 0.18f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
