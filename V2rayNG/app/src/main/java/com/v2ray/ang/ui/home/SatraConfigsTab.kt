package com.v2ray.ang.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.ui.compose.liquid.SatraGlassCard
import com.v2ray.ang.ui.compose.liquid.SatraGlassToggle
import com.v2ray.ang.ui.compose.liquid.onGlassPrimary
import com.v2ray.ang.ui.compose.liquid.onGlassSecondary
import com.v2ray.ang.ui.compose.satra.SatraPingBadge
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.main.ServerRowUiModel

@Composable
fun SatraConfigsTab(
    modifier: Modifier,
    subscriptions: List<Pair<String, SubscriptionItem>>,
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    selectedGroupId: String,
    autoConnect: Boolean,
    isTesting: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    onSelectServer: (String) -> Unit,
    onPingAll: () -> Unit,
    onRefreshSubs: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val activeSub = subscriptions.firstOrNull { it.first == selectedGroupId }
        ?: subscriptions.firstOrNull()
    val nodes = if (activeSub != null) {
        rows.filter { it.profile.subscriptionId == activeSub.first }
    } else {
        rows
    }
    val title = activeSub?.second?.profileTitle?.takeIf { it.isNotBlank() }
        ?: activeSub?.second?.remarks
        ?: stringResource(R.string.satra_nav_configs)

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = onGlassPrimary(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefreshSubs) {
                Text(stringResource(R.string.satra_sub_refresh))
            }
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
        if (nodes.isEmpty()) {
            SatraGlassCard {
                Text(stringResource(R.string.satra_locations_empty), color = onGlassPrimary())
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(nodes, key = { it.guid }) { row ->
                    val selected = !autoConnect && row.guid == selectedGuid
                    SatraGlassCard(
                        onClick = if (autoConnect) null else {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectServer(row.guid)
                            }
                        },
                        connectedTint = selected,
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
