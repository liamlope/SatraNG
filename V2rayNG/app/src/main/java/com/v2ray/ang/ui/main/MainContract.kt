package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget

/** Locale-neutral state formatted only when it reaches the main UI. */
sealed interface MainStatus {
    data object Disconnected : MainStatus
    data object Connected : MainStatus
    data object Testing : MainStatus
    data class TestProgress(val progress: String) : MainStatus
    data class ConnectionTest(val result: ConnectionTestResult) : MainStatus
}

/** Drives the hero connect button; richer than a plain running flag. */
enum class ConnectPhase { Idle, Connecting, Connected, Disconnecting, Failed }

enum class ServerSort { Default, Ping, Name }

/**
 * Main UI state
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val status: MainStatus = MainStatus.Disconnected,
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val shareQRCodeBitmap: android.graphics.Bitmap? = null,
    /** True while Auto-connect is retrying after an unexpected drop. */
    val autoReconnecting: Boolean = false,
    val phase: ConnectPhase = ConnectPhase.Idle,
    /** Short reason shown under the button when [phase] is [ConnectPhase.Failed]. */
    val errorMessage: String? = null,
    val sort: ServerSort = ServerSort.Default,
    val favoritesOnly: Boolean = false,
    /** Servers deleted with an undo window still open; hidden from lists, removed when it closes. */
    val hiddenGuids: Set<String> = emptySet(),
    /** Name of the config just deleted (drives the "Undo" snackbar). */
    val undoName: String? = null,
    val selectedRow: ServerRowUiModel? = null,
    val quota: QuotaUi? = null,
)

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object TestCurrentServer : MainAction
    data object TestAllServers : MainAction
    data object TestRealAllServers : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data object ExportAll : MainAction

    data object ImportQRcode : MainAction
    data object ImportClipboard : MainAction
    data object ImportConfigLocal : MainAction
    data class ImportManually(val type: Int) : MainAction
    data object RestartService : MainAction
    data object LocateSelectedServer : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class EditServer(val guid: String, val profile: com.v2ray.ang.dto.entities.ProfileItem) : MainAction
    data class Search(val query: String) : MainAction
    data class ShareQRCode(val guid: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data object DismissQRCodeDialog : MainAction

    data class ImportBatchConfig(val configText: String) : MainAction

    data object LocateHandled : MainAction

    data class ToggleFavorite(val guid: String) : MainAction
    data class SetSort(val sort: ServerSort) : MainAction
    data class SetFavoritesOnly(val enabled: Boolean) : MainAction
    data class DeleteWithUndo(val guid: String) : MainAction
    data object UndoDelete : MainAction
    data object UndoHandled : MainAction
    /** Re-measure the exit country of a single config (and its latency). */
    data class RecheckServer(val guid: String) : MainAction
}

/** Screens opened from the tab UI as separate activities. */
enum class MainDestination {
    PerAppProxy, Routing, UserAssets, Settings, Logcat, CheckUpdate, BackupRestore, About, Promotion,
}

/** Quota of the subscription the active config belongs to (Home card). */
data class QuotaUi(
    val name: String,
    val usedBytes: Long,
    val totalBytes: Long,
    /** Epoch millis, 0 when the subscription never expires. */
    val expireMs: Long,
) {
    val fraction: Float? get() = if (totalBytes > 0L) (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else null
}
