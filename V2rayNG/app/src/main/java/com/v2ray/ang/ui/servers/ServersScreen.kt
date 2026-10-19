package com.v2ray.ang.ui.servers

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.app.AppNav
import com.v2ray.ang.ui.compose.satra.GroupSeparator
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.PingPill
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraAlert
import com.v2ray.ang.ui.compose.satra.SatraChip
import com.v2ray.ang.ui.compose.satra.SatraEmptyState
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLargeTitleScaffold
import com.v2ray.ang.ui.compose.satra.SatraMenu
import com.v2ray.ang.ui.compose.satra.SatraMenuItem
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraSearchField
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSheet
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SheetTitle
import com.v2ray.ang.ui.compose.satra.rememberSatraHaptics
import com.v2ray.ang.ui.compose.satra.satraClickable
import com.v2ray.ang.ui.home.FlagBadge
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainUiState
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.main.ServerRowUiModel
import com.v2ray.ang.ui.main.ServerSort

private enum class ConfirmKind { RemoveAll, RemoveDuplicates, RemoveInvalid }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(vm: MainViewModel, nav: AppNav) {
    val state by vm.uiState.collectAsState()
    val group by vm.serverGroupState(state.selectedGroupId).collectAsState()
    val rows = remember(group.rows, state.hiddenGuids) { group.rows.filter { it.guid !in state.hiddenGuids } }
    ServersContent(state = state, rows = rows, nav = nav, onAction = vm::onAction)
}

