package com.v2ray.ang.ui.compose.satra

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.SatraColors

/** Extended brand + privacy tokens for cinematic Satra UI. */
object SatraTokens {
    val Teal = SatraColors.Teal
    val TealDeep = SatraColors.TealDeep
    val TealSoft = SatraColors.TealSoft
    val Ink = SatraColors.Ink
    val InkSoft = SatraColors.InkSoft
    val Amber = SatraColors.Amber
    val Danger = SatraColors.Danger

    // Tor-protected chrome
    val PrivacyIndigo = Color(0xFF1B1340)
    val PrivacyIndigoDeep = Color(0xFF0E0A24)
    val PrivacyViolet = Color(0xFF6E5BFF)
    val PrivacyVioletSoft = Color(0xFFA89BFF)
    val PrivacyGlow = Color(0xFF3D2B8E)

    val RadiusSm = 10.dp
    val RadiusMd = 16.dp
    val RadiusLg = 20.dp
    val RadiusXl = 28.dp
    val RadiusOrb = 999.dp

    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 24.dp
    val SpaceXxl = 32.dp

    val ElevationCard: Dp = 0.dp

    fun backgroundBrush(dark: Boolean, torProtected: Boolean): Brush = when {
        torProtected && dark -> Brush.verticalGradient(
            listOf(PrivacyIndigo, PrivacyIndigoDeep, Color(0xFF060414))
        )
        torProtected -> Brush.verticalGradient(
            listOf(Color(0xFFECE8FF), Color(0xFFF4F1FF), Color(0xFFE8EEF8))
        )
        dark -> Brush.verticalGradient(
            listOf(Color(0xFF0A1826), SatraColors.Ink, Color(0xFF06141F))
        )
        else -> Brush.verticalGradient(
            listOf(Color(0xFFECF5F2), SatraColors.Mist, Color(0xFFE8EEF4))
        )
    }

    fun accent(torProtected: Boolean): Color =
        if (torProtected) PrivacyViolet else Teal

    fun accentDeep(torProtected: Boolean): Color =
        if (torProtected) PrivacyVioletSoft else TealDeep
}
