package com.v2ray.ang.ui.compose.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class PowerCapsuleState {
    Off,
    Connecting,
    On,
    Failed,
}

/**
 * Slide power control: drag thumb right to connect, left to disconnect.
 * Soft Cosmic Orange thumb when idle; green when on; red when failed.
 */
@Composable
fun SatraGlassCapsule(
    state: PowerCapsuleState,
    subtitle: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    pingLabel: String? = null,
    /** When connected, tap the center text to test latency (slide still disconnects). */
    onCenterTap: (() -> Unit)? = null,
) {
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val accent by animateColorAsState(
        targetValue = when (state) {
            PowerCapsuleState.Off -> SatraTokens.Amber
            PowerCapsuleState.Connecting -> SatraTokens.Amber
            PowerCapsuleState.On -> SatraTokens.Green
            PowerCapsuleState.Failed -> SatraTokens.Danger
        },
        animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.capsuleStateMorph(),
        label = "capsuleAccent",
    )
    val title = when (state) {
        PowerCapsuleState.Off, PowerCapsuleState.Failed ->
            stringResource(R.string.satra_capsule_slide_on)
        PowerCapsuleState.Connecting -> stringResource(R.string.satra_capsule_connecting)
        PowerCapsuleState.On -> stringResource(R.string.satra_capsule_slide_off)
    }
    val a11y = stringResource(R.string.satra_capsule_a11y, title)
    val stateDesc = when (state) {
        PowerCapsuleState.On -> stringResource(R.string.satra_settings_on)
        PowerCapsuleState.Connecting -> stringResource(R.string.satra_capsule_connecting)
        PowerCapsuleState.Failed -> stringResource(R.string.satra_capsule_failed)
        PowerCapsuleState.Off -> stringResource(R.string.satra_settings_off)
    }
    val connectLabel = stringResource(R.string.satra_capsule_connect)
    val disconnectLabel = stringResource(R.string.satra_capsule_connected)

    // While connecting, keep the thumb at the target end (never park mid-track).
    val thumbRest = when (state) {
        PowerCapsuleState.On -> 1f
        PowerCapsuleState.Connecting -> 1f
        else -> 0f
    }
    val offsetFrac = remember { Animatable(thumbRest) }
    LaunchedEffect(state) {
        offsetFrac.animateTo(
            targetValue = thumbRest,
            animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.capsuleStateMorph(),
        )
    }

    var dragX by remember { mutableFloatStateOf(0f) }

    SatraGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .semantics {
                contentDescription = a11y
                role = Role.Switch
                stateDescription = stateDesc
                customActions = listOf(
                    CustomAccessibilityAction(connectLabel) {
                        if (state == PowerCapsuleState.Off || state == PowerCapsuleState.Failed) {
                            onToggle(); true
                        } else false
                    },
                    CustomAccessibilityAction(disconnectLabel) {
                        if (state == PowerCapsuleState.On) {
                            onToggle(); true
                        } else false
                    },
                )
            },
        capsule = true,
        onClick = null,
        connectedTint = state == PowerCapsuleState.On,
        contentAlignment = Alignment.Center,
        showBorder = false,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 5.dp)) {
            val trackWidthPx = with(density) { maxWidth.toPx() }
            val thumbSize = 54.dp
            val thumbPx = with(density) { thumbSize.toPx() }
            val maxTravel = (trackWidthPx - thumbPx).coerceAtLeast(1f)
            val canDrag = state != PowerCapsuleState.Connecting

            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = thumbSize / 2 + 8.dp)
                    .then(
                        if (state == PowerCapsuleState.On && onCenterTap != null) {
                            Modifier.clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCenterTap()
                            }
                        } else {
                            Modifier
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    // Preview: idle/connecting title is brand orange; on = green.
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                val sub = when {
                    state == PowerCapsuleState.On && !pingLabel.isNullOrBlank() -> pingLabel
                    !subtitle.isNullOrBlank() -> subtitle
                    else -> null
                }
                if (sub != null && state != PowerCapsuleState.Connecting) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = onGlassSecondary(),
                        maxLines = 1,
                    )
                }
            }

            val xPx = (offsetFrac.value * maxTravel).coerceIn(0f, maxTravel)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(xPx.roundToInt(), 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(accent)
                    .then(
                        if (canDrag) {
                            Modifier.pointerInput(state, maxTravel) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragX = offsetFrac.value * maxTravel
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        dragX = (dragX + amount).coerceIn(0f, maxTravel)
                                        scope.launch {
                                            offsetFrac.snapTo(dragX / maxTravel)
                                        }
                                    },
                                    onDragEnd = {
                                        val frac = dragX / maxTravel
                                        val wantOn = frac >= 0.55f
                                        val wantOff = frac <= 0.45f
                                        when (state) {
                                            PowerCapsuleState.Off, PowerCapsuleState.Failed -> {
                                                if (wantOn) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onToggle()
                                                } else {
                                                    scope.launch { offsetFrac.animateTo(0f) }
                                                }
                                            }
                                            PowerCapsuleState.On -> {
                                                if (wantOff) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onToggle()
                                                } else {
                                                    scope.launch { offsetFrac.animateTo(1f) }
                                                }
                                            }
                                            PowerCapsuleState.Connecting -> Unit
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { offsetFrac.animateTo(thumbRest) }
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (state == PowerCapsuleState.Connecting) {
                    val infinite = rememberInfiniteTransition(label = "capsuleSpin")
                    val alpha by infinite.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "dotPulse",
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White.copy(alpha = if (reduce) 1f else alpha),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more_24dp),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(
                                when (state) {
                                    PowerCapsuleState.On -> 90f // points left
                                    else -> -90f // points right
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** Map runtime flags to capsule visual state (ceremony can force Connecting). */
fun powerCapsuleState(
    isRunning: Boolean,
    connecting: Boolean,
    failed: Boolean,
): PowerCapsuleState = when {
    failed -> PowerCapsuleState.Failed
    // Ceremony / reconnect must win over isRunning so the orange connect anim plays.
    connecting -> PowerCapsuleState.Connecting
    isRunning -> PowerCapsuleState.On
    else -> PowerCapsuleState.Off
}
