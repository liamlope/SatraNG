package com.v2ray.ang.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.ui.compose.liquid.SatraGlassToggle
import com.v2ray.ang.ui.compose.liquid.onGlassPrimary
import com.v2ray.ang.ui.compose.liquid.onGlassSecondary
import com.v2ray.ang.ui.compose.satra.SatraPingBadge
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.main.ServerRowUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatraConfigsTab(
    modifier: Modifier,
    subscriptions: List<Pair<String, SubscriptionItem>>,
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    @Suppress("UNUSED_PARAMETER") selectedGroupId: String,
    autoConnect: Boolean,
    isTesting: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    onSelectServer: (String) -> Unit,
    onPingAll: () -> Unit,
    onRefreshSubs: () -> Unit,
    onAddConfig: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var fabOpen by remember { mutableStateOf(false) }
    val grouped = remember(subscriptions, rows) {
        val bySub = rows.groupBy { it.profile.subscriptionId }
        val ordered = mutableListOf<Pair<Pair<String, SubscriptionItem?>, List<ServerRowUiModel>>>()
        val seen = mutableSetOf<String>()
        for ((id, item) in subscriptions) {
            val nodes = bySub[id].orEmpty()
            if (nodes.isNotEmpty()) {
                ordered += (id to item) to nodes
                seen += id
            }
        }
        for ((id, nodes) in bySub) {
            if (id !in seen && nodes.isNotEmpty()) {
                ordered += (id to null) to nodes
            }
        }
        ordered
    }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.satra_nav_configs),
                style = MaterialTheme.typography.headlineMedium,
                color = onGlassPrimary(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.satra_auto_connect),
                    style = MaterialTheme.typography.titleSmall,
                    color = onGlassPrimary(),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                SatraGlassToggle(
                    checked = autoConnect,
                    onCheckedChange = onAutoConnectChange,
                    onLabel = stringResource(R.string.satra_settings_on),
                    offLabel = stringResource(R.string.satra_settings_off),
                )
            }
            if (isTesting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = SatraTokens.Amber,
                )
            }

            PullToRefreshBox(
                isRefreshing = isTesting,
                onRefresh = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPingAll()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (grouped.isEmpty()) {
                    Box(Modifier.fillMaxSize()) {
                        SimpleConfigCard {
                            Text(stringResource(R.string.satra_locations_empty), color = onGlassPrimary())
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        grouped.forEach { (subPair, nodes) ->
                            val (subId, subItem) = subPair
                            stickyHeader(key = "hdr_$subId") {
                                val headerTitle = subItem?.remarks?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.satra_configs_ungrouped)
                                Text(
                                    text = headerTitle.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = onGlassSecondary(),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                )
                            }
                            items(nodes, key = { it.guid }) { row ->
                                val selected = !autoConnect && row.guid == selectedGuid
                                SimpleConfigCard(
                                    onClick = if (autoConnect) null else {
                                        {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectServer(row.guid)
                                        }
                                    },
                                    selected = selected,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            row.flag.ifBlank { "🌐" },
                                            style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.padding(end = 12.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                row.remarks,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = onGlassPrimary(),
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val loc = listOfNotNull(
                                                row.countryName?.takeIf { it.isNotBlank() },
                                                row.typeDescription,
                                            ).joinToString(" · ")
                                            Text(
                                                loc,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = onGlassSecondary(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
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
                }
            }
        }

        // Dim scrim when the action fan is open
        AnimatedVisibility(
            visible = fabOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { fabOpen = false },
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(
                visible = fabOpen,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ConfigFabAction(
                        icon = R.drawable.ic_cloud_download_24dp,
                        label = stringResource(R.string.satra_sub_refresh),
                        onClick = {
                            fabOpen = false
                            onRefreshSubs()
                        },
                    )
                    ConfigFabAction(
                        icon = R.drawable.ic_flash_on_24dp,
                        label = stringResource(R.string.satra_configs_test),
                        busy = isTesting,
                        onClick = {
                            fabOpen = false
                            onPingAll()
                        },
                    )
                    ConfigFabAction(
                        icon = R.drawable.ic_add_24dp,
                        label = stringResource(R.string.satra_configs_add),
                        onClick = {
                            fabOpen = false
                            onAddConfig()
                        },
                    )
                }
            }
            val rot by animateFloatAsState(if (fabOpen) 45f else 0f, label = "fabRot")
            val fabA11y = stringResource(R.string.satra_configs_actions_fab)
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(SatraTokens.Amber)
                    .semantics { contentDescription = fabA11y }
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        fabOpen = !fabOpen
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp).rotate(rot),
                )
            }
        }
    }
}

@Composable
private fun ConfigFabAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    busy: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            color = onGlassPrimary(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SatraTokens.Amber.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = SatraTokens.Amber,
                )
            } else {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = SatraTokens.Amber,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SimpleConfigCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (selected) {
        SatraTokens.TealDeep.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.05f)
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}
