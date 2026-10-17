package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket

enum class TorConnectionMode {
    Off,
    WithVpn,
    TorOnly,
}

enum class TorBridgeMode {
    Auto,
    None,
    Obfs4,
    Snowflake,
    Meek,
    Custom,
}

/**
 * Tor mode state for SatraNG UI and routing.
 * Engine bootstrap is owned here; SOCKS may be provided by embedded engine or local port.
 */
object TorManager {

    data class TorState(
        val mode: TorConnectionMode = TorConnectionMode.Off,
        val enabled: Boolean = false,
        val exitCountry: String = "",
        val strictNodes: Boolean = true,
        val bridgeMode: TorBridgeMode = TorBridgeMode.Auto,
        val customBridges: String = "",
        val socksPort: Int = AppConfig.TOR_DEFAULT_SOCKS_PORT,
        val bootstrapPercent: Int = 0,
        val running: Boolean = false,
        val streamIsolation: Boolean = false,
        val killSwitch: Boolean = true,
        val statusMessage: String = "",
        val circuitHint: String = "",
    )

    private val _state = MutableStateFlow(load())
    val state: StateFlow<TorState> = _state.asStateFlow()

    private fun load(): TorState {
        val modeName = MmkvManager.decodeSettingsString(AppConfig.PREF_TOR_MODE, TorConnectionMode.Off.name)
        val mode = runCatching { TorConnectionMode.valueOf(modeName!!) }.getOrDefault(TorConnectionMode.Off)
        val bridgeName = MmkvManager.decodeSettingsString(AppConfig.PREF_TOR_BRIDGE_MODE, TorBridgeMode.Auto.name)
        val bridge = runCatching { TorBridgeMode.valueOf(bridgeName!!) }.getOrDefault(TorBridgeMode.Auto)
        val enabledLegacy = MmkvManager.decodeSettingsBool(AppConfig.PREF_TOR_ENABLED, false)
        val resolvedMode = when {
            mode != TorConnectionMode.Off -> mode
            enabledLegacy -> TorConnectionMode.WithVpn
            else -> TorConnectionMode.Off
        }
        return TorState(
            mode = resolvedMode,
            enabled = resolvedMode != TorConnectionMode.Off,
            exitCountry = MmkvManager.decodeSettingsString(AppConfig.PREF_TOR_EXIT_COUNTRY, "") ?: "",
            strictNodes = MmkvManager.decodeSettingsBool(AppConfig.PREF_TOR_STRICT_NODES, true),
            bridgeMode = bridge,
            customBridges = MmkvManager.decodeSettingsString(AppConfig.PREF_TOR_CUSTOM_BRIDGES, "") ?: "",
            socksPort = MmkvManager.decodeSettingsString(AppConfig.PREF_TOR_SOCKS_PORT, "")
                ?.toIntOrNull() ?: AppConfig.TOR_DEFAULT_SOCKS_PORT,
            streamIsolation = MmkvManager.decodeSettingsBool(AppConfig.PREF_TOR_STREAM_ISOLATION, false),
            killSwitch = MmkvManager.decodeSettingsBool(AppConfig.PREF_TOR_KILL_SWITCH, true),
        )
    }

    fun setMode(mode: TorConnectionMode, vpnConnected: Boolean): Boolean {
        if (mode == TorConnectionMode.WithVpn && !vpnConnected) {
            _state.value = _state.value.copy(
                mode = TorConnectionMode.Off,
                enabled = false,
                running = false,
                statusMessage = "vpn_required",
            )
            persistMode(TorConnectionMode.Off)
            return false
        }
        persistMode(mode)
        val socksReady = mode != TorConnectionMode.Off && isSocksReady()
        _state.value = _state.value.copy(
            mode = mode,
            enabled = mode != TorConnectionMode.Off,
            running = when (mode) {
                TorConnectionMode.Off -> false
                TorConnectionMode.WithVpn -> vpnConnected && socksReady
                TorConnectionMode.TorOnly -> socksReady
            },
            bootstrapPercent = if (mode == TorConnectionMode.Off) 0 else if (socksReady) 100 else 35,
            statusMessage = when {
                mode == TorConnectionMode.Off -> ""
                socksReady -> "active"
                else -> "waiting_socks"
            },
        )
        return true
    }

