package com.v2ray.ang.ui.catalog

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.liquid.ConnectionAtmosphereState
import com.v2ray.ang.ui.compose.liquid.PowerCapsuleState
import com.v2ray.ang.ui.compose.liquid.SatraAtmosphere
import com.v2ray.ang.ui.compose.liquid.SatraGlassBottomBar
import com.v2ray.ang.ui.compose.liquid.SatraGlassCapsule
import com.v2ray.ang.ui.compose.liquid.SatraGlassCard
import com.v2ray.ang.ui.compose.liquid.SatraGlassProvider
import com.v2ray.ang.ui.compose.liquid.SatraGlassToggle
import com.v2ray.ang.ui.compose.liquid.rememberSatraGlassTier
import com.v2ray.ang.ui.compose.satra.SatraTokens

/**
 * Debug-only catalog for Liquid Glass chrome (phase 0 gate).
 * Launched from Settings advanced when [BuildConfig.DEBUG].
 */
class LiquidCatalogActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        if (!BuildConfig.DEBUG) {
            Text("Catalog is debug-only")
            return
        }
        LiquidCatalogScreen(onBack = { finish() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) finish()
    }
}

@Composable
private fun LiquidCatalogScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var atm by remember { mutableStateOf(ConnectionAtmosphereState.Idle) }
    var capsule by remember { mutableStateOf(PowerCapsuleState.Off) }
    var toggle by remember { mutableStateOf(true) }
    val tier = rememberSatraGlassTier()
    val nav = listOf(
        Triple(0, R.string.satra_nav_home, R.drawable.ic_stat_satra),
        Triple(1, R.string.satra_nav_configs, R.drawable.ic_subscriptions_24dp),
        Triple(2, R.string.satra_nav_logs, R.drawable.ic_logcat_24dp),
        Triple(3, R.string.satra_nav_settings, R.drawable.ic_settings_24dp),
    )
    SatraAtmosphere(state = atm) {
        SatraGlassProvider {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.satra_liquid_catalog),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("Tier: $tier", color = SatraTokens.TealDeep)
                RowChips(
                    labels = listOf("Idle", "Connecting", "Connected", "Failed"),
                    onPick = {
                        atm = ConnectionAtmosphereState.entries[it]
                        capsule = PowerCapsuleState.entries[it]
                    },
                )
                SatraGlassCapsule(
                    state = capsule,
                    subtitle = "Catalog",
                    onToggle = {
                        capsule = when (capsule) {
                            PowerCapsuleState.Off -> PowerCapsuleState.Connecting
                            PowerCapsuleState.Connecting -> PowerCapsuleState.On
                            PowerCapsuleState.On -> PowerCapsuleState.Failed
                            PowerCapsuleState.Failed -> PowerCapsuleState.Off
                        }
                    },
                )
                SatraGlassCard {
                    Text("Glass card sample", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    SatraGlassToggle(checked = toggle, onCheckedChange = { toggle = it })
                }
                SatraGlassBottomBar(
                    selected = tab,
                    items = nav,
                    onSelect = { tab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.TextButton(onClick = onBack) {
                    Text(stringResource(R.string.satra_back))
                }
            }
        }
    }
}

@Composable
private fun RowChips(labels: List<String>, onPick: (Int) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            androidx.compose.material3.TextButton(onClick = { onPick(index) }) {
                Text(label)
            }
        }
    }
}
