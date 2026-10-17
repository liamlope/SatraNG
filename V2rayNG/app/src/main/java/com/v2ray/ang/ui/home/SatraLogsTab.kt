package com.v2ray.ang.ui.home

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.liquid.SatraGlassConfirmSheet
import com.v2ray.ang.ui.compose.liquid.onGlassPrimary
import com.v2ray.ang.ui.compose.liquid.onGlassSecondary
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion
import com.v2ray.ang.ui.logcat.LogcatViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

enum class SatraLogLevel { Normal, Debug, Trace }

@Composable
fun SatraLogsTab(
    modifier: Modifier = Modifier,
    logcatViewModel: LogcatViewModel = viewModel(),
) {
    var level by remember { mutableIntStateOf(0) }
    val logs by logcatViewModel.filteredLogs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var stickToBottom by remember { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { logcatViewModel.loadLogcat() }
    LaunchedEffect(level) {
        // Client-side filter; Trace keeps full buffer then redacts on render.
        val filter = when (SatraLogLevel.entries[level]) {
            SatraLogLevel.Normal ->
                "start|stop|fail|import|subscription|success|error|connected|disconnect|vpn|service"
            SatraLogLevel.Debug ->
                "Ang|Satra|routing|CoreService|Launcher|MMKV|WorkManager|GeoIp|Settings"
            SatraLogLevel.Trace -> null // full GoLog / Xray / app — scrubbed in UI
        }
        logcatViewModel.filter(filter)
        logcatViewModel.loadLogcat()
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 2
        }.distinctUntilChanged().collect { stickToBottom = it }
    }
    LaunchedEffect(logs.size, stickToBottom) {
        if (stickToBottom && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex.coerceAtLeast(0))
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.satra_nav_logs),
            style = MaterialTheme.typography.headlineMedium,
            color = onGlassPrimary(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LogLevelSegmented(
            selected = level,
            onSelect = { level = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Row {
            TextButton(onClick = { logcatViewModel.loadLogcat() }) {
                Text(stringResource(R.string.satra_sub_refresh))
            }
            TextButton(onClick = { logcatViewModel.copyLogcat() }) {
                Text(stringResource(R.string.satra_log_copy))
            }
            TextButton(onClick = {
                val text = logs.joinToString("\n") { redactSecrets(it) }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.satra_log_share)))
            }) {
                Text(stringResource(R.string.satra_log_share))
            }
            TextButton(onClick = { confirmClear = true }) {
                Text(stringResource(R.string.satra_log_clear))
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(SatraTokens.RadiusMd))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f))
                .padding(8.dp),
        ) {
            items(logs, key = { it.hashCode().toString() + it.take(32) }) { line ->
                Text(
                    redactSecrets(line),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = onGlassSecondary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                )
            }
        }
    }

    SatraGlassConfirmSheet(
        visible = confirmClear,
        title = stringResource(R.string.satra_log_clear),
        body = stringResource(R.string.satra_log_clear_confirm),
        confirmLabel = stringResource(R.string.satra_log_clear),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = { logcatViewModel.clearLogcat() },
        onDismiss = { confirmClear = false },
    )
}

@Composable
private fun LogLevelSegmented(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = rememberReduceMotion()
    val labels = listOf(
        R.string.satra_log_normal,
        R.string.satra_log_debug,
        R.string.satra_log_trace,
    )
    BoxWithConstraints(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .padding(3.dp),
    ) {
        val slot = maxWidth / 3
        val offsetAnim by animateFloatAsState(
            targetValue = selected.toFloat(),
            animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.liquidPill,
            label = "logPill",
        )
        Box(
            Modifier
                .offset(x = slot * offsetAnim)
                .width(slot)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(SatraTokens.Teal.copy(alpha = 0.28f)),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, res ->
                val tint by animateColorAsState(
                    if (selected == index) SatraTokens.TealDeep else onGlassSecondary(),
                    label = "logTint$index",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(res),
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Strip proxy URLs and credentials from Trace output. */
internal fun redactSecrets(line: String): String {
    var out = line
    out = Regex("""(?i)(password|passwd|pwd|token|secret|uuid)\s*[=:]\s*\S+""")
        .replace(out) { m -> "${m.groupValues[1]}=***" }
    out = Regex("""(?i)(vless|vmess|trojan|ss|socks|http)://\S+""")
        .replace(out, "$1://***")
    return out
}
