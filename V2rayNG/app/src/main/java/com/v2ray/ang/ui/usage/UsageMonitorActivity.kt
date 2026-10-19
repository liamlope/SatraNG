package com.v2ray.ang.ui.usage

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding

class UsageMonitorActivity : BaseComponentActivity() {

    private val viewModel: UsageMonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refresh()
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.refresh() }
        UsageMonitorScreen(
            state = state,
            onBack = { finish() },
            onRequestUsageAccess = {
                startActivity(viewModel.usageAccessIntent())
            },
            onRefresh = { viewModel.refresh() },
            onSetVolumeMb = { pkg, mb ->
                viewModel.setVolumeLimit(pkg, mb?.let { it * 1024L * 1024L })
            },
            onSetTimeMin = { pkg, min ->
                viewModel.setTimeLimit(pkg, min?.let { it * 60L * 1000L })
            },
            onClearLimit = { viewModel.clearLimit(it) },
        )
    }
}

@Composable
private fun UsageMonitorScreen(
    state: UsageMonitorUiState,
    onBack: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRefresh: () -> Unit,
    onSetVolumeMb: (String, Long?) -> Unit,
    onSetTimeMin: (String, Long?) -> Unit,
    onClearLimit: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.satra_usage_title),
                onBackClick = onBack,
            )
        },
    ) { padding ->
        val bottomPad = NavigationBarsBottomPadding()
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottomPad),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.satra_usage_daily),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.satra_sub_refresh))
                }
            }
            state.daily.forEach { (day, bytes) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(day, style = MaterialTheme.typography.bodyMedium)
                    Text(bytes.toTrafficString(), fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                stringResource(R.string.satra_usage_peak_hours),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (state.peakHours.all { it.bytes == 0L }) {
                Text(
                    stringResource(R.string.satra_usage_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.peakHours.filter { it.bytes > 0L }.forEach { bucket ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.satra_usage_hour_label, bucket.hour),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(bucket.bytes.toTrafficString(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Text(
                stringResource(R.string.satra_usage_top_apps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!state.hasUsagePermission) {
                Text(
                    stringResource(R.string.satra_usage_permission_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRequestUsageAccess) {
                    Text(stringResource(R.string.satra_usage_grant_permission))
                }
            } else if (state.topApps.isEmpty()) {
                Text(
                    stringResource(R.string.satra_usage_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.topApps.forEach { app ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(app.bytes.toTrafficString())
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onSetVolumeMb(app.packageName, 100L) },
                            ) {
                                Text(stringResource(R.string.satra_limit_set_100mb))
                            }
                            OutlinedButton(
                                onClick = { onSetTimeMin(app.packageName, 30L) },
                            ) {
                                Text(stringResource(R.string.satra_limit_set_30min))
                            }
                            TextButton(onClick = { onClearLimit(app.packageName) }) {
                                Text(stringResource(R.string.satra_limit_action_clear))
                            }
                        }
                        val lim = state.limits[app.packageName]
                        if (lim != null && (lim.volumeLimitBytes > 0L || lim.timeLimitMs > 0L)) {
                            Text(
                                stringResource(
                                    R.string.satra_limit_status,
                                    if (lim.volumeLimitBytes > 0L) lim.volumeLimitBytes.toTrafficString() else "—",
                                    if (lim.timeLimitMs > 0L) (lim.timeLimitMs / 60_000L).toString() else "—",
                                    lim.usedBytes.toTrafficString(),
                                    (lim.usedMs / 60_000L).toString(),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
