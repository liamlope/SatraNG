package com.v2ray.ang.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.satra.SatraBottomNavBar
import com.v2ray.ang.ui.compose.satra.SatraCard
import com.v2ray.ang.ui.compose.satra.SatraOrb
import com.v2ray.ang.ui.compose.satra.SatraPingBadge
import com.v2ray.ang.ui.compose.satra.SatraRowAction
import com.v2ray.ang.ui.compose.satra.SatraShimmer
import com.v2ray.ang.ui.compose.satra.SatraStatusChip
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.SatraUsageRing
import com.v2ray.ang.ui.compose.satra.toOrbState
import com.v2ray.ang.ui.compose.satraBackgroundBrush
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainDestination
import com.v2ray.ang.ui.main.MainDialogs
import com.v2ray.ang.ui.main.MainMoreMenuAction
import com.v2ray.ang.ui.main.MainStatus
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.main.ServerDeleteTarget
import com.v2ray.ang.ui.main.ServerRowUiModel
import com.v2ray.ang.ui.main.ShareMethodDialog
import com.v2ray.ang.ui.main.buildServerRowUiModel
import com.v2ray.ang.ui.notification.NotificationControlActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

private fun loadServersForGroup(groupId: String): List<ServersCache> {
    return MmkvManager.decodeServerList(groupId).mapNotNull { guid ->
        val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        ServersCache(guid, profile, aff?.testDelayMillis ?: 0L)
    }
}

@Composable
fun SatraHomeScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = mainViewModel.formatStatus(uiState.status)
    val selectedGuid = uiState.selectedGuid
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap
    val isDark = LocalDarkTheme.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var locSort by rememberSaveable { mutableIntStateOf(0) }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }
    var shareTarget by remember {
        mutableStateOf<Triple<String, com.v2ray.ang.dto.entities.ProfileItem, Boolean>?>(null)
    }

    val allRows = remember { mutableStateOf<List<ServerRowUiModel>>(emptyList()) }
    val activeSub = remember { mutableStateOf<Pair<String, SubscriptionItem>?>(null) }

    LaunchedEffect(uiState.groups, uiState.selectedGroupId, searchQuery, uiState.isTesting) {
        withContext(Dispatchers.IO) {
            val rows = mutableListOf<ServerRowUiModel>()
            uiState.groups.forEach { group ->
                loadServersForGroup(group.id).forEach { server ->
                    if (MmkvManager.decodeServerAffiliationInfo(server.guid)?.countryCode.isNullOrBlank()) {
                        GeoIpResolver.resolveForProfile(server.guid, server.profile)
                    }
                    val model = buildServerRowUiModel(server, group.remarks)
                    if (searchQuery.isBlank() ||
                        model.remarks.contains(searchQuery, true) ||
                        model.locationLabel.contains(searchQuery, true) ||
                        model.countryCode.orEmpty().contains(searchQuery, true)
                    ) {
                        rows += model
                    }
                }
            }
            allRows.value = rows
            val subId = uiState.selectedGroupId
            val sub = MmkvManager.decodeSubscription(subId)
            activeSub.value = if (sub != null) subId to sub else null
        }
    }

    val selectedRow = allRows.value.firstOrNull { it.guid == selectedGuid }
        ?: allRows.value.firstOrNull()

    val removeServer: (String, String) -> Unit = { guid, name ->
        if (confirmRemove) showRemoveConfirm = ServerDeleteTarget(guid, name)
        else onAction(MainAction.RemoveServer(guid))
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )
    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    val navItems = listOf(
        Triple(0, R.string.satra_nav_home, R.drawable.ic_stat_satra),
        Triple(1, R.string.satra_nav_locations, R.drawable.ic_routing_24dp),
        Triple(2, R.string.satra_nav_subs, R.drawable.ic_subscriptions_24dp),
        Triple(3, R.string.satra_nav_more, R.drawable.ic_more_vert_24dp),
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(satraBackgroundBrush(isDark))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                SatraBottomNavBar(
                    selected = tab,
                    items = navItems,
                    onSelect = { tab = it },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        ) { padding ->
            when (tab) {
                0 -> CinematicHomeTab(
                    modifier = Modifier.padding(padding),
                    isRunning = isRunning,
                    status = uiState.status,
                    statusText = displayText,
                    selectedRow = selectedRow,
                    subscription = activeSub.value?.second,
                    serverCount = activeSub.value?.let { (id, _) ->
                        allRows.value.count { it.profile.subscriptionId == id }
                    } ?: allRows.value.size,
                    onConnect = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAction(MainAction.ToggleService)
                    },
                    onOpenLocations = { tab = 1 },
                    onOpenSubs = { tab = 2 },
                    onRefreshSub = { onAction(MainAction.UpdateSubscriptions) },
                    onTestCurrent = { onAction(MainAction.TestCurrentServer) },
                )

                1 -> LocationsPickerTab(
                    modifier = Modifier.padding(padding),
                    rows = allRows.value,
                    selectedGuid = selectedGuid,
                    searchQuery = searchQuery,
                    sortMode = locSort,
                    isLoading = isLoading || uiState.isTesting,
                    onSearch = { searchQuery = it },
                    onSort = { locSort = it },
                    onSelect = { guid ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAction(MainAction.SelectServer(guid))
                        tab = 0
                    },
                    onTestAll = { onAction(MainAction.TestRealAllServers) },
                )

                2 -> SubscriptionsHubTab(
                    modifier = Modifier.padding(padding),
                    onOpenEditor = { onNavigate(MainDestination.Subscriptions) },
                    onRefreshAll = { onAction(MainAction.UpdateSubscriptions) },
                    isLoading = isLoading,
                )

                else -> MoreTab(
                    modifier = Modifier.padding(padding),
                    onNavigate = onNavigate,
                    onOpenNotif = {
                        context.startActivity(Intent(context, NotificationControlActivity::class.java))
                    },
                    onMoreAction = { action ->
                        when (action) {
                            MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                            MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                            MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
                            MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                            MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
                            MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
                            MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                            MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
                            MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                            MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                        }
                    },
                    onImportQr = { onAction(MainAction.ImportQRcode) },
                    onImportClipboard = { onAction(MainAction.ImportClipboard) },
                )
            }
        }
    }
}

