package com.v2ray.ang.ui.security

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.SecurityCheck
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraScrollPage
import com.v2ray.ang.ui.compose.satra.SatraSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** Connection Security Check: exit IP, DNS/IPv6/WebRTC leaks, encryption and kill switch, with a score. */
class SecurityCheckActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        SecurityCheckScreen(
            onBack = { finish() },
            onFix = { fix -> openFix(fix) },
        )
    }

    private fun openFix(fix: SecurityCheck.Fix) {
        try {
            when (fix) {
                SecurityCheck.Fix.EnableKillSwitch -> startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                else -> finish() // back to the app: connect / choose a server there
            }
        } catch (_: Exception) {
        }
    }
}

/** The core is up when its local HTTP inbound answers (works for VPN and Proxy-only modes). */
private fun coreListening(): Boolean = try {
    Socket().use { it.connect(InetSocketAddress("127.0.0.1", SettingsManager.getHttpPort()), 400); true }
} catch (_: Exception) {
    false
}

@Composable
private fun SecurityCheckScreen(onBack: () -> Unit, onFix: (SecurityCheck.Fix) -> Unit) {
    val c = Satra.colors
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<SecurityCheck.Report?>(null) }
    var running by remember { mutableStateOf(false) }
    var runId by remember { mutableIntStateOf(0) }
    LaunchedEffect(runId) {
        running = true
        report = withContext(Dispatchers.IO) { SecurityCheck.run(connected = coreListening()) }
        running = false
    }
    SatraScrollPage(title = stringResource(R.string.sx_sec_title), onBack = onBack, subtitle = stringResource(R.string.sx_sec_sub)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreRing(report?.score, running, report?.connected != false)
            Spacer(Modifier.height(10.dp))
            val r = report
            Text(
                when {
                    running -> stringResource(R.string.sx_sec_running)
                    r == null || !r.connected -> stringResource(R.string.sx_sec_connect_first)
                    r.score >= 85 -> stringResource(R.string.sx_sec_great)
                    r.score >= 60 -> stringResource(R.string.sx_sec_ok)
                    else -> stringResource(R.string.sx_sec_weak)
                },
                style = Satra.type.title2, color = c.textPrimary, textAlign = TextAlign.Center,
            )
        }
        val r = report
        if (r != null && !running) {
            InsetGroup {
                r.findings.forEach { f -> row { FindingRow(f) } }
            }
            if (r.fixes.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                InsetGroup(header = stringResource(R.string.sx_sec_fixes)) {
                    r.fixes.forEach { fix ->
                        row {
                            SatraRow(
                                fixTitle(fix), icon = SatraIcons.Warning, iconColor = c.warning,
                                chevron = fix == SecurityCheck.Fix.EnableKillSwitch,
                                onClick = if (fix == SecurityCheck.Fix.EnableKillSwitch || fix == SecurityCheck.Fix.Connect) ({ onFix(fix) }) else null,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            SatraButton(
                stringResource(R.string.sx_sec_run_again), onClick = { runId++ },
                modifier = Modifier.fillMaxWidth(), loading = running, enabled = !running,
            )
        }
        Text(
            stringResource(R.string.sx_sec_footer), style = Satra.type.footnote, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun ScoreRing(score: Int?, running: Boolean, connected: Boolean) {
    val c = Satra.colors
    val anim = remember { Animatable(0f) }
    LaunchedEffect(score, running) {
        if (!running && score != null) anim.animateTo(score / 100f, tween(900)) else anim.snapTo(0f)
    }
    val color = when {
        score == null || !connected -> c.textTertiary
        score >= 85 -> c.success
        score >= 60 -> c.warning
        else -> c.danger
    }
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            val w = 12.dp.toPx()
            drawArc(color.copy(alpha = 0.16f), 0f, 360f, false, style = Stroke(w))
            drawArc(color, -90f, 360f * anim.value, false, style = Stroke(w, cap = StrokeCap.Round))
        }
        if (running) SatraSpinner(size = 28.dp, color = c.accent, strokeWidth = 3.dp)
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(if (connected && score != null) "${(anim.value * 100).toInt()}" else "—", style = Satra.type.largeTitle.copy(fontWeight = FontWeight.Bold), color = color)
            Text(stringResource(R.string.sx_sec_score), style = Satra.type.footnote, color = c.textSecondary)
        }
    }
}

@Composable
private fun FindingRow(f: SecurityCheck.Finding) {
    val c = Satra.colors
    val (tint, label) = when (f.status) {
        SecurityCheck.Status.Pass -> c.success to stringResource(R.string.sx_sec_pass)
        SecurityCheck.Status.Warn -> c.warning to stringResource(R.string.sx_sec_warn)
        SecurityCheck.Status.Fail -> c.danger to stringResource(R.string.sx_sec_fail)
        SecurityCheck.Status.Unknown -> c.textTertiary to stringResource(R.string.sx_sec_unknown)
    }
    val (title, icon) = when (f.item) {
        SecurityCheck.Item.ExitIp -> stringResource(R.string.sx_sec_exit) to SatraIcons.Globe
        SecurityCheck.Item.DnsLeak -> stringResource(R.string.sx_sec_dns) to SatraIcons.Shield
        SecurityCheck.Item.Ipv6 -> stringResource(R.string.sx_sec_ipv6) to SatraIcons.Globe
        SecurityCheck.Item.WebRtc -> stringResource(R.string.sx_sec_webrtc) to SatraIcons.Shield
        SecurityCheck.Item.Encryption -> stringResource(R.string.sx_sec_tls) to SatraIcons.Lock
        SecurityCheck.Item.KillSwitch -> stringResource(R.string.sx_sec_killswitch) to SatraIcons.Lock
    }
    SatraRow(
        title, subtitle = f.detail.ifBlank { null }, icon = icon, iconColor = tint,
        trailing = {
            Icon(
                if (f.status == SecurityCheck.Status.Pass) SatraIcons.Check else SatraIcons.Warning,
                label, tint = tint, modifier = Modifier.size(20.dp),
            )
        },
    )
}

@Composable
private fun fixTitle(fix: SecurityCheck.Fix): String = stringResource(
    when (fix) {
        SecurityCheck.Fix.Connect -> R.string.sx_sec_fix_connect
        SecurityCheck.Fix.ChooseOtherServer -> R.string.sx_sec_fix_other
        SecurityCheck.Fix.UseSecureConfig -> R.string.sx_sec_fix_secure
        SecurityCheck.Fix.DisableInsecure -> R.string.sx_sec_fix_insecure
        SecurityCheck.Fix.EnableKillSwitch -> R.string.sx_sec_fix_killswitch
        SecurityCheck.Fix.UseRemoteDns -> R.string.sx_sec_fix_dns
        SecurityCheck.Fix.EnableIpv6Tunnel -> R.string.sx_sec_fix_ipv6
    },
)
