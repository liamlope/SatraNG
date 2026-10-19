package com.v2ray.ang.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.HybridUsageTracker
import com.v2ray.ang.ui.compose.liquid.SatraGlassCard
import com.v2ray.ang.ui.compose.liquid.SatraGlassIconButton
import com.v2ray.ang.ui.compose.liquid.SatraGlassToggle
import com.v2ray.ang.ui.compose.liquid.onGlassPrimary
import com.v2ray.ang.ui.compose.liquid.onGlassSecondary
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.subscription.SubEditActivity
import com.v2ray.ang.util.Utils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatraSubsTab(
    modifier: Modifier,
    subscriptions: List<Pair<String, SubscriptionItem>>,
    serverCounts: Map<String, Int>,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit,
    onRefreshOne: (String) -> Unit = {},
    onToggleEnabled: (String, Boolean) -> Unit,
    onOpenEdit: (String?) -> Unit,
    onOpenManage: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.satra_subs_title),
                style = MaterialTheme.typography.headlineMedium,
                color = onGlassPrimary(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.satra_sub_update_all), color = SatraTokens.Amber)
            }
            SatraGlassIconButton(
                onClick = onOpenManage,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24dp),
                    contentDescription = stringResource(R.string.satra_sub_settings),
                    tint = SatraTokens.Amber,
                    modifier = Modifier.size(22.dp),
                )
            }
            SatraGlassIconButton(
                onClick = {
                    onOpenEdit(null)
                    context.startActivity(Intent(context, SubEditActivity::class.java))
                },
                modifier = Modifier.size(44.dp).clip(CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = stringResource(R.string.satra_subs_add),
                    tint = SatraTokens.Teal,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRefresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (subscriptions.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    SatraGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.satra_sub_empty), color = onGlassPrimary())
                    }
                }
                return@PullToRefreshBox
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(subscriptions, key = { it.first }) { (id, sub) ->
                    PreviewStyleSubCard(
                        id = id,
                        sub = sub,
                        serverCount = serverCounts[id] ?: 0,
                        onToggle = { onToggleEnabled(id, it) },
                        onUpdate = { onRefreshOne(id) },
                        onOpenProfile = {
                            val url = sub.profileWebPageUrl?.takeIf { it.isNotBlank() }
                                ?: sub.supportUrl?.takeIf { it.isNotBlank() }
                            if (url != null) Utils.openUri(context, url)
                            else {
                                onOpenEdit(id)
                                context.startActivity(
                                    Intent(context, SubEditActivity::class.java).putExtra("subId", id),
                                )
                            }
                        },
                        onOpenSupport = {
                            val url = sub.supportUrl?.takeIf { it.isNotBlank() }
                                ?: sub.profileWebPageUrl?.takeIf { it.isNotBlank() }
                            if (url != null) Utils.openUri(context, url)
                        },
                        onOpenEdit = {
                            onOpenEdit(id)
                            context.startActivity(
                                Intent(context, SubEditActivity::class.java).putExtra("subId", id),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewStyleSubCard(
    id: String,
    sub: SubscriptionItem,
    serverCount: Int,
    onToggle: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenEdit: () -> Unit,
) {
    HybridUsageTracker.ensureBaseline(id, sub)
    val usage = HybridUsageTracker.displayFor(id, sub)
    val title = sub.profileTitle?.takeIf { it.isNotBlank() } ?: sub.remarks
    val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
    val days = subscriptionDaysLeftLocal(sub)
    val totalDays = maxOf((days ?: 0).coerceAtLeast(1), 30)
    val timeFrac = when {
        days == null -> 1f
        days <= 0 -> 0f
        else -> (days.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
    }
    val dataFrac = usage.fractionUsed?.let { 1f - it } ?: 1f

    SatraGlassCard(onClick = onOpenEdit) {
        // Top: avatar · title/meta · actions · toggle
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(SatraTokens.Amber, SatraTokens.AmberDeep),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initial,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onGlassPrimary(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildString {
                    append(stringResource(R.string.satra_sub_servers_count, serverCount))
                    if (sub.lastUpdated > 0L) {
                        append(" · ")
                        append(stringResource(R.string.satra_sub_updated, Utils.formatTimestamp(sub.lastUpdated)))
                    }
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = onGlassSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (sub.showSupportLinks) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniIconBtn(
                        icon = R.drawable.ic_about_24dp,
                        a11y = stringResource(R.string.satra_sub_profile_page),
                        onClick = onOpenProfile,
                    )
                    MiniIconBtn(
                        icon = R.drawable.ic_feedback_24dp,
                        a11y = stringResource(R.string.satra_sub_support),
                        onClick = onOpenSupport,
                    )
                }
            }
            SatraGlassToggle(
                checked = sub.enabled,
                onCheckedChange = onToggle,
                onLabel = stringResource(R.string.satra_settings_on),
                offLabel = stringResource(R.string.satra_settings_off),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Data + Time bars (preview layout)
        if (sub.showUsage) {
            UsageBarRow(
                label = stringResource(R.string.satra_home_quota_data),
                value = if (sub.total > 0L) {
                    stringResource(
                        R.string.satra_home_quota_data_of,
                        usage.remaining?.toTrafficString()
                            ?: (sub.total - usage.displayUsed).coerceAtLeast(0L).toTrafficString(),
                        sub.total.toTrafficString(),
                    )
                } else {
                    "${usage.displayUsed.toTrafficString()} · ${stringResource(R.string.satra_sub_unlimited)}"
                },
                fraction = dataFrac,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (sub.showExpire) {
            UsageBarRow(
                label = stringResource(R.string.satra_home_quota_time),
                value = when {
                    days == null -> stringResource(R.string.satra_sub_no_expiry)
                    days <= 0 -> stringResource(R.string.satra_sub_expired)
                    else -> stringResource(R.string.satra_home_quota_days_of, days, totalDays)
                },
                fraction = timeFrac,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Foot: Enabled chip + Update
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (sub.enabled) {
                    stringResource(R.string.satra_subs_enabled)
                } else {
                    stringResource(R.string.satra_settings_off)
                },
                style = MaterialTheme.typography.labelSmall,
                color = onGlassSecondary(),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.satra_sub_update_one),
                style = MaterialTheme.typography.labelLarge,
                color = SatraTokens.Amber,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SatraTokens.Amber.copy(alpha = 0.16f))
                    .clickable(onClick = onUpdate)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun UsageBarRow(label: String, value: String, fraction: Float) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = onGlassSecondary(),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = onGlassPrimary(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(4.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(6.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(SatraTokens.AmberDeep, SatraTokens.Amber),
                    ),
                ),
        )
    }
}

@Composable
private fun MiniIconBtn(icon: Int, a11y: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = a11y,
            tint = onGlassSecondary(),
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun subscriptionDaysLeftLocal(subscription: SubscriptionItem): Int? {
    if (subscription.expire <= 0L) return null
    val expireMs = if (subscription.expire > 10_000_000_000L) {
        subscription.expire
    } else {
        subscription.expire * 1000L
    }
    val left = expireMs - System.currentTimeMillis()
    if (left <= 0L) return 0
    return ((left + 86_399_999L) / 86_400_000L).toInt()
}
