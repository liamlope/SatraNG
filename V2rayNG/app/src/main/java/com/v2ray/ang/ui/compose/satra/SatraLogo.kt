package com.v2ray.ang.ui.compose.satra

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/** The SatraNG app mark: white "S" route on a squircle, orange when idle and green once connected. */
@Composable
fun SatraLogo(connected: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val top by animateColorAsState(if (connected) SatraBrand.GreenLight else Color(0xFFFF9A3D), tween(450), label = "logoTop")
    val bottom by animateColorAsState(if (connected) SatraBrand.GreenDeep else SatraBrand.Orange, tween(450), label = "logoBottom")
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_satra_glyph), null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}
