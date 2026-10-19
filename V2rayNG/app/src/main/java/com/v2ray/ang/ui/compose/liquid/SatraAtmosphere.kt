package com.v2ray.ang.ui.compose.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion

enum class ConnectionAtmosphereState {
    Idle,
    Connecting,
    Connected,
    Failed,
}

@Immutable
data class AtmospherePalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
)

fun atmospherePalette(state: ConnectionAtmosphereState, dark: Boolean): AtmospherePalette = when (state) {
    ConnectionAtmosphereState.Idle -> if (dark) {
        // Pure dark mixed with soft brand purple.
        AtmospherePalette(Color(0xFF000000), Color(0xFF0C0816), Color(0xFF000000))
    } else {
        AtmospherePalette(Color(0xFFFFFBFF), Color(0xFFF6F2FF), Color(0xFFEEE8FA))
    }
    ConnectionAtmosphereState.Connecting -> if (dark) {
        AtmospherePalette(Color(0xFF0A0804), Color(0xFF1C1408), Color(0xFF050403))
    } else {
        AtmospherePalette(Color(0xFFFFF8F0), Color(0xFFFFF1E0), Color(0xFFF5E6D4))
    }
    ConnectionAtmosphereState.Connected -> if (dark) {
        // Pure dark + soft green wash (connected).
        AtmospherePalette(Color(0xFF000000), Color(0xFF07140F), Color(0xFF000000))
    } else {
        AtmospherePalette(Color(0xFFF3FBF7), Color(0xFFE4F6EC), Color(0xFFD4EFDF))
    }
    ConnectionAtmosphereState.Failed -> if (dark) {
        AtmospherePalette(Color(0xFF0A0406), Color(0xFF1A0A0E), Color(0xFF050203))
    } else {
        AtmospherePalette(Color(0xFFFFF5F6), Color(0xFFFFE8EA), Color(0xFFF8D8DC))
    }
}

fun atmosphereLerpMs(state: ConnectionAtmosphereState, reduceMotion: Boolean): Int {
    if (reduceMotion) return SatraMotionSpec.ShortMs
    return when (state) {
        ConnectionAtmosphereState.Idle -> 420
        ConnectionAtmosphereState.Connecting -> 360
        ConnectionAtmosphereState.Connected -> 520
        ConnectionAtmosphereState.Failed -> 280
    }
}

/**
 * Simple state machine for Failed → Idle timeout and connect ceremony mapping.
 * Pure — unit-tested.
 */
object AtmosphereStateMachine {
    fun fromConnection(
        isRunning: Boolean,
        connecting: Boolean,
        failed: Boolean,
    ): ConnectionAtmosphereState = when {
        failed -> ConnectionAtmosphereState.Failed
        // Hold Connecting while ceremony runs even if VPN already reported up.
        connecting -> ConnectionAtmosphereState.Connecting
        isRunning -> ConnectionAtmosphereState.Connected
        else -> ConnectionAtmosphereState.Idle
    }

    /** After Failed, return Idle once [elapsedMs] exceeds [failHoldMs] unless [failedPersistent]. */
    fun afterFailHold(
        current: ConnectionAtmosphereState,
        elapsedMs: Long,
        failHoldMs: Long = 2200L,
        failedPersistent: Boolean = false,
    ): ConnectionAtmosphereState {
        if (current != ConnectionAtmosphereState.Failed) return current
        if (failedPersistent) return ConnectionAtmosphereState.Failed
        return if (elapsedMs >= failHoldMs) ConnectionAtmosphereState.Idle else ConnectionAtmosphereState.Failed
    }
}

@Composable
fun SatraAtmosphere(
    state: ConnectionAtmosphereState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    val reduce = rememberReduceMotion()
    val palette = atmospherePalette(state, dark)
    val duration = atmosphereLerpMs(state, reduce)
    val top by animateColorAsState(palette.top, tween(duration, easing = SatraMotionSpec.EnterEasing), label = "atmTop")
    val mid by animateColorAsState(palette.mid, tween(duration, easing = SatraMotionSpec.EnterEasing), label = "atmMid")
    val bottom by animateColorAsState(palette.bottom, tween(duration, easing = SatraMotionSpec.EnterEasing), label = "atmBottom")
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, mid, bottom))),
        content = content,
    )
}
