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
import com.v2ray.ang.ui.compose.satra.SatraTokens
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
        AtmospherePalette(Color(0xFF0A1826), SatraTokens.Ink, Color(0xFF06141F))
    } else {
        AtmospherePalette(Color(0xFFECF5F2), Color(0xFFE8EEF4), Color(0xFFDCE8F0))
    }
    ConnectionAtmosphereState.Connecting -> if (dark) {
        AtmospherePalette(Color(0xFF2A1A0A), Color(0xFF3D2810), Color(0xFF1A1208))
    } else {
        AtmospherePalette(Color(0xFFFFF0E0), Color(0xFFFFE4C4), Color(0xFFF5D9B0))
    }
    ConnectionAtmosphereState.Connected -> if (dark) {
        AtmospherePalette(Color(0xFF062820), Color(0xFF0A3D30), Color(0xFF041A14))
    } else {
        AtmospherePalette(Color(0xFFD4F5E8), Color(0xFFB8EBD4), Color(0xFF9ADFC0))
    }
    ConnectionAtmosphereState.Failed -> if (dark) {
        AtmospherePalette(Color(0xFF2A0C10), Color(0xFF3A1218), Color(0xFF1A080A))
    } else {
        AtmospherePalette(Color(0xFFFFE8EA), Color(0xFFFFD0D4), Color(0xFFF5C0C6))
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
        isRunning -> ConnectionAtmosphereState.Connected
        connecting -> ConnectionAtmosphereState.Connecting
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
