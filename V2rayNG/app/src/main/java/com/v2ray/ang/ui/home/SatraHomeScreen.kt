package com.v2ray.ang.ui.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.TorManager
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.SatraColors
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val torState by TorManager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }
    var shareTarget by remember {
        mutableStateOf<Triple<String, com.v2ray.ang.dto.entities.ProfileItem, Boolean>?>(null)
    }

    val allRows = remember(uiState.groups, searchQuery) {
        mutableStateOf<List<ServerRowUiModel>>(emptyList())
    }
    val activeSub = remember(uiState.selectedGroupId, uiState.groups) {
        mutableStateOf<Pair<String, SubscriptionItem>?>(null)
    }

    LaunchedEffect(uiState.groups, uiState.selectedGroupId, searchQuery, uiState.isTesting) {
        withContext(Dispatchers.IO) {
            val rows = mutableListOf<ServerRowUiModel>()
            uiState.groups.forEach { group ->
                val servers = loadServersForGroup(group.id)
                servers.forEach { server ->
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

    LaunchedEffect(isRunning) {
        TorManager.onVpnStateChanged(isRunning)
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

    Box(
        Modifier
            .fillMaxSize()
            .background(satraBackgroundBrush(isDark))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = if (isDark) SatraColors.InkSoft.copy(alpha = 0.96f)
                    else Color.White.copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavItem(0, tab, R.string.satra_nav_home, R.drawable.ic_stat_name) { tab = 0 }
                    NavItem(1, tab, R.string.satra_nav_locations, R.drawable.ic_routing_24dp) { tab = 1 }
                    NavItem(2, tab, R.string.satra_nav_subs, R.drawable.ic_subscriptions_24dp) { tab = 2 }
                    NavItem(3, tab, R.string.satra_nav_more, R.drawable.ic_more_vert_24dp) { tab = 3 }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> HomeTab(
                    modifier = Modifier.padding(padding),
                    isRunning = isRunning,
                    statusText = displayText,
                    status = uiState.status,
                    selectedRow = selectedRow,
                    subscription = activeSub.value?.second,
                    serverCount = allRows.value.count {
                        it.profile.subscriptionId == (activeSub.value?.first ?: it.profile.subscriptionId)
                    }.let { count ->
                        if (activeSub.value == null) allRows.value.size else
                            allRows.value.count { it.profile.subscriptionId == activeSub.value!!.first }
                    },
                    torEnabled = torState.enabled,
                    torExit = torState.exitCountry,
                    isLoading = isLoading,
                    onConnect = { onAction(MainAction.ToggleService) },
                    onOpenLocations = { tab = 1 },
                    onOpenSubs = { tab = 2 },
                    onRefreshSub = { onAction(MainAction.UpdateSubscriptions) },
                    onTestCurrent = { onAction(MainAction.TestCurrentServer) },
                    onTorToggle = { enabled ->
                        if (TorManager.setEnabled(enabled, isRunning)) {
                            if (enabled) TorManager.tryStartOrbot(context)
                            if (isRunning) onAction(MainAction.RestartService)
                        }
                    },
                    onTorExit = {
                        TorManager.setExitCountry(it)
                        if (isRunning && torState.enabled) onAction(MainAction.RestartService)
                    },
                )

                1 -> LocationsTab(
                    modifier = Modifier.padding(padding),
                    rows = allRows.value,
                    selectedGuid = selectedGuid,
                    searchQuery = searchQuery,
                    onSearch = { searchQuery = it },
                    onSelect = { guid ->
                        onAction(MainAction.SelectServer(guid))
                        tab = 0
                    },
                    onTestAll = { onAction(MainAction.TestRealAllServers) },
                    isLoading = isLoading,
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
private fun NavItem(
    index: Int,
    selected: Int,
    labelRes: Int,
    iconRes: Int,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected == index,
        onClick = onClick,
        icon = {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        },
        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = SatraColors.TealDeep,
            selectedTextColor = SatraColors.TealDeep,
            indicatorColor = SatraColors.Teal.copy(alpha = 0.18f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    )
}

@Composable
private fun HomeTab(
    modifier: Modifier,
    isRunning: Boolean,
    statusText: String,
    status: MainStatus,
    selectedRow: ServerRowUiModel?,
    subscription: SubscriptionItem?,
    serverCount: Int,
    torEnabled: Boolean,
    torExit: String,
    isLoading: Boolean,
    onConnect: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenSubs: () -> Unit,
    onRefreshSub: () -> Unit,
    onTestCurrent: () -> Unit,
    onTorToggle: (Boolean) -> Unit,
    onTorExit: (String) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.ic_satra_mark),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SatraColors.InkSoft.copy(alpha = 0.12f))
                    .padding(4.dp)
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.satra_brand_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val statusLabel = when {
                status is MainStatus.Testing || isLoading -> stringResource(R.string.satra_status_connecting)
                isRunning -> stringResource(R.string.satra_status_connected)
                else -> stringResource(R.string.satra_status_ready)
            }
            Text(
                statusLabel,
                style = MaterialTheme.typography.titleMedium,
                color = if (isRunning) SatraColors.TealDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onTestCurrent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(20.dp))
            BigConnectOrb(isRunning = isRunning, onClick = onConnect)
        }

        Spacer(Modifier.height(28.dp))
        LocationSelectCard(row = selectedRow, onClick = onOpenLocations)

        Spacer(Modifier.height(14.dp))
        SubscriptionUsageCard(
            subscription = subscription,
            serverCount = serverCount,
            onRefresh = onRefreshSub,
            onOpen = onOpenSubs
        )

        Spacer(Modifier.height(14.dp))
        TorCard(
            enabled = torEnabled,
            exitCountry = torExit,
            vpnConnected = isRunning,
            onToggle = onTorToggle,
            onExitChange = onTorExit,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TorCard(
    enabled: Boolean,
    exitCountry: String,
    vpnConnected: Boolean,
    onToggle: (Boolean) -> Unit,
    onExitChange: (String) -> Unit,
) {
    val exits = listOf("" to "Any", "DE" to "Germany", "NL" to "Netherlands", "US" to "United States",
        "FR" to "France", "GB" to "United Kingdom", "SE" to "Sweden", "CH" to "Switzerland")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.satra_tor_mode),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.satra_tor_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SatraColors.Teal
                )
            )
        }
        if (enabled && !vpnConnected) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.satra_tor_needs_vpn),
                style = MaterialTheme.typography.labelMedium,
                color = SatraColors.Amber
            )
        }
        if (enabled) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.satra_tor_exit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                exits.take(4).forEach { (code, name) ->
                    val selected = exitCountry.equals(code, true) || (code.isEmpty() && exitCountry.isEmpty())
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) SatraColors.Teal.copy(0.22f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .clickable { onExitChange(code) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (code.isEmpty()) name else "${GeoIpResolver.flagEmoji(code)} $code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationsTab(
    modifier: Modifier,
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onTestAll: () -> Unit,
    isLoading: Boolean,
) {
    val grouped = remember(rows) {
        rows.groupBy { it.countryCode?.uppercase() ?: "ZZ" }
            .toList()
            .sortedBy { (code, _) -> if (code == "ZZ") "ZZZ" else GeoIpResolver.countryNameForCode(code) ?: code }
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.satra_nav_locations),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.satra_test_all_ping),
                color = SatraColors.TealDeep,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onTestAll)
                    .padding(8.dp)
            )
        }

        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (searchQuery.isEmpty()) {
                Text(
                    stringResource(R.string.satra_search_locations),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearch,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(SatraColors.Teal),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isLoading) {
            androidx.compose.material3.LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                color = SatraColors.Teal
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            grouped.forEach { (code, list) ->
                item(key = "header-$code") {
                    Text(
                        text = if (code == "ZZ") stringResource(R.string.satra_unknown_location)
                        else "${GeoIpResolver.flagEmoji(code)} ${GeoIpResolver.countryNameForCode(code) ?: code}",
                        style = MaterialTheme.typography.labelLarge,
                        color = SatraColors.TealDeep,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                    )
                }
                items(list, key = { it.guid }) { row ->
                    LocationRow(
                        row = row,
                        selected = row.guid == selectedGuid,
                        onClick = { onSelect(row.guid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsHubTab(
    modifier: Modifier,
    onOpenEditor: () -> Unit,
    onRefreshAll: () -> Unit,
    isLoading: Boolean,
) {
    val subs = remember { mutableStateOf(MmkvManager.decodeSubscriptions()) }
    LaunchedEffect(isLoading) {
        if (!isLoading) subs.value = MmkvManager.decodeSubscriptions()
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.satra_nav_subs),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.satra_sub_refresh),
                color = SatraColors.TealDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRefreshAll)
                    .padding(8.dp)
            )
            Text(
                stringResource(R.string.satra_sub_add),
                color = SatraColors.TealDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenEditor)
                    .padding(8.dp)
            )
        }

        if (subs.value.isEmpty()) {
            Text(
                stringResource(R.string.satra_sub_empty),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(24.dp)
            )
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(subs.value, key = { it.guid }) { cache ->
                val count = loadServersForGroup(cache.guid).size
                SubscriptionUsageCard(
                    subscription = cache.subscription,
                    serverCount = count,
                    onRefresh = onRefreshAll,
                    onOpen = onOpenEditor,
                )
                cache.subscription.supportUrl?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreTab(
    modifier: Modifier,
    onNavigate: (MainDestination) -> Unit,
    onMoreAction: (MainMoreMenuAction) -> Unit,
    onImportQr: () -> Unit,
    onImportClipboard: () -> Unit,
) {
    val items = listOf(
        MainDestination.Settings,
        MainDestination.PerAppProxy,
        MainDestination.Routing,
        MainDestination.UserAssets,
        MainDestination.BackupRestore,
        MainDestination.Logcat,
        MainDestination.CheckUpdate,
        MainDestination.About,
    )
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.satra_more_advanced),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        MoreLink(stringResource(R.string.menu_item_import_config_qrcode), onImportQr)
        MoreLink(stringResource(R.string.menu_item_import_config_clipboard), onImportClipboard)
        MoreLink(stringResource(R.string.title_sub_setting)) { onNavigate(MainDestination.Subscriptions) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        items.forEach { dest ->
            MoreLink(stringResource(dest.labelRes)) { onNavigate(dest) }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        MoreLink(stringResource(R.string.title_service_restart)) {
            onMoreAction(MainMoreMenuAction.RestartService)
        }
        MoreLink(stringResource(R.string.title_del_all_config)) {
            onMoreAction(MainMoreMenuAction.DeleteAll)
        }
    }
}

@Composable
private fun MoreLink(title: String, onClick: () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp)
    )
}