    /** Backward-compatible toggle used by older Home chip. */
    fun setEnabled(enabled: Boolean, vpnConnected: Boolean): Boolean {
        return setMode(
            if (enabled) TorConnectionMode.WithVpn else TorConnectionMode.Off,
            vpnConnected,
        )
    }

    fun setExitCountry(code: String) {
        val normalized = code.trim().uppercase()
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_EXIT_COUNTRY, normalized)
        _state.value = _state.value.copy(exitCountry = normalized)
    }

    fun setStrictNodes(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_STRICT_NODES, enabled)
        _state.value = _state.value.copy(strictNodes = enabled)
    }

    fun setBridgeMode(mode: TorBridgeMode) {
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_BRIDGE_MODE, mode.name)
        _state.value = _state.value.copy(bridgeMode = mode)
    }

    fun setCustomBridges(lines: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_CUSTOM_BRIDGES, lines)
        _state.value = _state.value.copy(customBridges = lines)
    }

    fun setStreamIsolation(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_STREAM_ISOLATION, enabled)
        _state.value = _state.value.copy(streamIsolation = enabled)
    }

    fun setKillSwitch(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_KILL_SWITCH, enabled)
        _state.value = _state.value.copy(killSwitch = enabled)
    }

    fun rotateBridge() {
        val order = listOf(
            TorBridgeMode.Snowflake,
            TorBridgeMode.Obfs4,
            TorBridgeMode.Meek,
            TorBridgeMode.Auto,
        )
        val next = order[((order.indexOf(_state.value.bridgeMode).takeIf { it >= 0 } ?: -1) + 1) % order.size]
        setBridgeMode(next)
        _state.value = _state.value.copy(
            statusMessage = "bridge_rotated",
            circuitHint = "Rotated to ${next.name}",
        )
    }

    fun requestNewIdentity() {
        _state.value = _state.value.copy(
            circuitHint = "New identity requested",
            bootstrapPercent = (_state.value.bootstrapPercent).coerceAtLeast(50),
        )
        LogUtil.i(AppConfig.TAG, "Tor new identity requested")
    }

    fun onVpnStateChanged(connected: Boolean) {
        val mode = _state.value.mode
        if (mode == TorConnectionMode.WithVpn && !connected) {
            _state.value = _state.value.copy(running = false, statusMessage = "vpn_required")
        } else if (mode != TorConnectionMode.Off && connected) {
            val ready = isSocksReady()
            _state.value = _state.value.copy(
                running = ready || mode == TorConnectionMode.TorOnly,
                bootstrapPercent = if (ready) 100 else 40,
                statusMessage = if (ready) "active" else "waiting_socks",
            )
        }
    }

    fun tryStartOrbot(context: Context) {
        // Kept as no-op hook for legacy call sites; embedded engine replaces Orbot.
        LogUtil.i(AppConfig.TAG, "Tor start requested (embedded path); context=${context.packageName}")
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

    fun exitNodesDirective(): String {
        val cc = _state.value.exitCountry
        if (cc.isBlank() || cc.length != 2) return ""
        val strict = if (_state.value.strictNodes) 1 else 0
        return "ExitNodes {$cc}\nStrictNodes $strict\n"
    }

    fun localSocksProxyForBootstrap(): String {
        val port = SettingsManager.getSocksPort()
        return "socks5://${AppConfig.LOOPBACK}:$port"
    }

    fun refresh() {
        val base = load()
        _state.value = base.copy(
            running = base.enabled && (base.mode == TorConnectionMode.TorOnly || isSocksReady(base.socksPort)),
            statusMessage = _state.value.statusMessage,
            bootstrapPercent = _state.value.bootstrapPercent,
            circuitHint = _state.value.circuitHint,
        )
    }

    private fun persistMode(mode: TorConnectionMode) {
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_MODE, mode.name)
        MmkvManager.encodeSettings(AppConfig.PREF_TOR_ENABLED, mode != TorConnectionMode.Off)
    }
}
