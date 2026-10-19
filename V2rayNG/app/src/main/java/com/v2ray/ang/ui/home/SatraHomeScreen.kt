package com.v2ray.ang.ui.home

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.HybridUsageTracker
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager as AngNotificationManager
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.usage.UsageMonitorActivity
import com.v2ray.ang.ui.compose.liquid.AtmosphereStateMachine
import com.v2ray.ang.ui.compose.liquid.PowerCapsuleState
import com.v2ray.ang.ui.compose.liquid.SatraAtmosphere
import com.v2ray.ang.ui.compose.liquid.SatraGlassBottomBar
import com.v2ray.ang.ui.compose.liquid.SatraGlassCapsule
import com.v2ray.ang.ui.compose.liquid.SatraGlassCard
import com.v2ray.ang.ui.compose.liquid.SatraGlassIconButton
import com.v2ray.ang.ui.compose.liquid.SatraGlassProvider
import com.v2ray.ang.ui.compose.liquid.SatraGlassSurface
import com.v2ray.ang.ui.compose.liquid.onGlassPrimary
import com.v2ray.ang.ui.compose.liquid.onGlassSecondary
import com.v2ray.ang.ui.compose.liquid.powerCapsuleState
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.SatraPingBadge
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion
import com.v2ray.ang.ui.logcat.LogcatActivity
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainDestination
import com.v2ray.ang.ui.main.MainDialogs
import com.v2ray.ang.ui.main.MainStatus
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.main.ServerDeleteTarget
import com.v2ray.ang.ui.main.ShareMethodDialog
import com.v2ray.ang.ui.main.ServerRowUiModel
import com.v2ray.ang.ui.main.buildServerRowUiModel
import com.v2ray.ang.ui.settings.SettingsActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun loadServersForGroup(groupId: String): List<ServersCache> {
    return MmkvManager.decodeServerList(groupId).mapNotNull { guid ->
        val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        ServersCache(guid, profile, aff?.testDelayMillis ?: 0L)
    }
}

/** Always re-decode from MMKV so quota cards pick up fresh userinfo after pull-refresh. */
private fun decodedSubscriptionsFresh(): List<Pair<String, SubscriptionItem>> =
    MmkvManager.decodeSubscriptions().map { it.guid to it.subscription }