/** Stateless body of the Servers tab (also rendered by the screenshot tests). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersContent(
    state: MainUiState,
    rows: List<ServerRowUiModel>,
    nav: AppNav,
    onAction: (MainAction) -> Unit,
    initialQuery: String = "",
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var menu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var manualSheet by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ServerRowUiModel?>(null) }
    var confirm by remember { mutableStateOf<ConfirmKind?>(null) }
    val haptics = rememberSatraHaptics()
    val c = Satra.colors

    LaunchedEffect(query) { onAction(MainAction.Search(query)) }

    PullToRefreshBox(
        isRefreshing = state.isTesting,
        onRefresh = { onAction(MainAction.TestRealAllServers) },
    ) {
        SatraLargeTitleScaffold(
            title = stringResource(R.string.sx_servers_title),
            subtitle = if (rows.isNotEmpty()) pluralStringResource(R.plurals.sx_configs_count, rows.size, rows.size) else null,
            actions = {
                SatraIconButton(
                    if (state.isTesting) SatraIcons.Stop else SatraIcons.Pulse,
                    stringResource(if (state.isTesting) R.string.sx_servers_stop_test else R.string.sx_servers_test_all),
                    { onAction(if (state.isTesting) MainAction.CancelTesting else MainAction.TestRealAllServers) },
                    tint = c.accent,
                )
                Box {
                    SatraIconButton(SatraIcons.Plus, stringResource(R.string.sx_add), { addMenu = true })
                    SatraMenu(addMenu, { addMenu = false }) {
                        SatraMenuItem(stringResource(R.string.sx_add_scan), { addMenu = false; nav.action(MainAction.ImportQRcode) }, SatraIcons.Scan)
                        SatraMenuItem(stringResource(R.string.sx_add_clipboard), { addMenu = false; nav.action(MainAction.ImportClipboard) }, SatraIcons.Clipboard)
                        SatraMenuItem(stringResource(R.string.sx_add_file), { addMenu = false; nav.action(MainAction.ImportConfigLocal) }, SatraIcons.File)
                        SatraMenuItem(stringResource(R.string.sx_add_manual), { addMenu = false; manualSheet = true }, SatraIcons.Edit)
                    }
                }
                Box {
                    SatraIconButton(SatraIcons.More, stringResource(R.string.sx_more_options), { menu = true })
                    SatraMenu(menu, { menu = false }) {
                        SatraMenuItem(stringResource(R.string.sx_sort_default), { menu = false; onAction(MainAction.SetSort(ServerSort.Default)) }, if (state.sort == ServerSort.Default) SatraIcons.Check else SatraIcons.Sort)
                        SatraMenuItem(stringResource(R.string.sx_sort_ping), { menu = false; onAction(MainAction.SetSort(ServerSort.Ping)) }, if (state.sort == ServerSort.Ping) SatraIcons.Check else SatraIcons.Pulse)
                        SatraMenuItem(stringResource(R.string.sx_sort_name), { menu = false; onAction(MainAction.SetSort(ServerSort.Name)) }, if (state.sort == ServerSort.Name) SatraIcons.Check else SatraIcons.Tag)
                        SatraMenuItem(stringResource(R.string.sx_remove_duplicates), { menu = false; confirm = ConfirmKind.RemoveDuplicates }, SatraIcons.Copy)
                        SatraMenuItem(stringResource(R.string.sx_remove_invalid), { menu = false; confirm = ConfirmKind.RemoveInvalid }, SatraIcons.Ban)
                        SatraMenuItem(stringResource(R.string.sx_remove_all), { menu = false; confirm = ConfirmKind.RemoveAll }, SatraIcons.Trash, destructive = true)
                    }
                }
            },
        ) {
            item(key = "search") {
                Box(Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 10.dp)) {
                    SatraSearchField(query, { query = it }, stringResource(R.string.sx_search), clearLabel = stringResource(R.string.sx_clear))
                }
            }
            item(key = "chips") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = SatraSpace.Gutter).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SatraChip(
                        stringResource(R.string.sx_favorites), selected = state.favoritesOnly,
                        icon = if (state.favoritesOnly) SatraIcons.StarFilled else SatraIcons.Star,
                        onClick = { onAction(MainAction.SetFavoritesOnly(!state.favoritesOnly)) },
                    )
                    if (state.groups.size > 1) {
                        state.groups.forEach { g ->
                            SatraChip(
                                g.remarks.ifBlank { stringResource(R.string.sx_group_default) },
                                selected = g.id == state.selectedGroupId,
                                onClick = { onAction(MainAction.SelectGroup(g.id)) },
                            )
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    val searching = query.isNotBlank() || state.favoritesOnly
                    SatraEmptyState(
                        icon = if (searching) SatraIcons.Search else SatraIcons.Servers,
                        title = stringResource(if (searching) R.string.sx_servers_none_found else R.string.sx_servers_empty_title),
                        message = stringResource(if (searching) R.string.sx_servers_none_found_msg else R.string.sx_servers_empty_msg),
                        actionText = if (searching) null else stringResource(R.string.sx_add_clipboard),
                        onAction = if (searching) null else ({ nav.action(MainAction.ImportClipboard) }),
                        secondaryText = if (searching) null else stringResource(R.string.sx_add_scan),
                        onSecondary = if (searching) null else ({ nav.action(MainAction.ImportQRcode) }),
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            } else {
                items(rows, key = { it.guid }) { row ->
                    ServerItem(
                        row = row,
                        selected = row.guid == state.selectedGuid,
                        testing = state.isTesting && row.testDelayMillis == 0L,
                        onSelect = { nav.action(MainAction.SelectServer(row.guid)) },
                        onMore = { actionsFor = row },
                        onFavorite = { onAction(MainAction.ToggleFavorite(row.guid)) },
                        onDelete = { onAction(MainAction.DeleteWithUndo(row.guid)) },
                        modifier = Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 8.dp),
                    )
                }
            }
        }
    }

    actionsFor?.let { row ->
        ServerActionsSheet(row, nav, onAction, onDismiss = { actionsFor = null })
    }
    if (manualSheet) ManualSheet(onPick = { type -> manualSheet = false; nav.action(MainAction.ImportManually(type)) }, onDismiss = { manualSheet = false })
    state.shareQRCodeBitmap?.let { bmp ->
        SatraSheet(onDismiss = { onAction(MainAction.DismissQRCodeDialog) }) {
            SheetTitle(stringResource(R.string.sx_qr))
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Image(
                    bmp.asImageBitmap(), stringResource(R.string.sx_qr),
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.White).padding(10.dp),
                )
            }
        }
    }
    confirm?.let { kind ->
        SatraAlert(
            title = stringResource(
                when (kind) {
                    ConfirmKind.RemoveAll -> R.string.sx_remove_all_title
                    ConfirmKind.RemoveDuplicates -> R.string.sx_remove_duplicates_title
                    ConfirmKind.RemoveInvalid -> R.string.sx_remove_invalid_title
                },
            ),
            message = stringResource(if (kind == ConfirmKind.RemoveAll) R.string.sx_remove_all_msg else R.string.sx_remove_other_msg),
            confirmText = stringResource(R.string.sx_delete),
            dismissText = stringResource(R.string.sx_cancel),
            destructive = true,
            onConfirm = {
                haptics.heavy()
                when (kind) {
                    ConfirmKind.RemoveAll -> onAction(MainAction.RemoveAllServers)
                    ConfirmKind.RemoveDuplicates -> onAction(MainAction.RemoveDuplicateServers)
                    ConfirmKind.RemoveInvalid -> onAction(MainAction.RemoveInvalidServers)
                }
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ServerItem(
    row: ServerRowUiModel,
    selected: Boolean,
    testing: Boolean,
    onSelect: () -> Unit,
    onMore: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Satra.colors
    val haptics = rememberSatraHaptics()
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                haptics.confirm()
                onFavorite()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                haptics.heavy()
                onDelete()
                // If the delete was refused (active server) the row stays: slide it back.
                kotlinx.coroutines.delay(300)
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(SatraShapes.Large),
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bg by animateColorAsState(
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> c.warning
                    SwipeToDismissBoxValue.EndToStart -> c.danger
                    else -> c.fill
                },
                tween(120), label = "swipeBg",
            )
            Row(
                Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (dir == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
            ) {
                Icon(
                    if (dir == SwipeToDismissBoxValue.EndToStart) SatraIcons.Trash else if (row.favorite) SatraIcons.Star else SatraIcons.StarFilled,
                    null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp),
                )
            }
        },
    ) {
        val border by animateColorAsState(if (selected) c.accent else androidx.compose.ui.graphics.Color.Transparent, tween(180), label = "selBorder")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SatraShapes.Large)
                .background(if (selected) c.accentSoft else c.surface)
                .satraClickable(pressedScale = 0.985f, onLongClick = { haptics.heavy(); onMore() }, onClick = onSelect)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagBadge(row.countryCode, row.exitVerified, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.remarks.ifBlank { row.profile.server.orEmpty() },
                        style = Satra.type.headline, color = c.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(SatraIcons.StarFilled, null, tint = c.warning, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    listOf(row.locationLabel, row.typeDescription).filter { it.isNotBlank() }.joinToString(" · "),
                    style = Satra.type.footnote, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            PingPill(row.testDelayMillis, testing = testing)
            SatraIconButton(SatraIcons.More, stringResource(R.string.sx_more_options), onMore, tint = c.textSecondary, size = 20.dp)
        }
    }
}

@Composable
internal fun ServerActionsSheet(server: ServerRowUiModel, nav: AppNav, onAction: (MainAction) -> Unit, onDismiss: () -> Unit) {
    val c = Satra.colors
    SatraSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                FlagBadge(server.countryCode, server.exitVerified, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(server.remarks, style = Satra.type.title2, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(server.locationLabel, server.typeDescription).filter { it.isNotBlank() }.joinToString(" · "),
                        style = Satra.type.footnote, color = c.textSecondary,
                    )
                }
            }
            InsetGroup {
                row { SatraRow(stringResource(R.string.sx_server_use), icon = SatraIcons.Power, iconColor = c.accent, onClick = { onDismiss(); nav.action(MainAction.SelectServer(server.guid)) }) }
                row { SatraRow(stringResource(if (server.favorite) R.string.sx_server_unfavorite else R.string.sx_server_favorite), icon = SatraIcons.Star, iconColor = c.warning, onClick = { onDismiss(); onAction(MainAction.ToggleFavorite(server.guid)) }) }
                row { SatraRow(stringResource(R.string.sx_server_recheck), icon = SatraIcons.Globe, iconColor = c.info, onClick = { onDismiss(); onAction(MainAction.RecheckServer(server.guid)) }) }
                row { SatraRow(stringResource(R.string.sx_server_edit), icon = SatraIcons.Edit, iconColor = c.textSecondary, onClick = { onDismiss(); nav.action(MainAction.EditServer(server.guid, server.profile)) }) }
            }
            Spacer(Modifier.height(12.dp))
            InsetGroup {
                row { SatraRow(stringResource(R.string.sx_server_copy), icon = SatraIcons.Copy, iconColor = c.info, onClick = { onDismiss(); nav.action(MainAction.ShareClipboard(server.guid)) }) }
                row { SatraRow(stringResource(R.string.sx_server_qr), icon = SatraIcons.Qr, iconColor = c.info, onClick = { onDismiss(); onAction(MainAction.ShareQRCode(server.guid)) }) }
                row { SatraRow(stringResource(R.string.sx_server_copy_full), icon = SatraIcons.File, iconColor = c.info, onClick = { onDismiss(); nav.action(MainAction.ShareFullContent(server.guid)) }) }
            }
            Spacer(Modifier.height(12.dp))
            InsetGroup {
                row { SatraRow(stringResource(R.string.sx_delete), icon = SatraIcons.Trash, iconColor = c.danger, destructive = true, onClick = { onDismiss(); onAction(MainAction.DeleteWithUndo(server.guid)) }) }
            }
        }
    }
}

private data class ManualType(val label: String, val type: EConfigType)

@Composable
private fun ManualSheet(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val types = remember {
        listOf(
            ManualType("VLESS", EConfigType.VLESS), ManualType("VMess", EConfigType.VMESS),
            ManualType("Trojan", EConfigType.TROJAN), ManualType("Shadowsocks", EConfigType.SHADOWSOCKS),
            ManualType("Hysteria2", EConfigType.HYSTERIA2), ManualType("WireGuard", EConfigType.WIREGUARD),
            ManualType("SOCKS", EConfigType.SOCKS), ManualType("HTTP", EConfigType.HTTP),
        )
    }
    SatraSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.sx_add_manual), subtitle = stringResource(R.string.sx_add_manual_msg))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            InsetGroup {
                types.forEach { t ->
                    row { SatraRow(t.label, icon = SatraIcons.Servers, iconColor = Satra.colors.accent, chevron = true, onClick = { onPick(t.type.value) }) }
                }
            }
        }
    }
}
