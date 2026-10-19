package com.v2ray.ang.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.DayUsage
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.ui.compose.satra.ConnectOrb
import com.v2ray.ang.ui.compose.satra.OrbPhase
import com.v2ray.ang.ui.compose.satra.PingButton
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraBarChart
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraButtonStyle
import com.v2ray.ang.ui.compose.satra.SatraCard
import com.v2ray.ang.ui.compose.satra.SatraEmptyState
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraMenu
import com.v2ray.ang.ui.compose.satra.SatraMenuItem
import com.v2ray.ang.ui.compose.satra.SatraProgressBar
import com.v2ray.ang.ui.compose.satra.SatraRing
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSparkline
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.formatBytes
import com.v2ray.ang.ui.compose.satra.formatClock
import com.v2ray.ang.ui.compose.satra.formatSpeed
import com.v2ray.ang.ui.compose.satra.pingGrade
import com.v2ray.ang.ui.compose.satra.satraClickable
import com.v2ray.ang.ui.main.ConnectPhase
import com.v2ray.ang.ui.main.LiveUiState
import com.v2ray.ang.ui.main.MainUiState
import com.v2ray.ang.ui.main.QuotaUi
import com.v2ray.ang.ui.main.ServerRowUiModel
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AddMethod { Scan, Clipboard, File, Subscription }

@Composable
fun HomeScreen(
    state: MainUiState,
    live: LiveUiState,
    week: List<DayUsage>,
    onToggle: () -> Unit,
    onPing: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenUsage: () -> Unit,
    onAdd: (AddMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Satra.colors
    val hasServers = state.groups.isNotEmpty() && state.selectedGuid != null
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
    ) {
        item(key = "top", contentType = "top") {
            HomeTopBar(onAdd = onAdd)
        }
        if (!hasServers) {
            item(key = "empty", contentType = "empty") {
                SatraEmptyState(
                    icon = SatraIcons.Shield,
                    title = stringResource(R.string.sx_home_empty_title),
                    message = stringResource(R.string.sx_home_empty_message),
                    actionText = stringResource(R.string.sx_add_subscription),
                    onAction = { onAdd(AddMethod.Subscription) },
                    secondaryText = stringResource(R.string.sx_add_scan),
                    onSecondary = { onAdd(AddMethod.Scan) },
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
            item(key = "empty-actions") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = SatraSpace.Gutter, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SatraButton(
                        stringResource(R.string.sx_add_clipboard), { onAdd(AddMethod.Clipboard) },
                        modifier = Modifier.weight(1f), style = SatraButtonStyle.Tinted, icon = SatraIcons.Clipboard, compact = true,
                    )
                    SatraButton(
                        stringResource(R.string.sx_add_file), { onAdd(AddMethod.File) },
                        modifier = Modifier.weight(1f), style = SatraButtonStyle.Tinted, icon = SatraIcons.File, compact = true,
                    )
                }
            }
            return@LazyColumn
        }
        item(key = "orb", contentType = "orb") {
            OrbSection(state = state, live = live, onToggle = onToggle)
        }
        item(key = "server", contentType = "server") {
            ServerCard(
                row = state.selectedRow,
                state = state,
                onPing = onPing,
                onOpen = onOpenServers,
                modifier = Modifier.padding(horizontal = SatraSpace.Gutter),
            )
        }
        item(key = "stats", contentType = "stats") {
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = live.running,
                transitionSpec = { (fadeIn(tween(260)) + slideInVertically { it / 8 }) togetherWith fadeOut(tween(160)) },
                label = "statsSwap",
                modifier = Modifier.padding(horizontal = SatraSpace.Gutter),
            ) { running ->
                if (running) LiveCard(live, onOpenUsage) else TodayCard(live, week, onOpenUsage)
            }
        }
        state.quota?.let { quota ->
            item(key = "quota", contentType = "quota") {
                Spacer(Modifier.height(14.dp))
                QuotaCard(quota, Modifier.padding(horizontal = SatraSpace.Gutter))
            }
        }
    }
}

@Composable
private fun HomeTopBar(onAdd: (AddMethod) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = SatraSpace.Gutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShapeSmall).background(Brush.linearGradient(listOf(Satra.colors.accent, com.v2ray.ang.ui.compose.satra.SatraBrand.OrangeLight))),
            contentAlignment = Alignment.Center,
        ) { Icon(SatraIcons.Shield, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.app_name),
            style = Satra.type.title2.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
            color = Satra.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Box {
            SatraIconButton(SatraIcons.Plus, stringResource(R.string.sx_add), { menu = true })
            SatraMenu(menu, { menu = false }) {
                SatraMenuItem(stringResource(R.string.sx_add_scan), { menu = false; onAdd(AddMethod.Scan) }, SatraIcons.Scan)
                SatraMenuItem(stringResource(R.string.sx_add_clipboard), { menu = false; onAdd(AddMethod.Clipboard) }, SatraIcons.Clipboard)
                SatraMenuItem(stringResource(R.string.sx_add_file), { menu = false; onAdd(AddMethod.File) }, SatraIcons.File)
                SatraMenuItem(stringResource(R.string.sx_add_subscription), { menu = false; onAdd(AddMethod.Subscription) }, SatraIcons.Link)
            }
        }
    }
}

