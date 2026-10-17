package com.v2ray.ang.ui.compose.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion

enum class PowerCapsuleState {
    Off,
    Connecting,
    On,
    Failed,
}

@Composable
fun SatraGlassCapsule(
    state: PowerCapsuleState,
    subtitle: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    pingLabel: String? = null,
) {
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val accent by animateColorAsState(
        targetValue = when (state) {
            PowerCapsuleState.Off -> SatraTokens.Purple
            PowerCapsuleState.Connecting -> SatraTokens.Amber
            PowerCapsuleState.On -> SatraTokens.Green
            PowerCapsuleState.Failed -> SatraTokens.Danger
        },
        animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.capsuleStateMorph(),
        label = "capsuleAccent",
    )
    val title = when (state) {
        PowerCapsuleState.Off -> stringResource(R.string.satra_capsule_connect)
        PowerCapsuleState.Connecting -> stringResource(R.string.satra_capsule_connecting)
        PowerCapsuleState.On -> stringResource(R.string.satra_capsule_connected)
        PowerCapsuleState.Failed -> stringResource(R.string.satra_capsule_failed)
    }
    val a11y = stringResource(R.string.satra_capsule_a11y, title)
    val stateDesc = when (state) {
        PowerCapsuleState.On -> stringResource(R.string.satra_settings_on)
        PowerCapsuleState.Connecting -> stringResource(R.string.satra_capsule_connecting)
        PowerCapsuleState.Failed -> stringResource(R.string.satra_capsule_failed)
        PowerCapsuleState.Off -> stringResource(R.string.satra_settings_off)
    }

    SatraGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .semantics {
                contentDescription = a11y
                role = Role.Switch
                stateDescription = stateDesc
            },
        capsule = true,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        connectedTint = state == PowerCapsuleState.On,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (state == PowerCapsuleState.Connecting) {
                val infinite = rememberInfiniteTransition(label = "capsuleSpin")
                val alpha by infinite.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dotPulse",
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = accent.copy(alpha = if (reduce) 1f else alpha),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
                val sub = when {
                    state == PowerCapsuleState.On && !pingLabel.isNullOrBlank() -> pingLabel
                    !subtitle.isNullOrBlank() -> subtitle
                    else -> null
                }
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelMedium,
                        color = onGlassSecondary(),
                        maxLines = 1,
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
    isRunning -> PowerCapsuleState.On
    connecting -> PowerCapsuleState.Connecting
    else -> PowerCapsuleState.Off
}
