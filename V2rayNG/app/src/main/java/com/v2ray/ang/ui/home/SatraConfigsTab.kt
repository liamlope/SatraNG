package com.v2ray.ang.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.compose.liquid.SatraGlassCard
import com.v2ray.ang.ui.compose.liquid.SatraGlassToggle
import com.v2ray.ang.ui.compose.liquid.onGlassPrimary
import com.v2ray.ang.ui.compose.liquid.onGlassSecondary
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.SatraPingBadge
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.SatraUsageRing
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion
import com.v2ray.ang.ui.main.ServerRowUiModel

@Composable
fun SatraConfigsTab(
    modifier: Modifier,
    subscriptions: List<Pair<String, SubscriptionItem>>,
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    autoConnect: Boolean,
    isTesting: Boolean,
    initialSubId: String? = null,
    onAutoConnectChange: (Boolean) -> Unit,
    onSelectServer: (String) -> Unit,
    onPingAll: () -> Unit,
    onRefreshSubs: () -> Unit,
    onOpenSubSettings: (String) -> Unit,
) {
    val reduce = rememberReduceMotion()
    var openSubId by remember { mutableStateOf<String?>(initialSubId) }
    LaunchedEffect(initialSubId) {
        if (initialSubId != null) openSubId = initialSubId
    }
    val haptics = LocalHapticFeedback.current

    AnimatedContent(
        targetState = openSubId,
        transitionSpec = {
            if (reduce) {
                fadeIn(SatraMotionSpec.short()) togetherWith fadeOut(SatraMotionSpec.short())
            } else {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            }
        },
        label = "configsDrill",
        modifier = modifier.fillMaxSize(),
    ) { subId ->
        if (subId == null) {
            SubsListLevel(
                subscriptions = subscriptions,
                rows = rows,
                onOpen = { openSubId = it },
                onRefresh = onRefreshSubs,
                onOpenSettings = onOpenSubSettings,
            )
        } else {
            val sub = subscriptions.firstOrNull { it.first == subId }?.second
            val nodes = rows.filter { it.profile.subscriptionId == subId }
            NodesListLevel(
                title = sub?.profileTitle?.takeIf { it.isNotBlank() } ?: sub?.remarks
                    ?: stringResource(R.string.satra_nav_configs),
                nodes = nodes,
                selectedGuid = selectedGuid,
                autoConnect = autoConnect,
                isTesting = isTesting,
                onBack = { openSubId = null },
                onAutoConnectChange = onAutoConnectChange,
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectServer(it)
                },
                onPingAll = onPingAll,
            )
        }
    }
}

@Composable
private fun SubsListLevel(
    subscriptions: List<Pair<String, SubscriptionItem>>,
    rows: List<ServerRowUiModel>,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.satra_nav_configs),
                style = MaterialTheme.typography.headlineMedium,
                color = onGlassPrimary(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.satra_sub_refresh))
            }
        }
        if (subscriptions.isEmpty()) {
            SatraGlassCard {
                Text(stringResource(R.string.satra_sub_empty), color = onGlassPrimary())
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(subscriptions, key = { it.first }) { (id, sub) ->
                    val count = rows.count { it.profile.subscriptionId == id }
                    SatraGlassCard(onClick = { onOpen(id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SatraUsageRing(
                                fraction = if (sub.showUsage) sub.usageFraction() else null,
                                warn = sub.isNearQuota(0.9f) || sub.isExpired(),
                                size = 56.dp,
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sub.profileTitle?.takeIf { it.isNotBlank() } ?: sub.remarks,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = onGlassPrimary(),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.satra_sub_servers_count, count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onGlassSecondary(),
                                )
                                if (sub.showUsage) {
                                    val used = sub.usedBytes()
                                    Text(
                                        if (sub.usageFraction() != null)
                                            "${used.toTrafficString()} / ${sub.total.toTrafficString()}"
                                        else
                                            "${used.toTrafficString()} · ${stringResource(R.string.satra_sub_unlimited)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = onGlassPrimary(),
                                    )
                                }
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_24dp),
                                contentDescription = stringResource(R.string.satra_sub_settings),
                                tint = SatraTokens.TealDeep,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { onOpenSettings(id) },
                            )
                            Spacer(Modifier.size(8.dp))
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back_24dp),
                                contentDescription = null,
                                tint = onGlassSecondary(),
                                modifier = Modifier.size(18.dp).scale(scaleX = -1f, scaleY = 1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodesListLevel(
    title: String,
    nodes: List<ServerRowUiModel>,
    selectedGuid: String?,
    autoConnect: Boolean,
    isTesting: Boolean,
    onBack: () -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onPingAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24dp),
                contentDescription = stringResource(R.string.satra_back),
                tint = SatraTokens.TealDeep,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onBack)
                    .padding(2.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = onGlassPrimary(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.satra_auto_connect),
                style = MaterialTheme.typography.titleSmall,
                color = onGlassPrimary(),
                modifier = Modifier.weight(1f),
            )
            SatraGlassToggle(
                checked = autoConnect,
                onCheckedChange = onAutoConnectChange,
                onLabel = stringResource(R.string.satra_settings_on),
                offLabel = stringResource(R.string.satra_settings_off),
            )
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = onPingAll) {
                Text(stringResource(R.string.satra_test_all_ping))
            }
        }
        if (isTesting) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = SatraTokens.Teal,
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(nodes, key = { it.guid }) { row ->
                val selected = !autoConnect && row.guid == selectedGuid
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(SatraTokens.RadiusMd))
                        .background(
                            if (selected) SatraTokens.Teal.copy(0.14f)
                            else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
                        )
                        .clickable(enabled = !autoConnect) { onSelect(row.guid) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.flag.ifBlank { "🌐" }, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.remarks,
                            style = MaterialTheme.typography.titleSmall,
                            color = onGlassPrimary(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            row.typeDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = onGlassSecondary(),
                        )
                    }
                    SatraPingBadge(row.testDelayMillis)
                    if (selected) {
                        Spacer(Modifier.size(8.dp))
                        Text("✓", color = SatraTokens.TealDeep, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