private val RoundedCornerShapeSmall = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)

// ───────────────────────────── Orb ─────────────────────────────

@Composable
private fun OrbSection(state: MainUiState, live: LiveUiState, onToggle: () -> Unit) {
    val c = Satra.colors
    val phase = when (state.phase) {
        ConnectPhase.Idle -> OrbPhase.Idle
        ConnectPhase.Connecting -> OrbPhase.Connecting
        ConnectPhase.Connected -> OrbPhase.Connected
        ConnectPhase.Disconnecting -> OrbPhase.Disconnecting
        ConnectPhase.Failed -> OrbPhase.Failed
    }
    val title = stringResource(
        when (state.phase) {
            ConnectPhase.Idle -> R.string.sx_state_idle
            ConnectPhase.Connecting -> R.string.sx_state_connecting
            ConnectPhase.Connected -> R.string.sx_state_connected
            ConnectPhase.Disconnecting -> R.string.sx_state_disconnecting
            ConnectPhase.Failed -> R.string.sx_state_failed
        },
    )
    val hint = stringResource(
        when (state.phase) {
            ConnectPhase.Idle -> R.string.sx_hint_connect
            ConnectPhase.Connecting -> R.string.sx_hint_cancel
            ConnectPhase.Connected -> R.string.sx_hint_disconnect
            ConnectPhase.Disconnecting -> R.string.sx_hint_wait
            ConnectPhase.Failed -> R.string.sx_hint_retry
        },
    )
    val titleColor by animateColorAsState(
        when (state.phase) {
            ConnectPhase.Connected -> c.accent
            ConnectPhase.Failed -> c.danger
            else -> c.textPrimary
        },
        tween(350), label = "orbTitle",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ConnectOrb(
            phase = phase,
            onClick = onToggle,
            stateLabel = title,
            actionLabel = hint,
            modifier = Modifier.size(292.dp),
        )
        Text(
            title,
            style = Satra.type.title.copy(fontWeight = FontWeight.Bold),
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = Triple(state.phase, state.errorMessage, hint),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "orbSub",
        ) { (p, err, h) ->
            when {
                p == ConnectPhase.Connected && live.sessionStartMs > 0L -> SessionClock(live.sessionStartMs)
                p == ConnectPhase.Failed && !err.isNullOrBlank() -> Text(
                    err, style = Satra.type.callout, color = c.textSecondary, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                else -> Text(h, style = Satra.type.callout, color = c.textSecondary, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SessionClock(startMs: Long) {
    var elapsed by remember(startMs) { mutableLongStateOf(((System.currentTimeMillis() - startMs) / 1000L)) }
    LaunchedEffect(startMs) {
        while (true) {
            elapsed = (System.currentTimeMillis() - startMs) / 1000L
            delay(1000)
        }
    }
    Text(
        formatClock(elapsed),
        style = Satra.type.numberMedium,
        color = Satra.colors.textSecondary,
    )
}

// ───────────────────────────── Server card ─────────────────────────────

@Composable
private fun ServerCard(
    row: ServerRowUiModel?,
    state: MainUiState,
    onPing: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Satra.colors
    SatraCard(modifier = modifier, onClick = onOpen, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagBadge(row?.countryCode, row?.exitVerified == true, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row?.remarks?.takeIf { it.isNotBlank() } ?: stringResource(R.string.sx_home_choose_server),
                    style = Satra.type.headline, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val location = row?.locationLabel.orEmpty()
                val sub = buildString {
                    if (location.isNotBlank()) append(location)
                    row?.typeDescription?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                }
                if (sub.isNotBlank()) {
                    Text(sub, style = Satra.type.footnote, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(
                        stringResource(R.string.sx_location_unknown),
                        style = Satra.type.footnote, color = c.textTertiary, maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            PingButton(
                ms = row?.testDelayMillis?.takeIf { it != 0L },
                testing = state.isTesting,
                enabled = state.isRunning && !state.isTesting,
                onClick = onPing,
                idleLabel = stringResource(R.string.sx_ping_test),
                testingLabel = stringResource(R.string.sx_ping_testing),
                failedLabel = stringResource(R.string.sx_ping_failed),
                contentDescription = stringResource(R.string.sx_ping_cd),
            )
        }
        if (!state.isRunning) {
            Text(
                stringResource(R.string.sx_ping_needs_connection),
                style = Satra.type.caption, color = c.textTertiary,
                modifier = Modifier.padding(top = 8.dp, start = 58.dp),
            )
        }
    }
}

/** Circular flag. Unknown location shows a neutral globe; unverified (address-based) flags are slightly dimmed. */
@Composable
fun FlagBadge(countryCode: String?, verified: Boolean, size: androidx.compose.ui.unit.Dp = 40.dp, modifier: Modifier = Modifier) {
    val c = Satra.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.fill),
        contentAlignment = Alignment.Center,
    ) {
        if (countryCode.isNullOrBlank()) {
            Icon(SatraIcons.Globe, null, tint = c.textTertiary, modifier = Modifier.size(size * 0.5f))
        } else {
            Text(
                GeoIpResolver.flagEmoji(countryCode),
                fontSize = (size.value * 0.54f).sp,
                modifier = Modifier.semantics {
                    contentDescription = GeoIpResolver.countryNameForCode(countryCode).orEmpty()
                },
            )
        }
    }
}

// ───────────────────────────── Live / today / quota ─────────────────────────────

@Composable
private fun LiveCard(live: LiveUiState, onOpenUsage: () -> Unit) {
    val c = Satra.colors
    SatraCard(onClick = onOpenUsage) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpeedStat(SatraIcons.ArrowDown, stringResource(R.string.sx_live_download), formatSpeed(live.downBps), c.success, Modifier.weight(1f))
            SpeedStat(SatraIcons.ArrowUp, stringResource(R.string.sx_live_upload), formatSpeed(live.upBps), c.info, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        SatraSparkline(
            values = live.history.ifEmpty { listOf(0f, 0f) },
            modifier = Modifier.fillMaxWidth(),
            height = 64.dp,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sx_live_session), style = Satra.type.footnote, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text(formatBytes(live.sessionBytes), style = Satra.type.numberSmall, color = c.textPrimary)
        }
    }
}

@Composable
private fun SpeedStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = Satra.type.caption, color = Satra.colors.textSecondary)
            Text(value, style = Satra.type.numberMedium, color = Satra.colors.textPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun TodayCard(live: LiveUiState, week: List<DayUsage>, onOpenUsage: () -> Unit) {
    val c = Satra.colors
    SatraCard(onClick = onOpenUsage) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sx_home_today), style = Satra.type.footnote, color = c.textSecondary)
                Text(formatBytes(live.todayBytes), style = Satra.type.numberLarge, color = c.textPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sx_home_see_usage), style = Satra.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                Icon(SatraIcons.ChevronRight, null, tint = c.accent, modifier = Modifier.size(16.dp))
            }
        }
        if (week.isNotEmpty() && week.any { it.totalBytes > 0L }) {
            Spacer(Modifier.height(10.dp))
            SatraSparkline(
                values = week.map { it.totalBytes.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp,
            )
        }
    }
}

@Composable
private fun QuotaCard(quota: QuotaUi, modifier: Modifier = Modifier) {
    val c = Satra.colors
    val fraction = quota.fraction
    val ctx = LocalContext.current
    SatraCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fraction != null) {
                SatraRing(
                    fraction = fraction, size = 58.dp, stroke = 7.dp,
                    color = when {
                        fraction >= 0.9f -> c.danger
                        fraction >= 0.75f -> c.warning
                        else -> c.accent
                    },
                ) {
                    Text(
                        String.format(Locale.getDefault(), "%d%%", (fraction * 100).toInt()),
                        style = Satra.type.caption.copy(fontWeight = FontWeight.Bold), color = c.textPrimary,
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(quota.name.ifBlank { stringResource(R.string.sx_quota_title) }, style = Satra.type.headline, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (quota.totalBytes > 0L) {
                    Text(
                        stringResource(
                            R.string.sx_quota_left,
                            formatBytes((quota.totalBytes - quota.usedBytes).coerceAtLeast(0L)),
                            formatBytes(quota.totalBytes),
                        ),
                        style = Satra.type.footnote, color = c.textSecondary,
                    )
                } else {
                    Text(stringResource(R.string.sx_quota_unlimited), style = Satra.type.footnote, color = c.textSecondary)
                }
                if (quota.expireMs > 0L) {
                    val days = TimeUnit.MILLISECONDS.toDays(quota.expireMs - System.currentTimeMillis())
                    Text(
                        if (quota.expireMs <= System.currentTimeMillis()) stringResource(R.string.sx_quota_expired)
                        else if (days < 1) stringResource(R.string.sx_quota_expires_today)
                        else pluralStringResource(R.plurals.sx_quota_expires_days, days.toInt(), days.toInt()),
                        style = Satra.type.footnote,
                        color = if (days < 3) c.warning else c.textSecondary,
                    )
                }
            }
        }
    }
}
