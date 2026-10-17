package com.v2ray.ang.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.liquid.SatraGlassCard
import com.v2ray.ang.ui.compose.liquid.SatraGlassProvider
import com.v2ray.ang.ui.compose.liquid.SatraGlassToggle
import com.v2ray.ang.ui.compose.liquid.onGlassPrimary
import com.v2ray.ang.ui.compose.liquid.onGlassSecondary
import com.v2ray.ang.ui.compose.satra.SatraCard
import com.v2ray.ang.ui.compose.satra.SatraRowAction
import com.v2ray.ang.ui.compose.satra.SatraSectionTitle
import com.v2ray.ang.ui.compose.satra.SatraStatusChip
import com.v2ray.ang.ui.compose.satra.SatraTokens

@Composable
fun SettingsHubScreen(
    hubState: SettingsHubUiState,
    onOpen: (SettingsDestination) -> Unit,
    onPerAppToggle: (Boolean) -> Unit,
    onOpenPerAppManage: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenNotifications: () -> Unit,
    onApplyTunnelPresets: () -> Unit,
    onOpenLiquidCatalog: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val modeLabel = if (hubState.mode == AppConfig.VPN) {
        stringResource(R.string.satra_settings_mode_vpn)
    } else {
        stringResource(R.string.satra_settings_mode_proxy)
    }
    val coverageLabel = if (hubState.perAppProxyEnabled) {
        stringResource(R.string.satra_settings_coverage_selected)
    } else {
        stringResource(R.string.satra_settings_coverage_all)
    }
    val perAppSwitchDesc = stringResource(R.string.satra_settings_per_app_toggle_a11y)
    val perAppStateDesc = if (hubState.perAppProxyEnabled) {
        stringResource(R.string.satra_settings_on)
    } else {
        stringResource(R.string.satra_settings_off)
    }

    SatraGlassProvider(modifier = modifier) {
    Column(
        Modifier
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SatraGlassCard {
            Text(
                stringResource(R.string.satra_settings_hub_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onGlassPrimary(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.satra_settings_hub_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = onGlassSecondary(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SatraStatusChip(modeLabel)
                SatraStatusChip(stringResource(R.string.satra_settings_port_chip, hubState.socksPort))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                coverageLabel,
                style = MaterialTheme.typography.labelLarge,
                color = SatraTokens.TealDeep,
                fontWeight = FontWeight.Medium,
            )
        }

        SatraSectionTitle(stringResource(R.string.satra_settings_section_connection))
        SatraGlassCard {
            SatraRowAction(
                title = stringResource(R.string.title_mode_settings),
                subtitle = stringResource(R.string.satra_settings_cat_mode_sub),
                iconRes = R.drawable.ic_flash_on_24dp,
                onClick = { onOpen(SettingsDestination.Mode) },
            )
            SatraRowAction(
                title = stringResource(R.string.title_vpn_settings),
                subtitle = stringResource(R.string.satra_settings_cat_tunnel_sub),
                iconRes = R.drawable.ic_lock_24dp,
                onClick = { onOpen(SettingsDestination.Tunnel) },
            )
            SatraRowAction(
                title = stringResource(R.string.title_core_settings),
                subtitle = stringResource(R.string.satra_settings_cat_core_sub),
                iconRes = R.drawable.ic_routing_24dp,
                onClick = { onOpen(SettingsDestination.Core) },
            )
        }

        SatraSectionTitle(stringResource(R.string.satra_settings_section_apps))
        SatraGlassCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = perAppSwitchDesc
                        stateDescription = perAppStateDesc
                        role = Role.Switch
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.satra_settings_per_app_master),
                        style = MaterialTheme.typography.titleSmall,
                        color = onGlassPrimary(),
                    )
                    Text(
                        stringResource(R.string.satra_settings_per_app_master_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = onGlassSecondary(),
                    )
                }
                SatraGlassToggle(
                    checked = hubState.perAppProxyEnabled,
                    onCheckedChange = onPerAppToggle,
                    onLabel = stringResource(R.string.satra_settings_on),
                    offLabel = stringResource(R.string.satra_settings_off),
                )
            }
            SatraRowAction(
                title = stringResource(R.string.satra_settings_per_app_manage),
                subtitle = stringResource(R.string.satra_settings_per_app_manage_sub),
                iconRes = R.drawable.ic_per_apps_24dp,
                onClick = onOpenPerAppManage,
            )
        }

        SatraSectionTitle(stringResource(R.string.satra_settings_section_experience))
        SatraGlassCard {
            SatraRowAction(
                title = stringResource(R.string.title_ui_settings),
                subtitle = stringResource(R.string.satra_settings_cat_ui_sub),
                iconRes = R.drawable.ic_translate_24dp,
                onClick = { onOpen(SettingsDestination.Appearance) },
            )
            SatraRowAction(
                title = stringResource(R.string.satra_notif_control),
                subtitle = stringResource(R.string.satra_settings_cat_notif_sub),
                iconRes = R.drawable.ic_settings_24dp,
                onClick = onOpenNotifications,
            )
            SatraRowAction(
                title = stringResource(R.string.routing_settings_title),
                subtitle = stringResource(R.string.satra_settings_cat_routing_sub),
                iconRes = R.drawable.ic_outline_filter_alt_24,
                onClick = onOpenRouting,
            )
        }

        SatraSectionTitle(stringResource(R.string.satra_settings_section_performance))
        SatraGlassCard {
            SatraRowAction(
                title = stringResource(R.string.title_mux_settings),
                subtitle = stringResource(R.string.satra_settings_cat_mux_sub),
                iconRes = R.drawable.ic_qu_switch_24dp,
                onClick = { onOpen(SettingsDestination.Mux) },
            )
            SatraRowAction(
                title = stringResource(R.string.title_fragment_settings),
                subtitle = stringResource(R.string.satra_settings_cat_fragment_sub),
                iconRes = R.drawable.ic_scan_24dp,
                onClick = { onOpen(SettingsDestination.Fragment) },
            )
            SatraRowAction(
                title = stringResource(R.string.title_observatory_settings),
                subtitle = stringResource(R.string.satra_settings_cat_obs_sub),
                iconRes = R.drawable.ic_check_update_24dp,
                onClick = { onOpen(SettingsDestination.Observatory) },
            )
        }

        SatraSectionTitle(stringResource(R.string.satra_settings_section_system))
        SatraGlassCard {
            SatraRowAction(
                title = stringResource(R.string.title_advanced),
                subtitle = stringResource(R.string.satra_settings_cat_advanced_sub),
                iconRes = R.drawable.ic_more_vert_24dp,
                onClick = { onOpen(SettingsDestination.Advanced) },
            )
            SatraRowAction(
                title = stringResource(R.string.satra_settings_tunnel_reset),
                subtitle = stringResource(R.string.satra_settings_tunnel_reset_sub),
                iconRes = R.drawable.ic_restore_24dp,
                onClick = onApplyTunnelPresets,
            )
            SatraRowAction(
                title = stringResource(R.string.satra_settings_safety),
                subtitle = stringResource(R.string.satra_settings_safety_sub),
                iconRes = R.drawable.ic_privacy_24dp,
                onClick = { onOpen(SettingsDestination.Safety) },
            )
            if (BuildConfig.DEBUG && onOpenLiquidCatalog != null) {
                SatraRowAction(
                    title = stringResource(R.string.satra_liquid_catalog),
                    subtitle = "Debug",
                    iconRes = R.drawable.ic_stat_satra,
                    onClick = onOpenLiquidCatalog,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        NavigationBarsSpacer()
    }
    }
}

@Composable
fun SettingsSafetyPanel(modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SatraCard {
            Text(
                stringResource(R.string.satra_settings_safety_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.satra_settings_safety_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SatraCard {
            Text(
                stringResource(R.string.satra_settings_play_protect_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.satra_settings_play_protect_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NavigationBarsSpacer()
    }
}
