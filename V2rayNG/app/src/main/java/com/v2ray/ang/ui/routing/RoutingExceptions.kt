package com.v2ray.ang.ui.routing


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.RoutingPolicy
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SatraTextField
import com.v2ray.ang.ui.compose.satra.satraClickable

/** Kind of exception list; [key] is the settings key. */
internal enum class ExceptionKind(val key: String, val isApp: Boolean) {
    VpnDomain(RoutingPolicy.PREF_VPN_DOMAINS, false),
    VpnApp(RoutingPolicy.PREF_VPN_APPS, true),
    DirectDomain(RoutingPolicy.PREF_DIRECT_DOMAINS, false),
}

/** Validated value to store, or null. Pure; unit-tested. */
internal fun validateException(kind: ExceptionKind, input: String): String? =
    if (kind.isApp) input.trim().takeIf { RoutingPolicy.isValidPackage(it) } else RoutingPolicy.normalizeDomain(input)

/** Smart-routing exceptions: always through the VPN (sites, apps) and always direct (sites). */
@Composable
internal fun RoutingExceptionsSection() {
    val c = Satra.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = SatraSpace.Gutter).padding(top = 22.dp)) {
        Text(
            stringResource(R.string.sx_rx_title), style = Satra.type.headline, color = c.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            stringResource(R.string.sx_rx_note), style = Satra.type.footnote, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 2.dp, bottom = 10.dp),
        )
        ExceptionBox(ExceptionKind.VpnDomain, stringResource(R.string.sx_rx_vpn_sites), stringResource(R.string.sx_rx_vpn_sites_hint), c.accent)
        Spacer(Modifier.height(10.dp))
        ExceptionBox(ExceptionKind.VpnApp, stringResource(R.string.sx_rx_vpn_apps), stringResource(R.string.sx_rx_vpn_apps_hint), c.accent)
        Spacer(Modifier.height(10.dp))
        ExceptionBox(ExceptionKind.DirectDomain, stringResource(R.string.sx_rx_direct_sites), stringResource(R.string.sx_rx_direct_sites_hint), c.success)
    }
}


@Composable
private fun ExceptionBox(kind: ExceptionKind, title: String, hint: String, tone: androidx.compose.ui.graphics.Color) {
    val c = Satra.colors
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(RoutingPolicy.values(kind.key)) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(if (kind.isApp) R.string.sx_rx_bad_app else R.string.sx_rx_bad_domain)
    val notInstalled = stringResource(R.string.sx_rx_not_installed)
    val dup = stringResource(R.string.sx_rx_dup)
    fun add() {
        val v = validateException(kind, input)
        error = when {
            v == null -> invalid
            v in items -> dup
            kind.isApp && runCatching { ctx.packageManager.getApplicationInfo(v, 0) }.isFailure -> notInstalled
            else -> null
        }
        if (error == null && v != null) {
            RoutingPolicy.add(kind.key, v); items = items + v; input = ""
            SettingsChangeManager.makeRestartService()
        }
    }
    Column(Modifier.fillMaxWidth().clip(SatraShapes.Large).background(c.surface).padding(14.dp)) {
        Text(title, style = Satra.type.subhead, color = c.textPrimary)
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { v ->
                    Row(
                        Modifier.clip(SatraShapes.Pill).background(tone.copy(alpha = 0.14f))
                            .satraClickable {
                                RoutingPolicy.remove(kind.key, v); items = items - v
                                SettingsChangeManager.makeRestartService()
                            }
                            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("\u2066$v\u2069", style = Satra.type.footnote, color = c.textPrimary)
                        Spacer(Modifier.width(4.dp))
                        Icon(SatraIcons.Close, stringResource(R.string.sx_rx_remove, v), tint = c.textSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SatraTextField(
                value = input,
                onValueChange = { input = it; error = null },
                label = hint,
                modifier = Modifier.weight(1f),
                placeholder = if (kind.isApp) "org.telegram.messenger" else "example.ir",
                isError = error != null,
                supporting = error,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                ),
            )
            Spacer(Modifier.width(8.dp))
            SatraButton(stringResource(R.string.sx_add), onClick = { add() }, compact = true, enabled = input.isNotBlank())
        }
    }
}
