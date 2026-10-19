package com.v2ray.ang.ui.notification

import android.content.Intent
import com.v2ray.ang.ui.compose.satra.SatraScrollPage
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.NotificationPrefs
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.SatraColors
import com.v2ray.ang.ui.compose.satraBackgroundBrush

class NotificationControlActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        val prefs by NotificationPrefs.state.collectAsStateWithLifecycle()
        val isDark = LocalDarkTheme.current

        SatraScrollPage(
            title = stringResource(R.string.satra_notif_control),
            onBack = { finish() },
            modifier = Modifier
                .fillMaxSize()
                .background(satraBackgroundBrush(isDark)),
        ) {
            run {
                Text(
                    stringResource(R.string.satra_notif_control_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.satra_notif_preview),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                NotifPreviewCard(
                    title = stringResource(R.string.satra_notif_title_connected),
                    subtitle = buildString {
                        if (prefs.showLocation) append("🇩🇪 Berlin · ")
                        append("SatraNG")
                        if (prefs.showSpeed) append("\n↑ 1.2 MB/s  ↓ 3.4 MB/s")
                    },
                )
                Spacer(Modifier.height(20.dp))
                PrefSwitch(stringResource(R.string.satra_notif_show_speed), prefs.showSpeed) {
                    NotificationPrefs.update { it.copy(showSpeed = !it.showSpeed) }
                }
                PrefSwitch(stringResource(R.string.satra_notif_show_location), prefs.showLocation) {
                    NotificationPrefs.update { it.copy(showLocation = !it.showLocation) }
                }
                PrefSwitch(stringResource(R.string.satra_notif_action_stop), prefs.actionStop) {
                    NotificationPrefs.update { it.copy(actionStop = !it.actionStop) }
                }
                PrefSwitch(stringResource(R.string.satra_notif_action_restart), prefs.actionRestart) {
                    NotificationPrefs.update { it.copy(actionRestart = !it.actionRestart) }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, AppConfig.RAY_NG_CHANNEL_ID)
                        })
                    } else {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        })
                    }
                }) {
                    Text(stringResource(R.string.satra_notif_open_system))
                }
            }
        }
    }
}

@Composable
private fun NotifPreviewCard(title: String, subtitle: String) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.5f), shape)
            .padding(16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = SatraColors.TealDeep,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrefSwitch(title: String, checked: Boolean, onToggle: () -> Unit) {
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
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SatraColors.Teal,
            ),
        )
    }
}
