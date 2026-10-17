package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.SatraColors
import com.v2ray.ang.ui.compose.SatraMotion
import kotlinx.coroutines.launch

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit
) {
    val scope = rememberCoroutineScope()
    val pressScale = remember { Animatable(1f) }
    val rotationAnim = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "satraPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    LaunchedEffect(isRunning) {
        if (!isRunning) rotationAnim.snapTo(0f)
    }

    val orbColor by animateColorAsState(
        targetValue = if (isRunning) SatraColors.Teal else if (isDarkTheme) Color(0xFF3A4E60) else Color(0xFF8FA0B0),
        animationSpec = tween(420),
        label = "orbColor"
    )
    val statusTint by animateColorAsState(
        targetValue = if (isRunning) SatraColors.TealDeep else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(420),
        label = "statusTint"
    )
    val panelBrush = if (isDarkTheme) {
        Brush.verticalGradient(listOf(Color(0xE60B1B2B), Color(0xF2071018)))
    } else {
        Brush.verticalGradient(listOf(Color(0xF2FFFFFF), Color(0xE8F3F6F9)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(panelBrush)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.White,
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = { onAction(MainAction.TestCurrentServer) }
                )
                .padding(start = 20.dp, end = 108.dp, top = 18.dp, bottom = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusTint)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (isRunning) R.string.satra_status_connected
                            else R.string.satra_status_ready
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = statusTint,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = displayText }
                )
            }
        }

        ConnectionOrb(
            isRunning = isRunning,
            orbColor = orbColor,
            pulse = if (isRunning) pulse else 1f,
            ringAlpha = if (isRunning) ringAlpha else 0.12f,
            rotation = rotationAnim.value,
            scale = pressScale.value,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 22.dp)
                .offset(y = (-36).dp),
            onClick = {
                scope.launch {
                    pressScale.animateTo(0.88f, SatraMotion.snappy)
                    pressScale.animateTo(1f, SatraMotion.soft)
                }
                if (!isRunning) {
                    scope.launch {
                        rotationAnim.snapTo(0f)
                        rotationAnim.animateTo(720f, tween(2200))
                    }
                }
                onAction(MainAction.ToggleService)
            }
        )
    }
}

@Composable
private fun ConnectionOrb(
    isRunning: Boolean,
    orbColor: Color,
    pulse: Float,
    ringAlpha: Float,
    rotation: Float,
    scale: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.size(88.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    alpha = ringAlpha
                }
                .border(
                    width = 2.dp,
                    color = orbColor,
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(18.dp, CircleShape, ambientColor = orbColor.copy(alpha = 0.45f), spotColor = orbColor.copy(alpha = 0.55f))
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            orbColor.copy(alpha = 0.95f),
                            orbColor,
                            SatraColors.TealDeep.copy(alpha = if (isRunning) 0.9f else 0.35f)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                else painterResource(R.drawable.ic_play_24dp),
                contentDescription = stringResource(
                    if (isRunning) R.string.acc_stop else R.string.acc_start
                ),
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}
