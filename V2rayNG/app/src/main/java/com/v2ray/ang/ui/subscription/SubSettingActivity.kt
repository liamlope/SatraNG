package com.v2ray.ang.ui.subscription

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.liquid.ConnectionAtmosphereState
import com.v2ray.ang.ui.compose.liquid.SatraAtmosphere
import com.v2ray.ang.ui.compose.liquid.SatraGlassCard
import com.v2ray.ang.ui.compose.liquid.SatraGlassIconButton
import com.v2ray.ang.ui.compose.liquid.SatraGlassProvider
import com.v2ray.ang.ui.compose.liquid.SatraGlassToggle
import com.v2ray.ang.ui.compose.liquid.onGlassPrimary
import com.v2ray.ang.ui.compose.liquid.onGlassSecondary
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class SubscriptionShareAction(@StringRes val labelRes: Int) {
    QRCode(R.string.share_subscription_qrcode),
    Clipboard(R.string.share_subscription_clipboard)
}

private data class SubscriptionDeleteTarget(val guid: String, val name: String)

class SubSettingActivity : BaseComponentActivity() {
    private val viewModel: SubscriptionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        SubSettingScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            onBackClick = { finish() },
            onAddClick = { startActivity(Intent(this, SubEditActivity::class.java)) },
            onSubUpdate = { viewModel.updateSubscriptions() },
            onEditSub = { subId ->
                startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", subId))
            },
            onRemoveSub = { subId -> removeSub(subId) },
            onShareQRCode = viewModel::shareQRCode,
            onShareClipboard = { url ->
                Utils.setClipboard(this, url)
                toast(getString(R.string.toast_success))
            }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun removeSub(subId: String) {
        viewModel.remove(subId)
    }
}

@Composable
fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    @Suppress("UNUSED_PARAMETER") isLoading: Boolean,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubUpdate: () -> Unit,
    onEditSub: (String) -> Unit,
    onRemoveSub: (String) -> Unit,
    onShareQRCode: (String) -> Unit,
    onShareClipboard: (String) -> Unit
) {
    val subscriptions by viewModel.subsFlow.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<SubscriptionDeleteTarget?>(null) }
    val confirmRemove = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)

    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val qrCodeBitmap by viewModel.qrCode.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.move(from.index, to.index)
    }

    SatraAtmosphere(state = ConnectionAtmosphereState.Idle) {
        SatraGlassProvider {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0),
            ) { innerPadding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SatraGlassIconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back_24dp),
                                contentDescription = stringResource(R.string.satra_back),
                                tint = onGlassPrimary(),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Text(
                            stringResource(R.string.satra_subs_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = onGlassPrimary(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        SatraGlassIconButton(
                            onClick = onAddClick,
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_add_24dp),
                                contentDescription = stringResource(R.string.acc_add_subscription),
                                tint = SatraTokens.TealDeep,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        SatraGlassIconButton(
                            onClick = { showUpdateDialog = true },
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_restore_24dp),
                                contentDescription = stringResource(R.string.acc_update_subscriptions),
                                tint = SatraTokens.TealDeep,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScrollbar(lazyListState),
                        contentPadding = NavigationBarsBottomPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            items = subscriptions,
                            key = { _, item -> item.guid }
                        ) { _, subCache ->
                            ReorderableItem(reorderableState, key = subCache.guid) { isDragging ->
                                ReorderableListItem(
                                    scope = this,
                                    isDragging = isDragging,
                                ) {
                                    SatraGlassCard {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = subCache.subscription.remarks,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = onGlassPrimary(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (subCache.subscription.url.isNotEmpty()) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = subCache.subscription.url,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = onGlassSecondary(),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = Utils.formatTimestamp(subCache.subscription.lastUpdated),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = onGlassSecondary(),
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Row {
                                                    if (subCache.subscription.url.isNotEmpty()) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_share_24dp),
                                                            contentDescription = stringResource(R.string.acc_share_subscription),
                                                            tint = onGlassSecondary(),
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clickable {
                                                                    shareTarget = Pair(
                                                                        subCache.guid,
                                                                        subCache.subscription.url,
                                                                    )
                                                                }
                                                                .padding(6.dp),
                                                        )
                                                    }
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_edit_24dp),
                                                        contentDescription = stringResource(R.string.acc_edit),
                                                        tint = onGlassSecondary(),
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clickable { onEditSub(subCache.guid) }
                                                            .padding(6.dp),
                                                    )
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_delete_24dp),
                                                        contentDescription = stringResource(R.string.acc_delete),
                                                        tint = SatraTokens.Danger,
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clickable {
                                                                if (confirmRemove) {
                                                                    removeTarget = SubscriptionDeleteTarget(
                                                                        subCache.guid,
                                                                        subCache.subscription.remarks,
                                                                    )
                                                                } else {
                                                                    onRemoveSub(subCache.guid)
                                                                }
                                                            }
                                                            .padding(6.dp),
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                SatraGlassToggle(
                                                    checked = subCache.subscription.enabled,
                                                    onCheckedChange = { checked ->
                                                        val updated = subCache.subscription.copy()
                                                        updated.enabled = checked
                                                        viewModel.update(subCache.guid, updated)
                                                    },
                                                    onLabel = stringResource(R.string.satra_settings_on),
                                                    offLabel = stringResource(R.string.satra_settings_off),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (shareTarget != null) {
        val (_, url) = shareTarget!!
        SelectListDialog(
            options = SubscriptionShareAction.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { action ->
                shareTarget = null
                when (action) {
                    SubscriptionShareAction.QRCode -> onShareQRCode(url)
                    SubscriptionShareAction.Clipboard -> onShareClipboard(url)
                }
            },
            onDismiss = { shareTarget = null }
        )
    }

    if (qrCodeBitmap != null) {
        QRCodeDialog(
            bitmap = qrCodeBitmap,
            onDismiss = viewModel::dismissQRCode
        )
    }

    removeTarget?.let { target ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_subscription_group),
            itemName = target.name,
            onConfirm = {
                removeTarget = null
                onRemoveSub(target.guid)
            },
            onDismiss = { removeTarget = null }
        )
    }

    if (showUpdateDialog) {
        var updateSubscription by rememberMmkvBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
        var autoTestAfterUpdateSubscription by rememberMmkvBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)
        var autoRemoveInvalidAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
        var autoSortAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)

        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            text = {
                Column {
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_sub_update),
                        checked = updateSubscription,
                        onCheckedChange = { updateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_test_after_update_subscription),
                        summary = stringResource(R.string.summary_pref_auto_test_after_update_subscription),
                        checked = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoTestAfterUpdateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_remove_invalid_after_test),
                        summary = stringResource(R.string.summary_pref_auto_remove_invalid_after_test),
                        checked = autoRemoveInvalidAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoRemoveInvalidAfterTest = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_sort_after_test),
                        summary = stringResource(R.string.summary_pref_auto_sort_after_test),
                        checked = autoSortAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoSortAfterTest = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    onSubUpdate()
                }) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