@Composable
fun SatraHomeScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val selectedGuid = uiState.selectedGuid
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = tab, pageCount = { 4 })
    var ceremony by remember { mutableStateOf(false) }

    // Keep bottom nav pill in sync when the user swipes the middle content.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (tab != page) tab = page
            }
    }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(tab)
        }
    }
    var connectFailed by remember { mutableStateOf(false) }
    var failSince by remember { mutableLongStateOf(0L) }
    var autoConnect by remember {
        mutableStateOf(mainViewModel.isAutoConnectEnabled())
    }
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
    val subs = remember { mutableStateOf<List<Pair<String, SubscriptionItem>>>(emptyList()) }
    val activeSub = remember { mutableStateOf<Pair<String, SubscriptionItem>?>(null) }
    var subsEpoch by remember { mutableIntStateOf(0) }

    // After subscription update finishes, reload quota from MMKV.
    LaunchedEffect(isLoading) {
        if (!isLoading) subsEpoch++
    }

    LaunchedEffect(uiState.groups, uiState.selectedGroupId, uiState.isTesting, uiState.isRunning, subsEpoch) {
        withContext(Dispatchers.IO) {
            val rows = mutableListOf<ServerRowUiModel>()
            uiState.groups.forEach { group ->
                loadServersForGroup(group.id).forEach { server ->
                    rows += buildServerRowUiModel(server, group.remarks)
                }
            }
            allRows.value = rows
            val decoded = decodedSubscriptionsFresh()
            subs.value = decoded
            val selectedServerGuid = uiState.selectedGuid
            val serverSubId = selectedServerGuid
                ?.let { MmkvManager.decodeServerConfig(it)?.subscriptionId }
                ?.takeIf { it.isNotBlank() }
            val fromServer = serverSubId?.let { id ->
                decoded.firstOrNull { it.first == id }
                    ?: MmkvManager.decodeSubscription(id)?.let { id to it }
            }
            val preferred = decoded.firstOrNull { (_, s) ->
                s.enabled && (s.showUsage || s.showExpire) && s.url.isNotBlank()
            } ?: decoded.firstOrNull { it.second.enabled }
            val groupSub = uiState.selectedGroupId.takeIf { it.isNotBlank() }?.let { id ->
                decoded.firstOrNull { it.first == id }
                    ?: MmkvManager.decodeSubscription(id)?.let { id to it }
            }
            activeSub.value = listOfNotNull(fromServer, groupSub, preferred)
                .firstOrNull { it.second.enabled }
                ?: preferred
                ?: decoded.firstOrNull()
        }
    }

    LaunchedEffect(isRunning) {
        if (isRunning) connectFailed = false
    }

    LaunchedEffect(connectFailed) {
        if (connectFailed) {
            failSince = System.currentTimeMillis()
            delay(2200)
            connectFailed = false
        }
    }

    val connecting = (ceremony || uiState.autoReconnecting) && !isRunning
    val atmState = AtmosphereStateMachine.afterFailHold(
        current = AtmosphereStateMachine.fromConnection(isRunning, connecting, connectFailed),
        elapsedMs = if (connectFailed) System.currentTimeMillis() - failSince else 0L,
    )
    val capsule = powerCapsuleState(isRunning, connecting, connectFailed)
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

    // Home · Configs · Subs · Settings — icons match preview shell
    val navItems = listOf(
        Triple(0, R.string.satra_nav_home, R.drawable.ic_home_outline_24),
        Triple(1, R.string.satra_nav_configs, R.drawable.ic_flash_on_24dp),
        Triple(2, R.string.satra_nav_subs, R.drawable.ic_subscriptions_24dp),
        Triple(3, R.string.satra_nav_settings, R.drawable.ic_settings_24dp),
    )

    SatraAtmosphere(state = atmState) {
        SatraGlassProvider {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                        if (tab == 0) {
                            HomeConnectDock(
                                hasAnyConfig = allRows.value.isNotEmpty(),
                                importing = isLoading,
                                isTesting = uiState.isTesting,
                                pingMs = selectedRow?.testDelayMillis?.takeIf { it != 0L },
                                capsuleState = capsule,
                                selectedRemarks = selectedRow?.remarks,
                                onAddClipboard = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onAction(MainAction.ImportClipboard)
                                },
                                onPingAll = { onAction(MainAction.TestRealAllServers) },
                                onConnect = {
                                    if (!isRunning) {
                                        ceremony = true
                                        connectFailed = false
                                    }
                                    onAction(MainAction.ToggleService)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp),
                            )
                        }
                        SatraGlassBottomBar(
                            selected = tab,
                            items = navItems,
                            onSelect = { page ->
                                tab = page
                                scope.launch { pagerState.animateScrollToPage(page) }
                            },
                        )
                    }
                },
            ) { padding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { current ->
                    when (current) {
                        0 -> LiquidHomeTab(
                            capsuleState = capsule,
                            isRunning = isRunning,
                            rows = allRows.value,
                            selectedGuid = selectedGuid,
                            selectedRow = selectedRow,
                            subscriptionId = activeSub.value?.first,
                            subscription = activeSub.value?.second,
                            hasAnyConfig = allRows.value.isNotEmpty(),
                            hasAnySub = subs.value.isNotEmpty(),
                            importing = isLoading,
                            onConnect = {
                                if (!isRunning) {
                                    ceremony = true
                                    connectFailed = false
                                }
                                onAction(MainAction.ToggleService)
                            },
                            onAddClipboard = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAction(MainAction.ImportClipboard)
                            },
                            onSelectConfig = { guid ->
                                onAction(MainAction.SelectServer(guid))
                                if (isRunning) onAction(MainAction.RestartService)
                            },
                            onOpenConfigs = {
                                tab = 1
                                scope.launch { pagerState.animateScrollToPage(1) }
                            },
                            onOpenSubs = {
                                tab = 2
                                scope.launch { pagerState.animateScrollToPage(2) }
                            },
                            isTesting = uiState.isTesting,
                            onRefreshSub = {
                                onAction(MainAction.UpdateSubscriptions)
                            },
                            onPingAll = { onAction(MainAction.TestRealAllServers) },
                            onOpenUsage = {
                                context.startActivity(Intent(context, UsageMonitorActivity::class.java))
                            },
                            showDock = false,
                        )

                        1 -> SatraConfigsTab(
                            modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            subscriptions = subs.value,
                            rows = allRows.value,
                            selectedGuid = selectedGuid,
                            selectedGroupId = uiState.selectedGroupId,
                            autoConnect = autoConnect,
                            isTesting = uiState.isTesting,
                            onAutoConnectChange = {
                                autoConnect = it
                                mainViewModel.setAutoConnectEnabled(it)
                            },
                            onSelectServer = { guid ->
                                onAction(MainAction.SelectServer(guid))
                                if (isRunning) onAction(MainAction.RestartService)
                            },
                            onPingAll = { onAction(MainAction.TestRealAllServers) },
                            onRefreshSubs = { onAction(MainAction.UpdateSubscriptions) },
                            onAddConfig = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAction(MainAction.ImportClipboard)
                            },
                        )

                        2 -> SatraSubsTab(
                            modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            subscriptions = subs.value,
                            serverCounts = allRows.value
                                .groupingBy { it.profile.subscriptionId }
                                .eachCount(),
                            isRefreshing = isLoading,
                            onRefresh = { onAction(MainAction.UpdateSubscriptions) },
                            onRefreshOne = {
                                // Single-card Update uses the same full refresh path so
                                // userinfo headers land in MMKV for quota bars.
                                onAction(MainAction.UpdateSubscriptions)
                            },
                            onToggleEnabled = { id, enabled ->
                                scope.launch(Dispatchers.IO) {
                                    val sub = MmkvManager.decodeSubscription(id) ?: return@launch
                                    sub.enabled = enabled
                                    MmkvManager.encodeSubscription(id, sub)
                                    withContext(Dispatchers.Main) { subsEpoch++ }
                                }
                            },
                            onOpenEdit = { },
                            onOpenManage = { onNavigate(MainDestination.Subscriptions) },
                        )

                        else -> SettingsEmbedTab(
                            modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            onOpenFullSettings = {
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            },
                            onOpenLogs = {
                                context.startActivity(Intent(context, LogcatActivity::class.java))
                            },
                            onNavigate = onNavigate,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(ceremony, isRunning) {
        if (!ceremony) return@LaunchedEffect
        // Always play Connecting for CeremonyMs so the shield rings are visible,
        // even when the VPN comes up in under a second.
        delay(SatraMotionSpec.CeremonyMs.toLong())
        if (isRunning) {
            ceremony = false
            return@LaunchedEffect
        }
        delay(2_500L)
        if (!isRunning) {
            connectFailed = true
            ceremony = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidHomeTab(
    capsuleState: PowerCapsuleState,
    isRunning: Boolean,
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    selectedRow: ServerRowUiModel?,
    subscriptionId: String?,
    subscription: SubscriptionItem?,
    hasAnyConfig: Boolean,
    hasAnySub: Boolean,
    importing: Boolean,
    isTesting: Boolean,
    onConnect: () -> Unit,
    onAddClipboard: () -> Unit,
    onSelectConfig: (String) -> Unit,
    onOpenConfigs: () -> Unit,
    onOpenSubs: () -> Unit,
    onRefreshSub: () -> Unit,
    onPingAll: () -> Unit,
    onOpenUsage: () -> Unit,
    showDock: Boolean = true,
) {
    val reduce = rememberReduceMotion()
    val ctx = LocalContext.current
    var refreshing by remember { mutableStateOf(false) }
    val supportLink = subscription?.let { sub ->
        if (!sub.showSupportLinks) null
        else sub.supportUrl?.takeIf { it.isNotBlank() }
            ?: sub.profileWebPageUrl?.takeIf { it.isNotBlank() }
    }
    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        onRefreshSub()
        onPingAll()
        // Brief latch; spinner continues via importing / isTesting flags.
        delay(900L)
        refreshing = false
    }
    // Dock lives in Scaffold bottomBar — content can scroll so pull-to-refresh works.
    PullToRefreshBox(
        isRefreshing = refreshing || importing || isTesting,
        onRefresh = { refreshing = true },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_satra_brand_mark),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "SatraNG",
                    style = MaterialTheme.typography.titleLarge,
                    color = onGlassPrimary(),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.satra_home_support),
                    color = SatraTokens.Amber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable {
                            when {
                                supportLink != null -> Utils.openUri(ctx, supportLink)
                                else -> onOpenSubs()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            if (subscription != null && subscriptionId != null &&
                (subscription.showExpire || subscription.showUsage)
            ) {
                HomeQuotaCards(
                    subscriptionId = subscriptionId,
                    subscription = subscription,
                    vpnRunning = isRunning,
                    onRefresh = onRefreshSub,
                    onOpenUsage = onOpenUsage,
                )
            }

            Spacer(Modifier.height(28.dp))

            ProtectionShield(state = capsuleState)

            Spacer(Modifier.height(12.dp))

            val headlineColor = when (capsuleState) {
                PowerCapsuleState.Connecting -> SatraTokens.Amber
                PowerCapsuleState.On -> SatraTokens.Green
                PowerCapsuleState.Failed -> SatraTokens.Danger
                PowerCapsuleState.Off -> Color.White.copy(alpha = 0.55f)
            }
            Text(
                text = when (capsuleState) {
                    PowerCapsuleState.Off -> stringResource(R.string.satra_home_headline_ready)
                    PowerCapsuleState.Connecting -> stringResource(R.string.satra_home_headline_connecting)
                    PowerCapsuleState.On -> stringResource(R.string.satra_home_headline_connected)
                    PowerCapsuleState.Failed -> stringResource(R.string.satra_home_headline_failed)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = headlineColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f),
            )

            Spacer(Modifier.height(16.dp))

            QuickConfigRail(
                rows = rows,
                selectedGuid = selectedGuid,
                vpnConnected = isRunning,
                onSelect = onSelectConfig,
                onOpenAll = onOpenConfigs,
            )

            if (showDock) {
                Spacer(Modifier.height(10.dp))
                HomeConnectDock(
                    hasAnyConfig = hasAnyConfig,
                    importing = importing,
                    isTesting = isTesting,
                    pingMs = selectedRow?.testDelayMillis?.takeIf { it != 0L },
                    capsuleState = capsuleState,
                    selectedRemarks = selectedRow?.remarks,
                    onAddClipboard = onAddClipboard,
                    onPingAll = onPingAll,
                    onConnect = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Ping / + FAB + slide capsule — meant to sit directly above the bottom nav. */
@Composable
private fun HomeConnectDock(
    hasAnyConfig: Boolean,
    importing: Boolean,
    isTesting: Boolean,
    pingMs: Long?,
    capsuleState: PowerCapsuleState,
    selectedRemarks: String?,
    onAddClipboard: () -> Unit,
    onPingAll: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = rememberReduceMotion()
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hasAnyConfig) {
            EmptyPlusButton(importing = importing, onClick = onAddClipboard, reduce = reduce)
        } else {
            HomePingButton(
                testing = isTesting,
                pingMs = pingMs,
                onClick = onPingAll,
                reduce = reduce,
            )
        }
        SatraGlassCapsule(
            state = capsuleState,
            subtitle = when (capsuleState) {
                PowerCapsuleState.Off -> stringResource(R.string.satra_capsule_ready)
                PowerCapsuleState.On -> selectedRemarks
                else -> null
            },
            pingLabel = null,
            onToggle = onConnect,
            onCenterTap = null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeQuotaCards(
    subscriptionId: String,
    subscription: SubscriptionItem,
    vpnRunning: Boolean,
    onRefresh: () -> Unit,
    onOpenUsage: () -> Unit,
) {
    HybridUsageTracker.ensureBaseline(subscriptionId, subscription)
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vpnRunning, subscriptionId) {
        if (!vpnRunning) return@LaunchedEffect
        while (true) {
            withContext(Dispatchers.IO) {
                // Prefer NotificationManager's sampler when it is already running;
                // otherwise consume here so local delta still advances.
                if (!AngNotificationManager.isSpeedNotificationRunning()) {
                    HybridUsageTracker.consumeOutboundStats()
                }
            }
            tick = System.currentTimeMillis()
            delay(2_500L)
        }
    }
    // Re-read hybrid display when local delta ticks.
    val usage = remember(subscriptionId, subscription.upload, subscription.download, subscription.total, tick) {
        HybridUsageTracker.displayFor(subscriptionId, subscription)
    }
    val used = usage.displayUsed
    val rem = usage.remaining
    val days = subscriptionDaysLeft(subscription)
    val totalDays = (days ?: 0).coerceAtLeast(1).let { left ->
        maxOf(left, 30)
    }
    val timeFrac = when {
        days == null -> 1f
        days <= 0 -> 0f
        else -> (days.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
    }
    val dataFrac = usage.fractionUsed?.let { 1f - it } ?: 1f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (subscription.showExpire) {
            QuotaCard(
                label = stringResource(R.string.satra_home_quota_time),
                primary = when {
                    days == null -> stringResource(R.string.satra_sub_no_expiry)
                    days <= 0 -> stringResource(R.string.satra_sub_expired)
                    else -> stringResource(R.string.satra_home_quota_days_of, days, totalDays)
                },
                secondary = when {
                    days == null -> ""
                    days <= 0 -> ""
                    else -> stringResource(R.string.satra_sub_days_left, days)
                },
                fraction = timeFrac,
                accent = SatraTokens.Amber,
                // Tap: refresh sub/quota (plan phase 4). Long-press: open usage monitor.
                onClick = onRefresh,
                onLongClick = onOpenUsage,
                modifier = Modifier.weight(1f),
            )
        }
        if (subscription.showUsage) {
            QuotaCard(
                label = stringResource(R.string.satra_home_quota_data),
                primary = if (subscription.total > 0L) {
                    stringResource(
                        R.string.satra_home_quota_data_of,
                        rem?.toTrafficString() ?: "0",
                        subscription.total.toTrafficString(),
                    )
                } else {
                    stringResource(R.string.satra_sub_unlimited)
                },
                secondary = stringResource(R.string.satra_home_traffic_used, used.toTrafficString()),
                fraction = dataFrac.coerceIn(0f, 1f),
                accent = SatraTokens.Green,
                onClick = onRefresh,
                onLongClick = onOpenUsage,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuotaCard(
    label: String,
    primary: String,
    secondary: String,
    fraction: Float,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    // Fixed height so Time/Data cards always match in the real app.
    Column(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = onGlassSecondary(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                primary,
                style = MaterialTheme.typography.titleSmall,
                color = onGlassPrimary(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                secondary.ifBlank { " " },
                style = MaterialTheme.typography.bodySmall,
                color = if (secondary.isBlank()) Color.Transparent else onGlassSecondary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .background(
                        Brush.horizontalGradient(listOf(accent.copy(alpha = 0.75f), accent)),
                    ),
            )
        }
    }
}

@Composable
private fun ProtectionShield(state: PowerCapsuleState) {
    val stroke = when (state) {
        PowerCapsuleState.On -> SatraTokens.Green
        PowerCapsuleState.Connecting -> SatraTokens.Amber
        PowerCapsuleState.Failed -> SatraTokens.Danger
        PowerCapsuleState.Off -> Color.White.copy(alpha = 0.45f)
    }
    val fill = when (state) {
        PowerCapsuleState.On -> SatraTokens.Green.copy(alpha = 0.22f)
        PowerCapsuleState.Connecting -> SatraTokens.Amber.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    val ringPulse = if (state == PowerCapsuleState.Connecting || state == PowerCapsuleState.On) {
        val infinite = rememberInfiniteTransition(label = "shieldRing")
        infinite.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ringScale",
        ).value
    } else {
        1f
    }
    val ringAlpha = if (state == PowerCapsuleState.Connecting) {
        val infinite = rememberInfiniteTransition(label = "shieldRingAlpha")
        infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ringAlpha",
        ).value
    } else {
        0.18f
    }
    val haloBreath = if (state == PowerCapsuleState.Connecting || state == PowerCapsuleState.On) {
        val infinite = rememberInfiniteTransition(label = "shieldHalo")
        infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (state == PowerCapsuleState.Connecting) 900 else 1800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "haloBreath",
        ).value
    } else {
        0.35f
    }
    // Single stage: halo + shield + ring share one center so the icon sits in the glow.
    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        stroke.copy(alpha = 0.55f * haloBreath),
                        stroke.copy(alpha = 0.22f * haloBreath),
                        stroke.copy(alpha = 0.06f * haloBreath),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = maxR,
                ),
                radius = maxR,
                center = center,
            )
        }
        Box(
            modifier = Modifier.size(152.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .scale(ringPulse),
            ) {
                val w = size.width
                drawCircle(
                    color = stroke.copy(alpha = ringAlpha),
                    radius = w * 0.42f,
                    center = Offset(w / 2f, w / 2f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            Canvas(Modifier.size(112.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.82f, h * 0.24f)
                    lineTo(w * 0.82f, h * 0.48f)
                    cubicTo(w * 0.82f, h * 0.72f, w * 0.66f, h * 0.86f, w * 0.5f, h * 0.92f)
                    cubicTo(w * 0.34f, h * 0.86f, w * 0.18f, h * 0.72f, w * 0.18f, h * 0.48f)
                    lineTo(w * 0.18f, h * 0.24f)
                    close()
                }
                drawPath(path, color = fill)
                drawPath(path, color = stroke, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                if (state == PowerCapsuleState.Off || state == PowerCapsuleState.Failed) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(w * 0.42f, h * 0.38f),
                        end = Offset(w * 0.62f, h * 0.66f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_lock_24dp),
                contentDescription = null,
                tint = stroke,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private fun subscriptionDaysLeft(subscription: SubscriptionItem): Int? {
    if (subscription.expire <= 0L) return null
    val expireMs = if (subscription.expire > 10_000_000_000L) {
        subscription.expire
    } else {
        subscription.expire * 1000L
    }
    val left = expireMs - System.currentTimeMillis()
    if (left <= 0L) return 0
    return ((left + 86_399_999L) / 86_400_000L).toInt()
}

@Composable
private fun QuickConfigRail(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    vpnConnected: Boolean,
    onSelect: (String) -> Unit,
    onOpenAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.satra_home_quick_config).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = onGlassSecondary(),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        if (rows.isEmpty()) {
            SatraGlassSurface(
                onClick = onOpenAll,
                capsule = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    stringResource(R.string.satra_locations_empty),
                    style = MaterialTheme.typography.labelLarge,
                    color = onGlassSecondary(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            return
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(rows, key = { it.guid }) { row ->
                QuickConfigChip(
                    row = row,
                    selected = row.guid == selectedGuid,
                    vpnConnected = vpnConnected,
                    onClick = { onSelect(row.guid) },
                )
            }
        }
    }
}

@Composable
private fun QuickConfigChip(
    row: ServerRowUiModel,
    selected: Boolean,
    vpnConnected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        SatraTokens.Teal.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.06f)
    }
    val stroke = if (selected) {
        SatraTokens.Teal.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    Row(
        modifier = Modifier
            .widthIn(min = 148.dp, max = 180.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(width = if (selected) 1.5.dp else 1.dp, color = stroke, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            row.flag.ifBlank { "🌐" },
            style = MaterialTheme.typography.titleMedium,
        )
        Column(Modifier.weight(1f)) {
            Text(
                row.remarks,
                style = MaterialTheme.typography.labelLarge,
                color = onGlassPrimary(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                row.countryName?.takeIf { it.isNotBlank() },
                row.typeDescription?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = onGlassSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SatraPingBadge(
            delayMs = row.testDelayMillis,
            vpnConnected = vpnConnected,
        )
    }
}

@Composable
private fun EmptyPlusButton(importing: Boolean, onClick: () -> Unit, reduce: Boolean) {
    val breath = if (reduce || importing) {
        1f
    } else {
        val infinite = rememberInfiniteTransition(label = "plusBreath")
        infinite.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(SatraMotionSpec.EmptyPlusBreathMs),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "plusScale",
        ).value
    }
    SatraGlassIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp).scale(breath).clip(CircleShape),
    ) {
        if (importing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = SatraTokens.Teal,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_add_24dp),
                contentDescription = stringResource(R.string.satra_home_add_config),
                tint = SatraTokens.Amber,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun HomePingButton(
    testing: Boolean,
    pingMs: Long?,
    onClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") reduce: Boolean,
) {
    val a11y = when {
        testing -> stringResource(R.string.satra_home_ping_all)
        pingMs == null || pingMs == 0L -> stringResource(R.string.satra_home_ping_none_a11y)
        pingMs < 0L -> stringResource(R.string.satra_home_ping_none_a11y)
        else -> stringResource(R.string.satra_home_ping_a11y, pingMs.toInt())
    }
    val pingColor = when {
        pingMs == null || pingMs == 0L -> SatraTokens.Amber
        pingMs < 0L -> SatraTokens.Danger
        pingMs < 150L -> SatraTokens.Green
        pingMs < 250L -> SatraTokens.Amber
        else -> SatraTokens.Danger
    }
    SatraGlassIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .semantics { contentDescription = a11y },
    ) {
        when {
            testing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = SatraTokens.Amber,
                )
            }
            pingMs != null && pingMs != 0L -> {
                Text(
                    text = if (pingMs < 0L) "—" else "${pingMs}ms",
                    color = pingColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            else -> {
                Icon(
                    painter = painterResource(R.drawable.ic_flash_on_24dp),
                    contentDescription = null,
                    tint = SatraTokens.Amber,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsEmbedTab(
    modifier: Modifier,
    onOpenFullSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    data class SettingsEntry(
        val title: String,
        val subtitle: String,
        val emoji: String,
        val onClick: () -> Unit,
    )
    val entries = listOf(
        SettingsEntry(
            title = stringResource(R.string.satra_settings_section_connection),
            subtitle = stringResource(R.string.satra_settings_connection_sub),
            emoji = "⚡",
            onClick = onOpenFullSettings,
        ),
        SettingsEntry(
            title = stringResource(R.string.satra_settings_privacy_title),
            subtitle = stringResource(R.string.satra_settings_privacy_sub),
            emoji = "🔒",
            onClick = onOpenFullSettings,
        ),
        SettingsEntry(
            title = stringResource(MainDestination.PerAppProxy.labelRes),
            subtitle = stringResource(R.string.satra_settings_per_app_sub),
            emoji = "📱",
            onClick = { onNavigate(MainDestination.PerAppProxy) },
        ),
        SettingsEntry(
            title = stringResource(MainDestination.Routing.labelRes),
            subtitle = stringResource(R.string.satra_settings_routing_sub),
            emoji = "🧭",
            onClick = { onNavigate(MainDestination.Routing) },
        ),
        SettingsEntry(
            title = stringResource(MainDestination.BackupRestore.labelRes),
            subtitle = stringResource(R.string.satra_settings_backup_sub),
            emoji = "💾",
            onClick = { onNavigate(MainDestination.BackupRestore) },
        ),
        SettingsEntry(
            title = stringResource(R.string.satra_settings_logs),
            subtitle = stringResource(R.string.satra_settings_logs_subtitle),
            emoji = "📋",
            onClick = onOpenLogs,
        ),
        SettingsEntry(
            title = stringResource(MainDestination.About.labelRes),
            subtitle = stringResource(R.string.satra_settings_about_sub),
            emoji = "ℹ️",
            onClick = { onNavigate(MainDestination.About) },
        ),
    )
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            stringResource(R.string.satra_nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = onGlassPrimary(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable(onClick = entry.onClick)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(SatraTokens.Amber.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(entry.emoji, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = onGlassPrimary(),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        entry.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = onGlassSecondary(),
                    )
                }
                Text("›", color = onGlassSecondary().copy(alpha = 0.6f))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
