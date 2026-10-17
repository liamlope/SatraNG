package com.v2ray.ang.ui.compose.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion

/** Glass bottom sheet: scrim fade + spring from bottom (sheetPresent). */
@Composable
fun SatraGlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val reduce = rememberReduceMotion()
    val scrim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.toastGlass(),
        label = "sheetScrim",
    )
    val lift by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.sheetPresent,
        label = "sheetLift",
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * scrim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Box(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - lift) * 120f
                    alpha = 0.4f + 0.6f * lift
                }
                .navigationBarsPadding(),
        ) {
            SatraGlassSheetContainer(content = content)
        }
    }
}

@Composable
fun SatraGlassConfirmSheet(
    visible: Boolean,
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    SatraGlassSheet(visible = visible, onDismiss = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = onGlassPrimary(),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = onGlassSecondary(),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(confirmLabel, color = SatraTokens.Danger)
            }
        }
    }
}
