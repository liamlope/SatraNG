package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tor mode for SatraNG.
 *
 * When enabled with VPN connected, Xray chains through local Tor SOCKS
 * ([AppConfig.TAG_TOR]) with dialerProxy = selected VPN outbound, so Tor
 * bootstraps via the same config. If Orbot is installed it is started to
 * provide the SOCKS port; otherwise any Tor SOCKS on the configured port works.
 */
object TorManager {

    private const val ORBOT_PACKAGE = "org.torproject.android"
    private const val ORBOT_SERVICE = "org.torproject.android.service.TorService"

    data class TorState(
        val enabled: Boolean = false,
        val exitCountry: String = "",
        val socksPort: Int = AppConfig.TOR_DEFAULT_SOCKS_PORT,
        val running: Boolean = false,
        val statusMessage: String = "",
    )

    private val _state = MutableStateFlow(load())
    val state: StateFlow<TorState> = _state.asStateFlow()

    private fun load(): TorState = TorState(
        enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TOR_ENABLED, false),
        exitCountry = MmkvManager.decodeSettingsString(AppConfig.PREF_TOR_EXIT_COUNTRY, "") ?: "",
        socksPort = MmkvManager.decodeSettingsString(AppConfig.PREF_TOR_SOCKS_PORT, "")
            ?.toIntOrNull() ?: AppConfig.TOR_DEFAULT_SOCKS_PORT,
    )

    fun setEnabled(enabled: Boolean, vpnConnected: Boolean): Boolean {
        if (enabled && !vpnConnected) {
            _state.value = _state.value.copy(
                enabled = false,
                running = false,
                statusMessage = "vpn_required",
            )
            MmkvManager.encodeSettings(AppConfig.PREF_TOR_ENABLED, false)
            return false
        }
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_ENABLED, enabled)
        val socksReady = enabled && isSocksReady()
        _state.value = _state.value.copy(
            enabled = enabled,
            running = enabled && vpnConnected && socksReady,
            statusMessage = when {
                !enabled -> ""
                socksReady -> "active"
                else -> "waiting_socks"
            },
        )
        return true
    }

    fun setExitCountry(code: String) {
        val normalized = code.trim().uppercase()
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_EXIT_COUNTRY, normalized)
        _state.value = _state.value.copy(exitCountry = normalized)
    }

    fun onVpnStateChanged(connected: Boolean) {
        val enabled = _state.value.enabled
        if (enabled && !connected) {
            _state.value = _state.value.copy(running = false, statusMessage = "vpn_required")
        } else if (enabled && connected) {
            val ready = isSocksReady()
            _state.value = _state.value.copy(
                running = ready,
                statusMessage = if (ready) "active" else "waiting_socks",
            )
        }
    }

    fun tryStartOrbot(context: Context) {
        try {
            val intent = Intent().setClassName(ORBOT_PACKAGE, ORBOT_SERVICE)
            context.startForegroundService(intent)
            LogUtil.i(AppConfig.TAG, "Requested Orbot TorService start")
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Orbot not available: ${e.message}")
        }
    }

    fun isSocksReady(port: Int = _state.value.socksPort): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(AppConfig.LOOPBACK, port), 250)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Torrc-style ExitNodes snippet for the selected country (empty = any). */
    fun exitNodesDirective(): String {
        val cc = _state.value.exitCountry
        if (cc.isBlank() || cc.length != 2) return ""
        return "ExitNodes {$cc}\nStrictNodes 1\n"
    }

    fun localSocksProxyForBootstrap(): String {
        val port = SettingsManager.getSocksPort()
        return "socks5://${AppConfig.LOOPBACK}:$port"
    }

    fun refresh() {
        val base = load()
        _state.value = base.copy(
            running = base.enabled && isSocksReady(base.socksPort),
            statusMessage = _state.value.statusMessage,
        )
    }
}
