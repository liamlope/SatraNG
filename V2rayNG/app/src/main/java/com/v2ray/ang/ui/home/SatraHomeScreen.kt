package com.v2ray.ang.ui.home

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.QRCodeDialog
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
import com.v2ray.ang.ui.compose.satra.SatraUsageRing
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val selectedGuid = uiState.selectedGuid
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var ceremony by remember { mutableStateOf(false) }
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

    LaunchedEffect(uiState.groups, uiState.selectedGroupId, uiState.isTesting, uiState.isRunning, subsEpoch) {
        withContext(Dispatchers.IO) {
            val rows = mutableListOf<ServerRowUiModel>()
            uiState.groups.forEach { group ->
                loadServersForGroup(group.id).forEach { server ->
                    rows += buildServerRowUiModel(server, group.remarks)
                }
            }
            allRows.value = rows
            val decoded = MmkvManager.decodeSubscriptions().map { it.guid to it.subscription }
            subs.value = decoded
            val subId = uiState.selectedGroupId
            val sub = MmkvManager.decodeSubscription(subId)
            activeSub.value = if (sub != null) subId to sub else decoded.firstOrNull()
        }
    }

    LaunchedEffect(isRunning, uiState.status) {
        if (isRunning) {
            ceremony = false
            connectFailed = false
        } else if (uiState.status == MainStatus.Disconnected && ceremony) {
            // wait for start result
        }
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

    // Home · Configs · Subs · Settings
    val navItems = listOf(
        Triple(0, R.string.satra_nav_home, R.drawable.ic_stat_satra),
        Triple(1, R.string.satra_nav_configs, R.drawable.ic_flash_on_24dp),
        Triple(2, R.string.satra_nav_subs, R.drawable.ic_subscriptions_24dp),
        Triple(3, R.string.satra_nav_settings, R.drawable.ic_settings_24dp),
    )

    SatraAtmosphere(state = atmState) {
        SatraGlassProvider {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    SatraGlassBottomBar(
                        selected = tab,
                        items = navItems,
                        onSelect = { tab = it },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    )
                },
            ) { padding ->
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(if (reduce) SatraMotionSpec.short() else SatraMotionSpec.tabContentCrossfade()) togetherWith
                            fadeOut(if (reduce) SatraMotionSpec.short() else SatraMotionSpec.tabContentCrossfade())
                    },
                    label = "tabContent",
                    modifier = Modifier.padding(padding),
                ) { current ->
                    when (current) {
                        0 -> LiquidHomeTab(
                            capsuleState = capsule,
                            selectedRow = selectedRow,
                            subscription = activeSub.value?.second,
                            serverCount = activeSub.value?.let { (id, _) ->
                                allRows.value.count { it.profile.subscriptionId == id }
                            } ?: allRows.value.size,
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
                            onOpenConfigs = { tab = 1 },
                            onRefreshSub = { onAction(MainAction.UpdateSubscriptions) },
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
                        )

                        2 -> SatraSubsTab(
                            modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            subscriptions = subs.value,
                            serverCounts = allRows.value
                                .groupingBy { it.profile.subscriptionId }
                                .eachCount(),
                            onRefresh = { onAction(MainAction.UpdateSubscriptions) },
                            onToggleEnabled = { id, enabled ->
                                scope.launch(Dispatchers.IO) {
                                    val sub = MmkvManager.decodeSubscription(id) ?: return@launch
                                    sub.enabled = enabled
                                    MmkvManager.encodeSubscription(id, sub)
                                    withContext(Dispatchers.Main) { subsEpoch++ }
                                }
                            },
                            onOpenEdit = { },
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
        val minMs = SatraMotionSpec.CeremonyMs.toLong().coerceAtLeast(700L)
        delay(minMs)
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

@Composable
private fun LiquidHomeTab(
    capsuleState: PowerCapsuleState,
    selectedRow: ServerRowUiModel?,
    subscription: SubscriptionItem?,
    serverCount: Int,
    hasAnySub: Boolean,
    importing: Boolean,
    onConnect: () -> Unit,
    onAddClipboard: () -> Unit,
    onOpenConfigs: () -> Unit,
    onRefreshSub: () -> Unit,
) {
    val reduce = rememberReduceMotion()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_satra_mark),
                contentDescription = null,
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
            if (subscription != null && subscription.showUsage) {
                SatraUsageRing(
                    fraction = subscription.usageFraction(),
                    warn = subscription.isNearQuota(0.9f) || subscription.isExpired(),
                    size = 36.dp,
                )
            }
        }

        if (!hasAnySub) {
            Spacer(Modifier.weight(1f))
            EmptyPlusButton(importing = importing, onClick = onAddClipboard, reduce = reduce)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.satra_home_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = onGlassSecondary(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            return
        }

        Spacer(Modifier.weight(0.35f))

        StatusOrb(state = capsuleState, reduce = reduce)

        Spacer(Modifier.height(28.dp))

        RegionPill(
            row = selectedRow,
            onClick = onOpenConfigs,
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = when (capsuleState) {
                PowerCapsuleState.Off -> stringResource(R.string.satra_home_headline_ready)
                PowerCapsuleState.Connecting -> stringResource(R.string.satra_home_headline_connecting)
                PowerCapsuleState.On -> stringResource(R.string.satra_home_headline_connected)
                PowerCapsuleState.Failed -> stringResource(R.string.satra_home_headline_failed)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = onGlassPrimary(),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.86f),
        )

        if (subscription != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(
                    subscription.profileTitle?.takeIf { it.isNotBlank() } ?: subscription.remarks,
                    stringResource(R.string.satra_sub_servers_count, serverCount),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = onGlassSecondary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onRefreshSub),
            )
        }

        Spacer(Modifier.weight(0.55f))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SatraGlassIconButton(
                onClick = onAddClipboard,
                modifier = Modifier.size(56.dp).clip(CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = stringResource(R.string.satra_home_add_config),
                    tint = SatraTokens.TealDeep,
                    modifier = Modifier.size(26.dp),
                )
            }
            SatraGlassCapsule(
                state = capsuleState,
                subtitle = when (capsuleState) {
                    PowerCapsuleState.Off -> stringResource(R.string.satra_capsule_ready)
                    PowerCapsuleState.On -> selectedRow?.remarks
                    else -> null
                },
                pingLabel = selectedRow?.takeIf { it.testDelayMillis > 0 }?.let { "${it.testDelayMillis}ms" },
                onToggle = onConnect,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun StatusOrb(state: PowerCapsuleState, reduce: Boolean) {
    val breath = if (reduce || state == PowerCapsuleState.Off) {
        1f
    } else {
        val infinite = rememberInfiniteTransition(label = "orbBreath")
        infinite.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(SatraMotionSpec.EmptyPlusBreathMs),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "orbScale",
        ).value
    }
    val accent = when (state) {
        PowerCapsuleState.On -> SatraTokens.Teal
        PowerCapsuleState.Connecting -> SatraTokens.Amber
        PowerCapsuleState.Failed -> SatraTokens.Danger
        PowerCapsuleState.Off -> SatraTokens.TealDeep.copy(alpha = 0.55f)
    }
    Box(
        modifier = Modifier
            .size(196.dp)
            .scale(breath),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.35f),
                        accent.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.42f, size.height * 0.38f),
                    radius = r * 1.15f,
                ),
                radius = r,
            )
        }
        SatraGlassSurface(
            modifier = Modifier.size(148.dp),
            corner = 999.dp,
            capsule = true,
            connectedTint = state == PowerCapsuleState.On,
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                PowerCapsuleState.Connecting -> CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.5.dp,
                    color = accent,
                )
                PowerCapsuleState.On -> Icon(
                    painter = painterResource(R.drawable.ic_fab_check),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(40.dp),
                )
                PowerCapsuleState.Failed -> Icon(
                    painter = painterResource(R.drawable.ic_stop_24dp),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(36.dp),
                )
                PowerCapsuleState.Off -> Icon(
                    painter = painterResource(R.drawable.ic_play_24dp),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun RegionPill(row: ServerRowUiModel?, onClick: () -> Unit) {
    SatraGlassSurface(
        onClick = onClick,
        capsule = true,
        modifier = Modifier.height(40.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                row?.flag?.takeIf { it.isNotBlank() } ?: "🌐",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                row?.let {
                    listOfNotNull(
                        it.countryName?.takeIf { n -> n.isNotBlank() },
                        it.remarks.takeIf { r -> r.isNotBlank() },
                    ).joinToString(" · ").ifBlank { it.remarks }
                } ?: stringResource(R.string.satra_home_region_fallback),
                style = MaterialTheme.typography.labelLarge,
                color = onGlassPrimary(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row != null && row.testDelayMillis > 0) {
                Spacer(Modifier.size(10.dp))
                SatraPingBadge(row.testDelayMillis)
            }
        }
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
        modifier = Modifier.size(96.dp).scale(breath).clip(CircleShape),
    ) {
        if (importing) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = SatraTokens.TealDeep,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_add_24dp),
                contentDescription = stringResource(R.string.satra_home_add_config),
                tint = SatraTokens.TealDeep,
                modifier = Modifier.size(40.dp),
            )
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
        SatraGlassCard(onClick = onOpenFullSettings) {
            Text(
                stringResource(R.string.satra_settings_hub_title),
                style = MaterialTheme.typography.titleMedium,
                color = onGlassPrimary(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.satra_settings_hub_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = onGlassSecondary(),
            )
        }
        Spacer(Modifier.height(12.dp))
        SatraGlassCard(onClick = onOpenLogs, modifier = Modifier.padding(bottom = 10.dp)) {
            Text(
                stringResource(R.string.satra_settings_logs),
                style = MaterialTheme.typography.titleSmall,
                color = onGlassPrimary(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.satra_settings_logs_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = onGlassSecondary(),
            )
        }
        listOf(
            MainDestination.PerAppProxy,
            MainDestination.Routing,
            MainDestination.BackupRestore,
            MainDestination.About,
        ).forEach { dest ->
            SatraGlassCard(onClick = { onNavigate(dest) }, modifier = Modifier.padding(bottom = 10.dp)) {
                Text(
                    stringResource(dest.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = onGlassPrimary(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
