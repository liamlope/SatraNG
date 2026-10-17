package com.v2ray.ang.ui.compose.satra

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.SatraColors

/** Brand tokens for cinematic Satra UI (teal / ink / amber). */
object SatraTokens {
    val Teal = SatraColors.Teal
    val TealDeep = SatraColors.TealDeep
    val TealSoft = SatraColors.TealSoft
    val Ink = SatraColors.Ink
    val InkSoft = SatraColors.InkSoft
    val Amber = SatraColors.Amber
    val Danger = SatraColors.Danger
    val Success = Color(0xFF1FA97A)
    val DividerOnGlass = Color.White.copy(alpha = 0.15f)

    val RadiusSm = 12.dp
    val RadiusMd = 18.dp
    val RadiusLg = 24.dp
    val RadiusXl = 32.dp
    val RadiusCapsule = 999.dp

    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 24.dp
    val SpaceXxl = 32.dp

    val CapsuleHeight = 60.dp
    val BottomBarMinHeight = 56.dp

    val ElevationCard: Dp = 0.dp

    fun backgroundBrush(dark: Boolean): Brush = if (dark) {
        Brush.verticalGradient(
            listOf(Color(0xFF0A1826), SatraColors.Ink, Color(0xFF06141F))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFECF5F2), SatraColors.Mist, Color(0xFFE8EEF4))
        )
    }
}
