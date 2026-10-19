package com.v2ray.ang.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.app.AppNav
import com.v2ray.ang.ui.compose.satra.GroupScope
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLargeTitleScaffold
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraSearchField
import com.v2ray.ang.ui.compose.satra.SatraSegmented
import com.v2ray.ang.ui.compose.satra.SatraSheet
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SatraSwitchRow
import com.v2ray.ang.ui.compose.satra.SheetTitle
import com.v2ray.ang.ui.main.MainDestination
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.settings.SettingsDestination
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color

private class Spec(val title: String, val subtitle: String?, val keywords: String, val content: GroupScope.() -> Unit)
private class Section(val header: String?, val footer: String?, val specs: List<Spec>)

@Composable
fun MoreScreen(mainVm: MainViewModel, nav: AppNav, vm: MoreViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }
    MoreContent(s, nav, vm)
}

/** Stateless body of the Settings tab (also rendered by the screenshot tests). */
@Composable
fun MoreContent(s: MoreUiState, nav: AppNav, vm: MoreActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var languageSheet by remember { mutableStateOf(false) }
    val c = Satra.colors
    val ctx = LocalContext.current

    val languageNames = stringArrayResource(R.array.language_select)
    val languageCodes = stringArrayResource(R.array.language_select_value)
    val languageLabel = languageCodes.indexOf(s.language).takeIf { it >= 0 }?.let { idx ->
        if (languageCodes[idx] == "auto") stringResource(R.string.sx_language_system) else languageNames[idx]
    } ?: s.language

    val themeOptions = listOf(stringResource(R.string.sx_theme_system), stringResource(R.string.sx_theme_light), stringResource(R.string.sx_theme_dark))

    fun spec(title: String, subtitle: String? = null, keywords: String = "", content: GroupScope.() -> Unit) = Spec(title, subtitle, keywords, content)

    @Composable
    fun navRow(title: String, subtitle: String?, icon: ImageVector, color: Color, keywords: String = "", onClick: () -> Unit) =
        spec(title, subtitle, keywords) { row { SatraRow(title, subtitle = subtitle, icon = icon, iconColor = color, chevron = true, onClick = onClick) } }

    val sections = buildList {
        add(
            Section(
                stringResource(R.string.sx_set_connection), null,
                listOf(
                    spec(stringResource(R.string.sx_set_smart), stringResource(R.string.sx_set_smart_sub), "auto fastest reconnect best") {
                        row { SatraSwitchRow(stringResource(R.string.sx_set_smart), s.smartConnect, vm::setSmartConnect, subtitle = stringResource(R.string.sx_set_smart_sub), icon = SatraIcons.Bolt, iconColor = c.accent) }
                    },
                    spec(stringResource(R.string.sx_set_quick), stringResource(R.string.sx_set_quick_sub), "open start launch") {
                        row { SatraSwitchRow(stringResource(R.string.sx_set_quick), s.quickConnect, vm::setQuickConnect, subtitle = stringResource(R.string.sx_set_quick_sub), icon = SatraIcons.Play, iconColor = c.success) }
                    },
                    spec(stringResource(R.string.sx_set_sub_on_open), stringResource(R.string.sx_set_sub_on_open_sub), "subscription update refresh launch open") {
                        row { SatraSwitchRow(stringResource(R.string.sx_set_sub_on_open), s.subUpdateOnOpen, vm::setSubUpdateOnOpen, subtitle = stringResource(R.string.sx_set_sub_on_open_sub), icon = SatraIcons.Refresh, iconColor = c.info) }
                    },
                    spec(stringResource(R.string.sx_set_killswitch), stringResource(R.string.sx_set_killswitch_sub), "always-on lockdown block kill") {
                        row {
                            SatraRow(
                                stringResource(R.string.sx_set_killswitch), subtitle = stringResource(R.string.sx_set_killswitch_sub),
                                icon = SatraIcons.Lock, iconColor = c.danger, chevron = true,
                                onClick = { nav.openSystemVpnSettings() },
                            )
                        }
                    },
                    spec(stringResource(R.string.sx_set_mode), stringResource(R.string.sx_set_mode_sub), "vpn proxy mode") {
                        row {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.v2ray.ang.ui.compose.satra.IconTile(SatraIcons.Shield, c.info)
                                    Spacer(Modifier.size(12.dp))
                                    Column {
                                        Text(stringResource(R.string.sx_set_mode), style = Satra.type.body, color = c.textPrimary)
                                        Text(stringResource(if (s.vpnMode) R.string.sx_set_mode_vpn_sub else R.string.sx_set_mode_proxy_sub), style = Satra.type.footnote, color = c.textSecondary)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                SatraSegmented(
                                    listOf(stringResource(R.string.sx_set_mode_vpn), stringResource(R.string.sx_set_mode_proxy)),
                                    if (s.vpnMode) 0 else 1, { vm.setVpnMode(it == 0) },
                                )
                            }
                        }
                    },
                ),
            ),
        )
        add(
            Section(
                stringResource(R.string.sx_set_apps), null,
                listOf(
                    navRow(stringResource(R.string.sx_set_split), stringResource(R.string.sx_set_split_sub), SatraIcons.Apps, c.accent, "per-app split tunnel bypass") { nav.navigate(MainDestination.PerAppProxy) },
                    navRow(stringResource(R.string.sx_set_routing), stringResource(R.string.sx_set_routing_sub), SatraIcons.Route, c.info, "rules iran russia geo direct") { nav.navigate(MainDestination.Routing) },
                ),
            ),
        )
        add(
            Section(
                stringResource(R.string.sx_set_appearance), null,
                listOf(
                    spec(stringResource(R.string.sx_set_theme), null, "dark light theme night") {
                        row {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.v2ray.ang.ui.compose.satra.IconTile(SatraIcons.Palette, c.warning)
                                    Spacer(Modifier.size(12.dp))
                                    Text(stringResource(R.string.sx_set_theme), style = Satra.type.body, color = c.textPrimary)
                                }
                                Spacer(Modifier.height(10.dp))
                                SatraSegmented(themeOptions, s.theme.toIntOrNull()?.coerceIn(0, 2) ?: 0, { vm.setTheme(it.toString()) })
                            }
                        }
                    },
                    spec(stringResource(R.string.sx_set_language), languageLabel, "language persian farsi english") {
                        row { SatraRow(stringResource(R.string.sx_set_language), icon = SatraIcons.Language, iconColor = c.info, value = languageLabel, chevron = true, onClick = { languageSheet = true }) }
                    },
                    spec(stringResource(R.string.sx_set_speed_notif), stringResource(R.string.sx_set_speed_notif_sub), "notification speed") {
                        row { SatraSwitchRow(stringResource(R.string.sx_set_speed_notif), s.speedNotification, vm::setSpeedNotification, subtitle = stringResource(R.string.sx_set_speed_notif_sub), icon = SatraIcons.Bell, iconColor = c.danger) }
                    },
                ),
            ),
        )
        add(
            Section(
                stringResource(R.string.sx_set_tools), null,
                listOf(
                    navRow(stringResource(R.string.sx_set_log), stringResource(R.string.sx_set_log_sub), SatraIcons.Terminal, c.textSecondary, "log debug errors") { nav.navigate(MainDestination.Logcat) },
                    navRow(stringResource(R.string.sx_set_backup), stringResource(R.string.sx_set_backup_sub), SatraIcons.Download, c.info, "export import restore") { nav.navigate(MainDestination.BackupRestore) },
                    navRow(stringResource(R.string.sx_set_update), null, SatraIcons.Refresh, c.success, "version") { nav.navigate(MainDestination.CheckUpdate) },
                    navRow(stringResource(R.string.sx_set_about), "v${BuildConfig.VERSION_NAME}", SatraIcons.Info, c.accent, "about version") { nav.navigate(MainDestination.About) },
                ),
            ),
        )
        add(
            Section(
                stringResource(R.string.sx_set_advanced), stringResource(R.string.sx_set_advanced_footer),
                buildList {
                    add(
                        spec(stringResource(R.string.sx_set_show_advanced), null, "advanced expert") {
                            row { SatraSwitchRow(stringResource(R.string.sx_set_show_advanced), s.showAdvanced, vm::setShowAdvanced, icon = SatraIcons.Sliders, iconColor = c.textSecondary) }
                        },
                    )
                    if (s.showAdvanced || query.isNotBlank()) {
                        @Composable
                        fun adv(titleRes: Int, dest: SettingsDestination, icon: ImageVector, kw: String) = navRow(stringResource(titleRes), null, icon, c.textSecondary, kw) { nav.openSettingsPage(dest) }
                        add(adv(R.string.sx_adv_tunnel, SettingsDestination.Tunnel, SatraIcons.Shield, "dns mtu ipv6 hev tun"))
                        add(adv(R.string.sx_adv_core, SettingsDestination.Core, SatraIcons.Gear, "xray core dns sniffing log"))
                        add(adv(R.string.sx_adv_mux, SettingsDestination.Mux, SatraIcons.Layers, "mux concurrency"))
                        add(adv(R.string.sx_adv_fragment, SettingsDestination.Fragment, SatraIcons.Wifi, "fragment dpi"))
                        add(adv(R.string.sx_adv_observatory, SettingsDestination.Observatory, SatraIcons.Pulse, "observatory balancer"))
                        add(adv(R.string.sx_adv_more, SettingsDestination.Advanced, SatraIcons.Sliders, "ping test url concurrency"))
                        add(navRow(stringResource(R.string.sx_adv_assets), null, SatraIcons.File, c.textSecondary, "geoip geosite assets") { nav.navigate(MainDestination.UserAssets) })
                    }
                },
            ),
        )
    }

    val q = query.trim()
    val visible = sections.mapNotNull { sec ->
        val specs = if (q.isEmpty()) sec.specs else sec.specs.filter { (it.title + " " + (it.subtitle ?: "") + " " + it.keywords).contains(q, ignoreCase = true) }
        if (specs.isEmpty()) null else Section(sec.header, sec.footer, specs)
    }

    SatraLargeTitleScaffold(title = stringResource(R.string.sx_tab_more), bottomPadding = 32.dp) {
        item(key = "search") {
            Box(Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 8.dp)) {
                SatraSearchField(query, { query = it }, stringResource(R.string.sx_set_search), clearLabel = stringResource(R.string.sx_clear))
            }
        }
        if (visible.isEmpty()) {
            item(key = "none") {
                Text(stringResource(R.string.sx_set_nothing), style = Satra.type.callout, color = c.textSecondary, modifier = Modifier.padding(SatraSpace.Gutter))
            }
        }
        visible.forEachIndexed { index, sec ->
            item(key = "sec$index") {
                Spacer(Modifier.height(if (index == 0) 0.dp else 12.dp))
                InsetGroup(header = sec.header, footer = sec.footer) {
                    sec.specs.forEach { spec -> spec.content(this) }
                }
            }
        }
        item(key = "brand") {
            Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.app_name) + " v" + BuildConfig.VERSION_NAME, style = Satra.type.footnote, color = c.textTertiary)
            }
        }
    }

    if (languageSheet) {
        SatraSheet(onDismiss = { languageSheet = false }) {
            SheetTitle(stringResource(R.string.sx_set_language))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InsetGroup {
                    languageCodes.forEachIndexed { i, code ->
                        row {
                            SatraRow(
                                title = if (code == "auto") stringResource(R.string.sx_language_system) else languageNames[i],
                                trailing = { if (code == s.language) Icon(SatraIcons.Check, null, tint = c.accent, modifier = Modifier.size(22.dp)) },
                                onClick = { languageSheet = false; vm.setLanguage(code) },
                            )
                        }
                    }
                }
            }
        }
    }
}
