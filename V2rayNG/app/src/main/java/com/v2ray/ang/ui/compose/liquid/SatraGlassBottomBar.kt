package com.v2ray.ang.ui.compose.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.ui.compose.satra.SatraMotionSpec
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.rememberReduceMotion

/**
 * Four-tab glass bottom bar with liquid pill indicator.
 * Height + internal padding sized so labels are never clipped by the capsule edge.
 * @param items Triple(index, labelRes, iconRes)
 */
@Composable
fun SatraGlassBottomBar(
    selected: Int,
    items: List<Triple<Int, Int, Int>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    SatraGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(72.dp),
        capsule = true,
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            val count = items.size.coerceAtLeast(1)
            val slot = maxWidth / count
            val indicatorOffset by animateFloatAsState(
                targetValue = selected.toFloat(),
                animationSpec = if (reduce) SatraMotionSpec.short() else SatraMotionSpec.liquidPill,
                label = "navPill",
            )
            Box(
                Modifier
                    .offset(x = slot * indicatorOffset)
                    .width(slot)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(SatraTokens.RadiusCapsule))
                    .background(SatraTokens.Purple.copy(alpha = 0.22f)),
            )
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { (index, labelRes, iconRes) ->
                    GlassNavItem(
                        selected = selected == index,
                        labelRes = labelRes,
                        iconRes = iconRes,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.GlassNavItem(
    selected: Boolean,
    labelRes: Int,
    iconRes: Int,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        if (selected) SatraTokens.PurpleDeep else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = SatraMotionSpec.short(),
        label = "navTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = SatraMotionSpec.pressGel,
        label = "navScale",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(labelRes),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
