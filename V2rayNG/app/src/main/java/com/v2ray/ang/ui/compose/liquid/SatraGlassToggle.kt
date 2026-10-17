package com.v2ray.ang.ui.compose.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion
import androidx.compose.ui.graphics.Color

@Composable
fun SatraGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLabel: String = "On",
    offLabel: String = "Off",
) {
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val track by animateColorAsState(
        targetValue = if (checked) SatraTokens.TealDeep else Color(0xFF8A949E),
        animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.capsuleStateMorph(),
        label = "toggleTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.pressGel,
        label = "toggleThumb",
    )
    Box(
        modifier
            .width(52.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(track.copy(alpha = if (enabled) 1f else 0.45f))
            .semantics {
                stateDescription = if (checked) onLabel else offLabel
            }
            .clickable(enabled = enabled, role = Role.Switch) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
