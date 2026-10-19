package com.v2ray.ang.ui.more

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.compose.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MoreUiState(
    /** "0" follow system, "1" light, "2" dark. */
    val theme: String = "0",
    val language: String = "auto",
    val vpnMode: Boolean = true,
    val speedNotification: Boolean = false,
    val smartConnect: Boolean = true,
    val quickConnect: Boolean = false,
    val showAdvanced: Boolean = false,
)

/** State + writes for the Settings tab; keeps MMKV out of the composables. */
class MoreViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<MoreUiState> = _state.asStateFlow()

    private fun read() = MoreUiState(
        theme = MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0",
        language = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, "auto") ?: "auto",
        vpnMode = (MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN) ?: AppConfig.VPN) == AppConfig.VPN,
        speedNotification = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED, false),
        smartConnect = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_CONNECT, true),
        quickConnect = MmkvManager.decodeSettingsBool(AppConfig.PREF_QUICK_CONNECT, false),
        showAdvanced = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_ADVANCED, false),
    )

    fun refresh() { _state.value = read() }

    fun setTheme(mode: String) {
        ThemeManager.setThemeMode(mode)
        _state.update { it.copy(theme = mode) }
    }

    fun setLanguage(code: String) {
        _state.update { it.copy(language = code) }
        AppLocaleManager.setApplicationLanguage(code)
    }

    fun setVpnMode(vpn: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_MODE, if (vpn) AppConfig.VPN else "Proxy only")
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_MODE)
        _state.update { it.copy(vpnMode = vpn) }
    }

    fun setSpeedNotification(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED, on)
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_SPEED_ENABLED)
        _state.update { it.copy(speedNotification = on) }
    }

    fun setSmartConnect(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_CONNECT, on)
        _state.update { it.copy(smartConnect = on) }
    }

    fun setQuickConnect(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_QUICK_CONNECT, on)
        _state.update { it.copy(quickConnect = on) }
    }

    fun setShowAdvanced(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_SHOW_ADVANCED, on)
        _state.update { it.copy(showAdvanced = on) }
    }
}
