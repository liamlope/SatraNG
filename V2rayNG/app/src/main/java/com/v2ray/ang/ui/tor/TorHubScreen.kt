package com.v2ray.ang.ui.tor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.handler.TorBridgeMode
import com.v2ray.ang.handler.TorConnectionMode
import com.v2ray.ang.handler.TorManager
import com.v2ray.ang.ui.compose.satra.SatraCard
import com.v2ray.ang.ui.compose.satra.SatraPill
import com.v2ray.ang.ui.compose.satra.SatraSectionTitle
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.SatraUsageRing

private val ExitCountries = listOf(
    "" to R.string.satra_country_any,
    "DE" to 0, "NL" to 0, "US" to 0, "FR" to 0, "GB" to 0,
    "SE" to 0, "CH" to 0, "CA" to 0, "JP" to 0, "AT" to 0,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TorHubScreen(
    modifier: Modifier = Modifier,
    vpnConnected: Boolean,
    onModeApplied: () -> Unit,
) {
    val tor by TorManager.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val torOn = tor.enabled
    var exitQuery by remember { mutableStateOf("") }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            stringResource(R.string.satra_tor_hub_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.satra_tor_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        SatraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SatraUsageRing(
                    fraction = (tor.bootstrapPercent / 100f).coerceIn(0f, 1f),
                    size = 64.dp,
                    torProtected = torOn,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.satra_tor_bootstrap, tor.bootstrapPercent),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when (tor.mode) {
                            TorConnectionMode.WithVpn -> stringResource(R.string.satra_status_vpn_tor)
                            TorConnectionMode.TorOnly -> stringResource(R.string.satra_status_tor_only)
                            TorConnectionMode.Off -> stringResource(R.string.satra_tor_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (tor.circuitHint.isNotBlank()) {
                        Text(
                            tor.circuitHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = SatraTokens.accentDeep(torOn),
                        )
                    }
                }
            }
            if (tor.bootstrapPercent in 1..99) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { tor.bootstrapPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = SatraTokens.accent(torOn),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                TorManager.requestNewIdentity()
            }) {
                Text(stringResource(R.string.satra_tor_new_identity))
            }
        }

        Spacer(Modifier.height(16.dp))
        SatraSectionTitle(stringResource(R.string.satra_tor_connection_mode))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TorConnectionMode.Off to R.string.satra_tor_mode_off,
                TorConnectionMode.WithVpn to R.string.satra_tor_mode_with_vpn,
                TorConnectionMode.TorOnly to R.string.satra_tor_mode_only,
            ).forEach { (mode, label) ->
                SatraPill(
                    text = stringResource(label),
                    selected = tor.mode == mode,
                    torProtected = torOn,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (TorManager.setMode(mode, vpnConnected || mode == TorConnectionMode.TorOnly)) {
                            onModeApplied()
                        }
                    },
                )
            }
        }
        if (tor.mode == TorConnectionMode.WithVpn && !vpnConnected) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.satra_tor_needs_vpn),
                style = MaterialTheme.typography.labelMedium,
                color = SatraTokens.Amber,
            )
        }

        Spacer(Modifier.height(20.dp))
        SatraSectionTitle(stringResource(R.string.satra_tor_exit))
        BasicTextField(
            value = exitQuery,
            onValueChange = { exitQuery = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(SatraTokens.accent(torOn)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(14.dp),
            decorationBox = { inner ->
                if (exitQuery.isEmpty()) {
                    Text(
                        stringResource(R.string.satra_search_exit),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.height(10.dp))
        val anyLabel = stringResource(R.string.satra_country_any)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExitCountries
                .filter { (code, _) ->
                    exitQuery.isBlank() || code.contains(exitQuery, true) ||
                        (code.isEmpty() && anyLabel.contains(exitQuery, true))
                }
                .forEach { (code, _) ->
                    val label = if (code.isEmpty()) anyLabel
                    else "${GeoIpResolver.flagEmoji(code)} $code"
                    SatraPill(
                        text = label,
                        selected = tor.exitCountry.equals(code, true) ||
                            (code.isEmpty() && tor.exitCountry.isEmpty()),
                        torProtected = torOn,
                        onClick = {
                            TorManager.setExitCountry(code)
                            onModeApplied()
                        },
                    )
                }
        }
        TorToggleRow(
            title = stringResource(R.string.satra_tor_strict_nodes),
            checked = tor.strictNodes,
            torOn = torOn,
            onChecked = { TorManager.setStrictNodes(it); onModeApplied() },
        )

        Spacer(Modifier.height(20.dp))
        SatraSectionTitle(stringResource(R.string.satra_tor_bridges))
        Text(
            stringResource(R.string.satra_tor_bridge_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TorBridgeMode.entries.forEach { mode ->
                val label = when (mode) {
                    TorBridgeMode.Auto -> R.string.satra_tor_bridge_auto
                    TorBridgeMode.None -> R.string.satra_tor_bridge_none
                    TorBridgeMode.Obfs4 -> R.string.satra_tor_bridge_obfs4
                    TorBridgeMode.Snowflake -> R.string.satra_tor_bridge_snowflake
                    TorBridgeMode.Meek -> R.string.satra_tor_bridge_meek
                    TorBridgeMode.Custom -> R.string.satra_tor_bridge_custom
                }
                SatraPill(
                    text = stringResource(label),
                    selected = tor.bridgeMode == mode,
                    torProtected = torOn,
                    onClick = { TorManager.setBridgeMode(mode); onModeApplied() },
                )
            }
        }
        if (tor.bridgeMode == TorBridgeMode.Custom) {
            Spacer(Modifier.height(10.dp))
            var draft by remember(tor.customBridges) { mutableStateOf(tor.customBridges) }
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    TorManager.setCustomBridges(it)
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(SatraTokens.accent(torOn)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            stringResource(R.string.satra_tor_custom_bridges),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
        TextButton(onClick = {
            TorManager.rotateBridge()
            onModeApplied()
        }) {
            Text(stringResource(R.string.satra_tor_rotate_bridge))
        }

        Spacer(Modifier.height(16.dp))
        SatraSectionTitle(stringResource(R.string.satra_tor_advanced))
        TorToggleRow(
            title = stringResource(R.string.satra_tor_stream_isolation),
            checked = tor.streamIsolation,
            torOn = torOn,
            onChecked = TorManager::setStreamIsolation,
        )
        TorToggleRow(
            title = stringResource(R.string.satra_tor_kill_switch),
            checked = tor.killSwitch,
            torOn = torOn,
            onChecked = TorManager::setKillSwitch,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TorToggleRow(
    title: String,
    checked: Boolean,
    torOn: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = SatraTokens.accent(torOn),
            ),
        )
    }
}
