package com.v2ray.ang.ui.usage

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.handler.HybridAppLimitManager
import com.v2ray.ang.handler.UsageMonitorStore
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UsageMonitorUiState(
    val daily: List<Pair<String, Long>> = emptyList(),
    val topApps: List<UsageMonitorStore.AppUsageRow> = emptyList(),
    val peakHours: List<UsageMonitorStore.HourBucket> = emptyList(),
    val hasUsagePermission: Boolean = false,
    val limits: Map<String, HybridAppLimitManager.AppLimit> = emptyMap(),
    val loading: Boolean = true,
)

class UsageMonitorViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(UsageMonitorUiState())
    val uiState: StateFlow<UsageMonitorUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val ctx = getApplication<Application>()
            val data = withContext(Dispatchers.IO) {
                Triple(
                    UsageMonitorStore.dailyBytes(7),
                    UsageMonitorStore.topAppsToday(ctx),
                    UsageMonitorStore.peakHoursToday().take(6),
                )
            }
            _uiState.value = UsageMonitorUiState(
                daily = data.first,
                topApps = data.second,
                peakHours = data.third,
                hasUsagePermission = UsageMonitorStore.hasUsageStatsPermission(ctx),
                limits = HybridAppLimitManager.allLimits(),
                loading = false,
            )
        }
    }

    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun setVolumeLimit(packageName: String, bytes: Long?) {
        HybridAppLimitManager.setVolumeLimit(packageName, bytes)
        refresh()
    }

    fun setTimeLimit(packageName: String, ms: Long?) {
        HybridAppLimitManager.setTimeLimit(packageName, ms)
        refresh()
    }

    fun clearLimit(packageName: String) {
        HybridAppLimitManager.clearLimit(packageName)
        refresh()
    }
}
