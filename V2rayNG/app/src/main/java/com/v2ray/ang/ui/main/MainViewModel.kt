package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.AutoConnectController
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.handler.LiveUsage
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.UsagePeriods
import com.v2ray.ang.handler.UsageStore
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

private fun applyTestDelayResults(
    servers: List<ServersCache>,
    updates: Map<String, Long>,
): List<ServersCache> = servers.map { server ->
    val delayMillis = updates[server.guid]
    if (delayMillis == null || delayMillis == server.testDelayMillis) {
        server
    } else {
        server.copy(testDelayMillis = delayMillis)
    }
}

private fun applyTestDelayResultsToRows(
    rows: List<ServerRowUiModel>,
    updates: Map<String, Long>,
): List<ServerRowUiModel> = rows.map { row ->
    val delayMillis = updates[row.guid]
    if (delayMillis == null || delayMillis == row.testDelayMillis) {
        row
    } else {
        row.copy(testDelayMillis = delayMillis)
    }
}

/** Live numbers for Home: current speeds, a rolling history for the graph, and today's total. */
data class LiveUiState(
    val running: Boolean = false,
    val upBps: Long = 0L,
    val downBps: Long = 0L,
    val sessionStartMs: Long = 0L,
    val sessionBytes: Long = 0L,
    val todayBytes: Long = 0L,
    val history: List<Float> = emptyList(),
) {
    fun next(live: LiveUsage): LiveUiState {
        val today = UsageStore.day(UsagePeriods.dayKey(System.currentTimeMillis())).totalBytes
        if (!live.running) return LiveUiState(todayBytes = today)
        val point = (live.upBps + live.downBps).toFloat()
        return LiveUiState(
            running = true,
            upBps = live.upBps,
            downBps = live.downBps,
            sessionStartMs = live.sessionStartMs,
            sessionBytes = live.sessionUp + live.sessionDown,
            todayBytes = today,
            history = (history + point).takeLast(HISTORY_POINTS),
        )
    }

    companion object { const val HISTORY_POINTS = 48 }
}

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    // ---------- UI state ----------
    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer(),
            confirmRemove = dataSource.getConfirmRemove(),
            doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _autoReconnectRequest = MutableStateFlow(0)
    /** Increments when Auto policy wants Activity to restart the service. */
    val autoReconnectRequest: StateFlow<Int> = _autoReconnectRequest.asStateFlow()

    // ---------- Live stats (published by the daemon's UsageSampler) ----------
    val liveState: StateFlow<LiveUiState> = flow {
        var n = 0
        while (true) {
            emit(UsageStore.live())
            if (++n % 10 == 0 && uiState.value.isRunning) refreshSelectedRow()
            delay(1000)
        }
    }
        .flowOn(Dispatchers.IO)
        .scan(LiveUiState()) { acc, live -> acc.next(live) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    // ---------- Connect phase ----------
    private var phaseJob: Job? = null
    private val geoDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val geoInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var pendingDeleteJob: Job? = null
    private var pendingDeleteGuid: String? = null

    /** The user pressed connect: animate immediately, give the core a bounded time to come up. */
    fun beginConnect() {
        phaseJob?.cancel()
        _uiState.update { it.copy(phase = ConnectPhase.Connecting, errorMessage = null) }
        phaseJob = viewModelScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (uiState.value.phase == ConnectPhase.Connecting) {
                LauncherManager.stopService(getApplication())
                failConnect(dataSource.getString(R.string.sx_connect_timeout))
            }
        }
    }

    /** VPN permission refused or the start was abandoned. */
    fun cancelConnect() {
        phaseJob?.cancel()
        _uiState.update { it.copy(phase = if (it.isRunning) ConnectPhase.Connected else ConnectPhase.Idle) }
    }

    fun beginDisconnect() {
        phaseJob?.cancel()
        _uiState.update { it.copy(phase = ConnectPhase.Disconnecting, errorMessage = null) }
        phaseJob = viewModelScope.launch {
            delay(DISCONNECT_TIMEOUT_MS)
            if (uiState.value.phase == ConnectPhase.Disconnecting) {
                _uiState.update { it.copy(phase = if (it.isRunning) ConnectPhase.Connected else ConnectPhase.Idle) }
            }
        }
    }

    private fun failConnect(message: String?) {
        phaseJob?.cancel()
        _uiState.update {
            it.copy(
                phase = ConnectPhase.Failed,
                errorMessage = message?.takeIf { m -> m.isNotBlank() },
                autoReconnecting = false,
            )
        }
        phaseJob = viewModelScope.launch {
            delay(FAILED_SHOWN_MS)
            if (uiState.value.phase == ConnectPhase.Failed) {
                _uiState.update { it.copy(phase = ConnectPhase.Idle) }
            }
        }
    }

    // ---------- Keyword filtering ----------
    @Volatile
    private var keywordFilter: String = ""
    private var filterJob: Job? = null

    // ---------- Groups & cache ----------
    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupUiFlows = ConcurrentHashMap<String, MutableStateFlow<ServerGroupUiState>>()
    private val groupServerFlows = ConcurrentHashMap<String, StateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val serverOrderPersistenceJobs = mutableMapOf<String, Job>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null
    private var reloadJob: Job? = null
    private var testResultFlushJob: Job? = null
    private val pendingTestResults = linkedMapOf<String, Long>()

    private val testRequests = MainTestRequests()
    private var bulkTestJob: Job? = null

    private val initialPageReady = CompletableDeferred<Unit>()

    // ---------- Service events ----------
    init {
        collectServiceEvents()
        setupGroupTab()
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    @Volatile private var expectUserStop = false

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
            MainServiceEvent.StateNotRunning -> {
                val wasRunning = uiState.value.isRunning
                updateRunningState(false, clearTestingText = false)
                if (wasRunning) maybeScheduleAutoReconnect()
            }
            MainServiceEvent.StateStartSuccess -> {
                updateRunningState(true)
                reconnectAttempt = 0
                expectUserStop = false
                MmkvManager.encodeSettings(AppConfig.PREF_USER_REQUESTED_STOP, false)
                _uiState.update { it.copy(autoReconnecting = false) }
                refreshExitGeoForSelectedServer()
            }

            is MainServiceEvent.StateStartFailure -> {
                updateRunningState(false)
                failConnect(event.message?.takeIf { it.isNotBlank() } ?: dataSource.getString(R.string.toast_services_failure))
                maybeScheduleAutoReconnect()
            }

            MainServiceEvent.StateStopSuccess -> {
                updateRunningState(false)
                maybeScheduleAutoReconnect()
            }
            is MainServiceEvent.MeasureDelayResult -> {
                if (!uiState.value.isRunning || !testRequests.completeCurrent(event.requestId)) return
                _uiState.update { it.copy(isTesting = testRequests.isTesting, status = MainStatus.ConnectionTest(event.result)) }
            }

            is MainServiceEvent.MeasureConfigSuccess -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                queueTestResult(event.result, request)
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                if (event.requestId == testRequests.bulk?.id) {
                    _uiState.update { it.copy(status = MainStatus.TestProgress(event.progress)) }
                }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                val scheduledFlush = testResultFlushJob
                testResultFlushJob = viewModelScope.launch {
                    scheduledFlush?.cancelAndJoin()
                    if (testRequests.bulk?.id != request.id) return@launch
                    flushPendingTestResults(request)
                    onTestsFinished(request.id)
                }
            }

            is MainServiceEvent.MeasureDelayCancelled -> {
                if (testRequests.completeCurrent(event.requestId)) resetTestStatus()
            }

            is MainServiceEvent.MeasureConfigCancelled -> {
                if (testRequests.completeBulk(event.requestId) != null) {
                    cancelPendingTestResults()
                    resetTestStatus()
                }
            }
        }
    }

    private fun queueTestResult(result: RealPingResult, request: MainTestRequests.Bulk) {
        pendingTestResults[result.guid] = result.delayMillis
        if (testResultFlushJob?.isActive == true) return

        testResultFlushJob = viewModelScope.launch {
            while (pendingTestResults.isNotEmpty()) {
                delay(TEST_RESULT_FLUSH_INTERVAL_MS)
                flushPendingTestResults(request)
            }
        }
    }

    private suspend fun flushPendingTestResults(request: MainTestRequests.Bulk) {
        if (pendingTestResults.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return

        val updates = cacheMutex.withLock {
            val drained = pendingTestResults.toMap()
            pendingTestResults.clear()
            groupDataCache[request.groupId]?.let { cached ->
                groupDataCache[request.groupId] = applyTestDelayResults(cached, drained)
            }
            drained
        }
        if (updates.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return
        mutableServerGroupState(request.groupId).update { current ->
            current.copy(
                servers = applyTestDelayResults(current.servers, updates),
                rows = applyTestDelayResultsToRows(current.rows, updates),
            )
        }
    }

    internal fun formatStatus(status: MainStatus): String = when (status) {
        MainStatus.Disconnected -> dataSource.getString(R.string.connection_not_connected)
        MainStatus.Connected -> dataSource.getString(R.string.connection_connected)
        MainStatus.Testing -> dataSource.getString(R.string.connection_test_testing)
        is MainStatus.TestProgress -> dataSource.getString(
            R.string.connection_running_task_left,
            status.progress
        )

        is MainStatus.ConnectionTest -> formatConnectionTestResult(status.result)
    }

    private fun formatConnectionTestResult(result: ConnectionTestResult): String {
        val status = if (result.delayMillis >= 0) {
            val delay = dataSource.getString(R.string.server_test_delay_value, result.delayMillis)
            dataSource.getString(R.string.connection_test_available, delay)
        } else {
            val detail = result.errorMessage.ifBlank {
                dataSource.getString(R.string.connection_test_empty_message)
            }
            dataSource.getString(R.string.connection_test_error, detail)
        }

        if (result.delayMillis < 0 || (result.country == null && result.ipAddress == null)) {
            return status
        }

        val unknown = dataSource.getString(R.string.value_unknown)
        return "$status\n(${result.country ?: unknown}) ${result.ipAddress ?: unknown}"
    }

    // ---------- Public state accessors ----------
    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupServerFlows.computeIfAbsent(groupId) {
            val groupState = mutableServerGroupState(groupId)
            groupState
                .map { it.servers }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    initialValue = groupState.value.servers,
                )
        }

    internal fun serverGroupState(groupId: String): StateFlow<ServerGroupUiState> =
        mutableServerGroupState(groupId).asStateFlow()

    private fun mutableServerGroupState(groupId: String): MutableStateFlow<ServerGroupUiState> =
        groupUiFlows.computeIfAbsent(groupId) { MutableStateFlow(ServerGroupUiState()) }

    private fun currentServers(): List<ServersCache> =
        mutableServerGroupState(uiState.value.selectedGroupId).value.servers

    // ---------- Action handler ----------
    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing(true)
            MainAction.TestRealAllServers -> testAllRealPing()
            MainAction.CancelTesting -> cancelAllPing()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> sortByTestResultsAsync()
            MainAction.UpdateSubscriptions -> importConfigViaSub()
            MainAction.ExportAll -> exportAllAsync()
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> updateSelectedGuid(action.guid)
            is MainAction.RemoveServer -> removeServerAndRefresh(action.guid)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            MainAction.LocateHandled -> consumeLocateTarget()
            is MainAction.ToggleFavorite -> toggleFavorite(action.guid)
            is MainAction.SetSort -> {
                _uiState.update { it.copy(sort = action.sort) }
                reapplyViewOptions()
            }
            is MainAction.SetFavoritesOnly -> {
                _uiState.update { it.copy(favoritesOnly = action.enabled) }
                reapplyViewOptions()
            }
            is MainAction.DeleteWithUndo -> deleteWithUndo(action.guid)
            MainAction.UndoDelete -> undoDelete()
            MainAction.UndoHandled -> _uiState.update { it.copy(undoName = null) }
            is MainAction.RecheckServer -> recheckServer(action.guid)
            is MainAction.ShareQRCode -> {
                val bitmap = dataSource.share2QRCode(action.guid)
                _uiState.update { it.copy(shareQRCodeBitmap = bitmap) }
            }

            MainAction.DismissQRCodeDialog -> {
                _uiState.update { it.copy(shareQRCodeBitmap = null) }
            }

            MainAction.ToggleService,
            MainAction.TestCurrentServer,
            MainAction.ImportQRcode,
            MainAction.ImportClipboard,
            MainAction.ImportConfigLocal,
            is MainAction.ImportManually,
            MainAction.RestartService,
            MainAction.LocateSelectedServer,
            is MainAction.EditServer,
            is MainAction.ShareClipboard,
            is MainAction.ShareFullContent -> {
                // Handled by Activity via its onAction lambda
            }
        }
    }

    // ---------- Initialization ----------
    fun initialize() {
        viewModelScope.launch(preloadDispatcher) {
            try {
                initialPageReady.await()
                delay(32)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Main background initialization failed", error)
            }
        }
    }

    fun refreshUiSettings() {
        _uiState.update {
            it.copy(
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
            )
        }
    }

    // ---------- Group & server loading ----------
    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            val affiliation = dataSource.decodeAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis = affiliation?.testDelayMillis ?: 0L
            )
        }

    private suspend fun loadGroup(
        groupId: String,
        forceRefresh: Boolean = false
    ): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { cache ->
            val profile = cache.profile
            profile.remarks.matchesPattern(regex, keyword) ||
                    profile.description.orEmpty().matchesPattern(regex, keyword) ||
                    profile.server.orEmpty().matchesPattern(regex, keyword) ||
                    profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        val filteredServers = applyKeywordFilter(servers)
        val rows = buildServerRows(groupId, filteredServers)
        val state = uiState.value
        val order = rows.indices
            .filter { !state.favoritesOnly || rows[it].favorite }
            .sortedWith(rowComparator(rows, state.sort))
        mutableServerGroupState(groupId).value = ServerGroupUiState(
            servers = order.map { filteredServers[it] },
            rows = order.map { rows[it] },
        )
        scheduleGeoFill(groupId, order.map { rows[it] })
        refreshSelectedRow()
    }

    private fun rowComparator(rows: List<ServerRowUiModel>, sort: ServerSort): Comparator<Int> {
        // Starred configs are always pinned to the top; the chosen sort orders each block.
        val byFavorite = compareByDescending<Int> { rows[it].favorite }
        return when (sort) {
            ServerSort.Default -> byFavorite
            ServerSort.Ping -> byFavorite.thenBy {
                val d = rows[it].testDelayMillis
                if (d > 0L) d else Long.MAX_VALUE
            }
            ServerSort.Name -> byFavorite.thenBy { rows[it].remarks.lowercase() }
        }
    }

    /** Fills in missing flags lazily: server address -> IP -> country. Never blocks the UI. */
    private fun scheduleGeoFill(groupId: String, rows: List<ServerRowUiModel>) {
        val todo = rows.filter { it.countryCode == null && it.guid !in geoInFlight }.take(GEO_FILL_BATCH)
        if (todo.isEmpty()) return
        viewModelScope.launch(geoDispatcher) {
            val now = System.currentTimeMillis()
            for (row in todo) {
                ensureActive()
                val aff = MmkvManager.decodeServerAffiliationInfo(row.guid)
                if (!GeoIpResolver.needsLookup(aff, now)) continue
                if (!geoInFlight.add(row.guid)) continue
                try {
                    val port = if (uiState.value.isRunning) SettingsManager.getHttpPort() else 0
                    val result = GeoIpResolver.resolveHostSoft(row.guid, row.profile, port)
                    if (result.countryCode != null) patchRowGeo(groupId, row.guid, result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtil.w(AppConfig.TAG, "Geo fill failed: ${e.message}")
                } finally {
                    geoInFlight.remove(row.guid)
                }
            }
        }
    }

    private fun patchRowGeo(groupId: String, guid: String, geo: GeoIpResolver.GeoResult) {
        mutableServerGroupState(groupId).update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row.guid != guid) row
                    else row.copy(countryCode = geo.countryCode, countryName = geo.countryName, exitVerified = geo.exitVerified)
                },
            )
        }
        if (guid == uiState.value.selectedGuid) refreshSelectedRow()
    }

    /** Rebuilds the row for the active config (Home hero card). */
    fun refreshSelectedRow() {
        viewModelScope.launch(ioDispatcher) {
            val guid = dataSource.getSelectServer()
            val row = guid?.let { g ->
                val profile = dataSource.decodeServerConfig(g) ?: return@let null
                val aff = dataSource.decodeAffiliationInfo(g)
                buildServerRowUiModel(
                    ServersCache(g, profile.copy(), aff?.testDelayMillis ?: 0L),
                    subscriptionRemarks = dataSource.getSubscriptionItem(profile.subscriptionId)?.remarks.orEmpty(),
                )
            }
            val quota = guid?.let { g ->
                val subId = dataSource.decodeServerConfig(g)?.subscriptionId?.takeIf { it.isNotBlank() } ?: return@let null
                val sub = dataSource.getSubscriptionItem(subId) ?: return@let null
                if (sub.total <= 0L && sub.expire <= 0L) return@let null
                val used = com.v2ray.ang.handler.HybridUsageTracker.displayFor(subId, sub)
                QuotaUi(
                    name = sub.remarks,
                    usedBytes = used.displayUsed,
                    totalBytes = sub.total,
                    expireMs = if (sub.expire <= 0L) 0L else if (sub.expire > 10_000_000_000L) sub.expire else sub.expire * 1000L,
                )
            }
            _uiState.update { it.copy(selectedRow = row, quota = quota) }
        }
    }

    private fun reapplyViewOptions() {
        viewModelScope.launch(defaultDispatcher) {
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            snapshot.forEach { (groupId, servers) -> updateGroupUi(groupId, servers) }
        }
    }

    fun toggleFavorite(guid: String) {
        viewModelScope.launch(ioDispatcher) {
            MmkvManager.setServerFavorite(guid, !MmkvManager.isServerFavorite(guid))
            reapplyViewOptions()
        }
    }

    fun deleteWithUndo(guid: String) {
        if (guid == uiState.value.selectedGuid && uiState.value.isRunning) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        commitPendingDelete()
        val name = dataSource.decodeServerConfig(guid)?.remarks.orEmpty()
        pendingDeleteGuid = guid
        _uiState.update { it.copy(hiddenGuids = it.hiddenGuids + guid, undoName = name.ifBlank { "·" }) }
        pendingDeleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            commitPendingDelete()
        }
    }

    fun undoDelete() {
        val guid = pendingDeleteGuid ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteGuid = null
        pendingDeleteJob = null
        _uiState.update { it.copy(hiddenGuids = it.hiddenGuids - guid, undoName = null) }
    }

    /** Actually removes a config whose undo window has closed (or is being superseded). */
    private fun commitPendingDelete() {
        val guid = pendingDeleteGuid ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteGuid = null
        pendingDeleteJob = null
        viewModelScope.launch(ioDispatcher) {
            if (guid == uiState.value.selectedGuid && uiState.value.isRunning) {
                _uiState.update { it.copy(hiddenGuids = it.hiddenGuids - guid, undoName = null) }
                return@launch
            }
            dataSource.removeServer(guid)
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
            _uiState.update { it.copy(hiddenGuids = it.hiddenGuids - guid, undoName = null) }
        }
    }

    fun recheckServer(guid: String) {
        // Re-ping one config; the test worker also refreshes its exit country.
        val request = testRequests.beginBulk(uiState.value.selectedGroupId)
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        viewModelScope.launch {
            withContext(ioDispatcher) { dataSource.clearAllTestDelayResults(listOf(guid)) }
            dataSource.sendMsg2TestService(
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = "",
                    serverGuids = listOf(guid),
                    onlyTcp = false,
                ),
                request.id,
            )
        }
    }

    private fun buildServerRows(groupId: String, servers: List<ServersCache>): List<ServerRowUiModel> {
        val subscriptionRemarks = if (groupId.isEmpty()) {
            servers.asSequence()
                .map { it.profile.subscriptionId }
                .filter { it.isNotEmpty() }
                .distinct()
                .associateWith { subscriptionId ->
                    dataSource.getSubscriptionItem(subscriptionId)?.remarks.orEmpty()
                }
        } else {
            emptyMap()
        }
        return servers.map { server ->
            buildServerRowUiModel(
                server = server,
                subscriptionRemarks = subscriptionRemarks[server.profile.subscriptionId].orEmpty()
            )
        }
    }

    fun getSubscriptions(): List<SubscriptionCache> = dataSource.getSubscriptions()

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) {
            dataSource.setSelectedSubscriptionId(resolved)
        }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance
            if (right in groups.indices) result += groups[right].id
            if (left in groups.indices) result += groups[left].id
        }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()

        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock { groupDataCache.clear() }
                }
                val groups = dataSource.getSubscriptions().map {
                    GroupMapItem(id = it.guid, remarks = it.subscription.remarks)
                }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupUiFlows.keys.removeAll { it !in validIds }
                groupServerFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid = dataSource.getSelectServer(),
                    )
                }
                groups.forEach { mutableServerGroupState(it.id) }

                if (groups.isEmpty()) {
                    cacheMutex.withLock { groupDataCache.clear() }
                    return@launch
                }

                val selectedServers = loadGroup(selectedGroup, forceRefresh)
                updateGroupUi(selectedGroup, selectedServers)

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex =
                    groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()
                        delay(32)
                        val servers = loadGroup(groupId, forceRefresh)
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error)
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also { setupGroupJob = it }
    }

    // ---------- Business actions (coroutine-based) ----------
    private fun importBatchConfig(configText: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.importBatchConfig(
                        configText, uiState.value.selectedGroupId, true
                    )
                    when {
                        count > 0 -> {
                            toast(dataSource.getString(R.string.title_import_config_count, count))
                            setupGroupTab(forceRefresh = true)
                        }

                        countSub > 0 -> setupGroupTab(forceRefresh = true)
                        else -> toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importConfigViaSub() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    // Always refresh every enabled subscription so Home quota cards
                    // (usage/expiry headers) update even when the selected tab is empty/"All".
                    val result = dataSource.updateConfigViaSubAll()
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount == 0 ->
                            toast(
                                getQuantityString(
                                    R.plurals.title_update_config_count,
                                    result.configCount.coerceAtLeast(result.successCount),
                                    result.configCount.coerceAtLeast(result.successCount),
                                )
                            )

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.successCount > 0 || result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun exportAllAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val groupId = uiState.value.selectedGroupId
                    val list = if (groupId.isEmpty() && keywordFilter.isEmpty()) {
                        dataSource.getServerGuidList("")
                    } else {
                        currentServers().map { it.guid }
                    }
                    val ret = dataSource.shareNonCustomConfigsToClipboard(list)
                    if (ret > 0) {
                        toast(dataSource.getString(R.string.title_export_config_count, ret))
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Export failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeAllServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count =
                        if (uiState.value.selectedGroupId.isEmpty() && keywordFilter.isEmpty()) {
                            dataSource.removeAllServer()
                        } else {
                            val guids = currentServers().map { it.guid }
                            guids.forEach { dataSource.removeServer(it) }
                            guids.size
                        }
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                    }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete all failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeDuplicateServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val seen = HashSet<ProfileItem>()
                    val duplicates = ArrayList<String>()
                    currentServers().forEach { server ->
                        val profile = server.profile
                        if (!profile.configType.isComplexType()) {
                            val identity = profile.duplicateIdentity()
                            if (!seen.add(identity)) duplicates += server.guid
                        }
                    }
                    duplicates.forEach { dataSource.removeServer(it) }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_duplicate_config_count, duplicates.size))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count = removeInvalidServerInternal()
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                        setupGroupTab(forceRefresh = true)
                    }
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete invalid failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerInternal(): Int {
        val visibleServersOnly =
            uiState.value.selectedGroupId.isNotEmpty() || keywordFilter.isNotBlank()
        return if (visibleServersOnly) {
            currentServers().sumOf { server ->
                dataSource.removeInvalidServerByGuid(server.guid)
            }
        } else {
            dataSource.removeInvalidServersInGroup("")
        }
    }

    private fun sortByTestResultsAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    sortByTestResultsInternal()
                    cacheMutex.withLock { groupDataCache.clear() }
                    setupGroupTab(forceRefresh = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Sort by test results failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun sortByTestResultsInternal() {
        val subs = if (uiState.value.selectedGroupId.isEmpty()) {
            dataSource.getSubsList()
        } else {
            listOf(uiState.value.selectedGroupId)
        }
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return
        mutableServerGroupState(id)
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id) }
        }
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                updateGroupUi(id, loadGroup(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error)
            }
        }
    }

    fun reloadServerList() {
        val groupId = uiState.value.selectedGroupId
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(preloadDispatcher) {
            val selected = uiState.value.selectedGroupId
            val order = buildList {
                if (selected in groupIds) add(selected)
                addAll(groupIds.filter { it != selected })
            }
            order.forEachIndexed { index, groupId ->
                ensureActive()
                if (index > 0) delay(32)
                updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) {
            delay(300)
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            ensureActive()
            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    fun updateSelectedGuid(guid: String) {
        dataSource.setSelectServer(guid)
        _uiState.update { it.copy(selectedGuid = guid) }
        refreshSelectedRow()
    }

    fun refreshSelectedGuid() {
        _uiState.update { it.copy(selectedGuid = dataSource.getSelectServer()) }
    }

    fun removeServerAndRefresh(guid: String) {
        if (guid == uiState.value.selectedGuid) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            dataSource.removeServer(guid)
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
        }
    }

    fun moveServer(groupId: String, fromPosition: Int, toPosition: Int) {
        val groupState = mutableServerGroupState(groupId).value
        val servers = groupState.servers.toMutableList()
        if (!servers.moveItem(fromPosition, toPosition)) return
        val rows = groupState.rows.toMutableList()
        rows.moveItem(fromPosition, toPosition)
        val guids = servers.map { it.guid }
        mutableServerGroupState(groupId).value = ServerGroupUiState(servers, rows)
        // A drag emits several moves; serialize writes so an older order cannot overwrite a newer one.
        val previousPersistenceJob = serverOrderPersistenceJobs[groupId]
        serverOrderPersistenceJobs[groupId] = viewModelScope.launch(ioDispatcher) {
            previousPersistenceJob?.join()
            dataSource.encodeServerList(guids, groupId)
            cacheMutex.withLock { groupDataCache[groupId] = servers }
        }
    }

    // ---------- Testing ----------
    fun cancelAllPing() {
        bulkTestJob?.cancel()
        bulkTestJob = null
        testRequests.cancelBulk()
        testRequests.invalidateCurrent()
        cancelPendingTestResults()
        resetTestStatus()
        dataSource.cancelAllPing()
    }

    private fun resetTestStatus() {
        _uiState.update {
            it.copy(
                isTesting = testRequests.isTesting,
                status = if (testRequests.isTesting) MainStatus.Testing
                else if (it.isRunning) MainStatus.Connected else MainStatus.Disconnected
            )
        }
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        cancelAllPing()
        val groupId = uiState.value.selectedGroupId
        val selectedServers = currentServers()
        val crossGroup = selectedServers.isEmpty()
        val servers = if (crossGroup) {
            // Home pull-to-refresh / empty selected group: ping every loaded group.
            uiState.value.groups.flatMap { group ->
                mutableServerGroupState(group.id).value.servers
            }
        } else {
            selectedServers
        }
        if (servers.isEmpty()) {
            return
        }
        val serverGuids = servers.map { it.guid }
        if (!crossGroup) {
            mutableServerGroupState(groupId).update { current ->
                current.copy(
                    servers = current.servers.map { server ->
                        if (server.testDelayMillis == 0L) server
                        else server.copy(testDelayMillis = 0L)
                    },
                    rows = current.rows.map { row ->
                        if (row.testDelayMillis == 0L) row
                        else row.copy(testDelayMillis = 0L)
                    }
                )
            }
        }
        val request = testRequests.beginBulk(groupId)
        val message = TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            subscriptionId = if (crossGroup) "" else groupId,
            // Explicit guids when filtering or when spanning every group.
            serverGuids = if (keywordFilter.isNotEmpty() || crossGroup) serverGuids else emptyList(),
            onlyTcp = onlyTcp
        )
        _uiState.update {
            it.copy(
                isTesting = true,
                status = MainStatus.Testing
            )
        }
        bulkTestJob = viewModelScope.launch {
            withContext(ioDispatcher) {
                dataSource.clearAllTestDelayResults(serverGuids)
                val resetGuids = serverGuids.toHashSet()
                cacheMutex.withLock {
                    groupDataCache[groupId]?.let { cached ->
                        groupDataCache[groupId] = cached.map { server ->
                            if (server.guid !in resetGuids || server.testDelayMillis == 0L) server
                            else server.copy(testDelayMillis = 0L)
                        }
                    }
                }
            }
            dataSource.sendMsg2TestService(message, request.id)
        }
    }

    private fun cancelPendingTestResults() {
        testResultFlushJob?.cancel()
        testResultFlushJob = null
        pendingTestResults.clear()
    }

    fun testCurrentServerRealPing() {
        if (!uiState.value.isRunning) return
        val requestId = testRequests.beginCurrent()
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        dataSource.testCurrentServerRealPing(requestId)
    }

    private fun onTestsFinished(requestId: String) {
        if (testRequests.completeBulk(requestId) == null) return
        resetTestStatus()
        val groupId = uiState.value.selectedGroupId
        viewModelScope.launch(ioDispatcher) {
            // The test worker already measured each answering config's exit country.
            if (uiState.value.isRunning) {
                refreshExitGeoForSelectedServer(delayMs = 200L)
            }
            cacheMutex.withLock { groupDataCache.clear() }
            reloadAllGroups(_uiState.value.groups.map { it.id })
        }
    }

    fun triggerLocateSelectedServer() {
        val selected = dataSource.getSelectServer() ?: return
        val profile = dataSource.decodeServerConfig(selected) ?: return
        val groupId = profile.subscriptionId
        if (_uiState.value.groups.none { it.id == groupId }) return
        viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId))
            if (_uiState.value.selectedGroupId != groupId) {
                dataSource.setSelectedSubscriptionId(groupId)
            }
            val target = LocateTarget(groupId, selected)
            _uiState.update {
                it.copy(selectedGroupId = groupId, locateTarget = target)
            }
        }
    }

    private fun consumeLocateTarget() {
        _uiState.update { it.copy(locateTarget = null) }
    }

    // ---------- Auto-connect / user stop ----------
    fun isAutoConnectEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_CONNECT, true)

    fun setAutoConnectEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_CONNECT, enabled)
    }

    fun markUserRequestedStop() {
        expectUserStop = true
        reconnectJob?.cancel()
        reconnectAttempt = 0
        MmkvManager.encodeSettings(AppConfig.PREF_USER_REQUESTED_STOP, true)
    }

    fun markUserRequestedStart() {
        expectUserStop = false
        reconnectJob?.cancel()
        reconnectAttempt = 0
        MmkvManager.encodeSettings(AppConfig.PREF_USER_REQUESTED_STOP, false)
    }

    fun prepareConnectGuid(): String? {
        val groupId = uiState.value.selectedGroupId
        val candidates = if (groupId.isNotBlank()) {
            MmkvManager.decodeServerList(groupId)
        } else {
            uiState.value.groups.flatMap { MmkvManager.decodeServerList(it.id) }
        }
        val now = System.currentTimeMillis()
        val samples = candidates.mapNotNull { guid ->
            val aff = MmkvManager.decodeServerAffiliationInfo(guid) ?: return@mapNotNull null
            val delay = aff.testDelayMillis
            if (delay <= 0L) return@mapNotNull null
            // Prefer geoUpdatedAt as a proxy for last measurement age when available.
            val measuredAt = aff.geoUpdatedAt.takeIf { it > 0L } ?: now
            AutoConnectController.PingSample(guid, delay, measuredAt)
        }
        val fresh = samples.any { now - it.measuredAtMs <= AutoConnectController.PING_FRESH_MS }
        if (isAutoConnectEnabled() && !fresh && candidates.isNotEmpty()) {
            // Kick a real-ping pass so the next Best selection has fresh data.
            testAllRealPing()
        }
        val best = AutoConnectController.selectBestGuid(
            candidates = candidates,
            samples = samples,
            nowMs = now,
            selectedGuid = uiState.value.selectedGuid,
            autoConnect = isAutoConnectEnabled(),
        )
        if (best != null && best != uiState.value.selectedGuid) {
            updateSelectedGuid(best)
        }
        return best ?: uiState.value.selectedGuid
    }

    private fun maybeScheduleAutoReconnect() {
        val userStop = expectUserStop ||
            MmkvManager.decodeSettingsBool(AppConfig.PREF_USER_REQUESTED_STOP, false)
        if (!AutoConnectController.shouldReconnect(
                autoConnect = isAutoConnectEnabled(),
                userRequestedStop = userStop,
                unexpectedDrop = true,
            )
        ) {
            return
        }
        val delayMs = AutoConnectController.backoffMsForAttempt(reconnectAttempt) ?: return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            _uiState.update { it.copy(autoReconnecting = true, status = MainStatus.Testing, phase = ConnectPhase.Connecting) }
            delay(delayMs)
            reconnectAttempt++
            prepareConnectGuid()
            _autoReconnectRequest.update { it + 1 }
        }
    }

    // ---------- Running state ----------
    private fun updateRunningState(running: Boolean, clearTestingText: Boolean = true) {
        if (!running || clearTestingText) testRequests.invalidateCurrent()
        if (running) phaseJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isRunning = running,
                isTesting = testRequests.isTesting,
                status = if (!clearTestingText && state.isRunning == running) state.status
                else if (running) MainStatus.Connected else MainStatus.Disconnected,
                phase = when {
                    running -> ConnectPhase.Connected
                    state.phase == ConnectPhase.Connecting || state.phase == ConnectPhase.Failed -> state.phase
                    else -> ConnectPhase.Idle
                },
                errorMessage = if (running) null else state.errorMessage,
            )
        }
    }

    /**
     * After connect, resolve the public exit IP through the local proxy and store country
     * for the selected profile (tunnel configs must not use the inbound server IP).
     */
    /** Resolve exit-IP flag after connect, or on resume while the tunnel is up. */
    fun refreshExitGeoForSelectedServer(delayMs: Long = 900L) {
        viewModelScope.launch(Dispatchers.IO) {
            if (delayMs > 0L) delay(delayMs)
            if (!uiState.value.isRunning) return@launch
            val guid = dataSource.getSelectServer()?.takeIf { it.isNotBlank() } ?: return@launch
            val result = GeoIpResolver.resolveExitAfterConnect(guid)
            if (result.exitVerified) {
                reloadServerList()
            }
        }
    }

    override fun onCleared() {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        reloadJob?.cancel()
        filterJob?.cancel()
        reconnectJob?.cancel()
        cancelAllPing()
        dataSource.close()
        super.onCleared()
    }

    // ---------- Factory ----------
    class Factory(private val application: Application, private val dataSource: MainDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val DISCONNECT_TIMEOUT_MS = 6_000L
        const val FAILED_SHOWN_MS = 3_600L
        const val UNDO_WINDOW_MS = 5_000L
        const val GEO_FILL_BATCH = 80
        const val TEST_RESULT_FLUSH_INTERVAL_MS = 500L
    }
}