@Composable
private fun CinematicHomeTab(
    modifier: Modifier,
    isRunning: Boolean,
    status: MainStatus,
    statusText: String,
    selectedRow: ServerRowUiModel?,
    subscription: SubscriptionItem?,
    serverCount: Int,
    onConnect: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenSubs: () -> Unit,
    onRefreshSub: () -> Unit,
    onTestCurrent: () -> Unit,
) {
    val statusLabel = when {
        isRunning -> stringResource(R.string.satra_status_connected)
        status == MainStatus.Testing -> stringResource(R.string.satra_status_connecting)
        else -> stringResource(R.string.satra_status_ready)
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_satra_mark),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "SatraNG",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.satra_brand_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRunning) {
                SatraStatusChip(stringResource(R.string.satra_status_connected))
            }
        }
        Spacer(Modifier.height(24.dp))
        SatraOrb(
            state = status.toOrbState(isRunning),
            statusLabel = statusLabel,
            onClick = onConnect,
        )
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        SatraCard(onClick = onOpenLocations) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedRow?.flag ?: "🌐",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.satra_choose_location),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        selectedRow?.let {
                            listOfNotNull(it.locationLabel.takeIf { l -> l.isNotBlank() }, it.remarks)
                                .joinToString(" · ").ifBlank { it.remarks }
                        } ?: stringResource(R.string.satra_best_location),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    selectedRow?.typeDescription?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    SatraPingBadge(selectedRow?.testDelayMillis ?: 0L)
                    TextButton(onClick = onTestCurrent) {
                        Text(stringResource(R.string.satra_ping))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HomeSubSummary(
            subscription = subscription,
            serverCount = serverCount,
            onOpen = onOpenSubs,
            onRefresh = onRefreshSub,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HomeSubSummary(
    subscription: SubscriptionItem?,
    serverCount: Int,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
    SatraCard(onClick = onOpen) {
        if (subscription == null) {
            Text(
                stringResource(R.string.satra_sub_empty),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.satra_manage_sub),
                color = SatraTokens.TealDeep,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@SatraCard
        }
        val fraction = subscription.usageFraction()
        Row(verticalAlignment = Alignment.CenterVertically) {
            SatraUsageRing(
                fraction = fraction,
                warn = subscription.isNearQuota(0.9f) || subscription.isExpired(),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    subscription.profileTitle?.takeIf { it.isNotBlank() } ?: subscription.remarks,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.satra_sub_servers_count, serverCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val used = subscription.usedBytes()
                Text(
                    if (fraction != null) "${used.toTrafficString()} / ${subscription.total.toTrafficString()}"
                    else "${used.toTrafficString()} · ${stringResource(R.string.satra_sub_unlimited)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ExpireLine(subscription)
            }
            Text(
                stringResource(R.string.satra_sub_refresh),
                color = SatraTokens.TealDeep,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRefresh)
                    .padding(8.dp),
            )
        }
        when {
            subscription.isExpired() -> AlertLine(stringResource(R.string.satra_sub_expired), SatraTokens.Danger)
            subscription.isNearQuota(0.9f) -> AlertLine(stringResource(R.string.satra_sub_low_quota), SatraTokens.Danger)
            subscription.isNearExpire() -> AlertLine(stringResource(R.string.satra_sub_near_expire), SatraTokens.Amber)
        }
        if (!subscription.announce.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                subscription.announce.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExpireLine(subscription: SubscriptionItem) {
    if (subscription.expire <= 0L) {
        Text(
            stringResource(R.string.satra_sub_no_expiry),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val ms = if (subscription.expire > 10_000_000_000L) subscription.expire else subscription.expire * 1000L
    val left = ms - System.currentTimeMillis()
    val text = if (left <= 0) stringResource(R.string.satra_sub_expired)
    else {
        val days = TimeUnit.MILLISECONDS.toDays(left)
        val hours = TimeUnit.MILLISECONDS.toHours(left) % 24
        "${stringResource(R.string.satra_sub_expires)}: ${days}d ${hours}h"
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AlertLine(text: String, color: Color) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun LocationsPickerTab(
    modifier: Modifier,
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    searchQuery: String,
    sortMode: Int,
    isLoading: Boolean,
    onSearch: (String) -> Unit,
    onSort: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onTestAll: () -> Unit,
) {
    val sorted = remember(rows, sortMode) {
        when (sortMode) {
            1 -> rows.sortedBy { it.remarks.lowercase() }
            2 -> rows.sortedBy { it.countryCode.orEmpty() + it.countryName.orEmpty() }
            else -> rows.sortedWith(
                compareBy { if (it.testDelayMillis <= 0) Long.MAX_VALUE else it.testDelayMillis }
            )
        }
    }
    val grouped = remember(sorted) {
        sorted.groupBy { it.countryCode?.takeIf { c -> c.isNotBlank() } ?: "??" }
    }
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.satra_nav_locations),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearch,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(SatraTokens.Teal),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(14.dp),
            decorationBox = { inner ->
                if (searchQuery.isEmpty()) {
                    Text(stringResource(R.string.satra_search_locations), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            },
        )
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onSort(0) }) { Text(stringResource(R.string.satra_sort_by_ping)) }
            TextButton(onClick = { onSort(1) }) { Text(stringResource(R.string.satra_sort_by_name)) }
            TextButton(onClick = { onSort(2) }) { Text(stringResource(R.string.satra_sort_by_country)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onTestAll) { Text(stringResource(R.string.satra_test_all_ping)) }
        }
        if (isLoading) {
            SatraShimmer(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(48.dp))
            Spacer(Modifier.height(8.dp))
        }
        if (sorted.isEmpty()) {
            Text(
                stringResource(R.string.satra_locations_empty),
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                grouped.forEach { (cc, list) ->
                    item(key = "hdr_$cc") {
                        Text(
                            "${GeoIpResolver.flagEmoji(cc)}  ${list.firstOrNull()?.countryName ?: cc}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    items(list, key = { it.guid }) { row ->
                        LocationRowRich(
                            row = row,
                            selected = row.guid == selectedGuid,
                            onClick = { onSelect(row.guid) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationRowRich(row: ServerRowUiModel, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) SatraTokens.Teal.copy(0.14f) else MaterialTheme.colorScheme.surfaceContainerLow
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.flag, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.remarks, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.typeDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SatraPingBadge(row.testDelayMillis)
    }
}

@Composable
private fun SubscriptionsHubTab(
    modifier: Modifier,
    onOpenEditor: () -> Unit,
    onRefreshAll: () -> Unit,
    isLoading: Boolean,
) {
    val context = LocalContext.current
    val subs = remember { mutableStateOf(MmkvManager.decodeSubscriptions()) }
    LaunchedEffect(isLoading) {
        if (!isLoading) subs.value = MmkvManager.decodeSubscriptions()
    }
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.satra_nav_subs),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefreshAll) { Text(stringResource(R.string.satra_sub_refresh)) }
            TextButton(onClick = onOpenEditor) { Text(stringResource(R.string.satra_sub_add)) }
        }
        Text(
            stringResource(R.string.satra_sub_hub_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (subs.value.isEmpty()) {
            SatraCard(onClick = onOpenEditor) {
                Text(stringResource(R.string.satra_sub_empty), color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(subs.value, key = { it.guid }) { item ->
                    val sub = item.subscription
                    val fraction = sub.usageFraction()
                    Spacer(Modifier.height(10.dp))
                    SatraCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SatraUsageRing(
                                fraction = fraction,
                                warn = sub.isNearQuota(0.9f) || sub.isExpired(),
                                size = 84.dp,
                            )
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sub.profileTitle?.takeIf { it.isNotBlank() } ?: sub.remarks,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val used = sub.usedBytes()
                                Text(
                                    if (fraction != null)
                                        "${used.toTrafficString()} / ${sub.total.toTrafficString()}"
                                    else
                                        "${used.toTrafficString()} · ${stringResource(R.string.satra_sub_unlimited)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                ExpireLine(sub)
                                if (sub.profileUpdateInterval > 0) {
                                    Text(
                                        stringResource(R.string.satra_sub_interval, sub.profileUpdateInterval.toInt()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (sub.lastUpdated > 0) {
                                    Text(
                                        stringResource(
                                            R.string.satra_sub_updated,
                                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                                .format(Date(sub.lastUpdated)),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        when {
                            sub.isExpired() -> AlertLine(stringResource(R.string.satra_sub_expired), SatraTokens.Danger)
                            sub.isNearQuota(0.9f) -> AlertLine(stringResource(R.string.satra_sub_low_quota), SatraTokens.Danger)
                            sub.isNearExpire() -> AlertLine(stringResource(R.string.satra_sub_near_expire), SatraTokens.Amber)
                        }
                        if (!sub.announce.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            SatraSectionBanner(
                                title = stringResource(R.string.satra_sub_announce),
                                body = sub.announce.orEmpty(),
                            )
                        }
                        Row {
                            sub.supportUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                }) { Text(stringResource(R.string.satra_sub_support)) }
                            }
                            sub.profileWebPageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                }) { Text(stringResource(R.string.satra_sub_profile_page)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SatraSectionBanner(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SatraTokens.Teal.copy(alpha = 0.10f))
            .padding(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = SatraTokens.TealDeep, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MoreTab(
    modifier: Modifier,
    onNavigate: (MainDestination) -> Unit,
    onOpenNotif: () -> Unit,
    onMoreAction: (MainMoreMenuAction) -> Unit,
    onImportQr: () -> Unit,
    onImportClipboard: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            stringResource(R.string.satra_nav_more),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        SatraRowAction(
            title = stringResource(R.string.satra_notif_control),
            subtitle = stringResource(R.string.satra_notif_control_desc),
            iconRes = R.drawable.ic_settings_24dp,
            onClick = onOpenNotif,
        )
        listOf(
            MainDestination.Settings,
            MainDestination.Routing,
            MainDestination.PerAppProxy,
            MainDestination.Subscriptions,
            MainDestination.UserAssets,
            MainDestination.BackupRestore,
            MainDestination.Logcat,
            MainDestination.About,
        ).forEach { dest ->
            SatraRowAction(
                title = stringResource(dest.labelRes),
                iconRes = dest.iconRes,
                onClick = { onNavigate(dest) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.satra_more_advanced), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onImportQr) { Text(stringResource(R.string.menu_item_import_config_qrcode)) }
        TextButton(onClick = onImportClipboard) { Text(stringResource(R.string.menu_item_import_config_clipboard)) }
        TextButton(onClick = { onMoreAction(MainMoreMenuAction.UpdateSubscriptions) }) {
            Text(stringResource(R.string.satra_sub_refresh))
        }
        TextButton(onClick = { onMoreAction(MainMoreMenuAction.RestartService) }) {
            Text(stringResource(R.string.title_service_restart))
        }
        Spacer(Modifier.height(24.dp))
    }
}
