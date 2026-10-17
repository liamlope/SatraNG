package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SatraNG brand tokens — pure dark / clean light.
 * Palette: blue (actions) · green (connected) · orange (connecting) ·
 * red (error) · purple (brand accent).
 */
object SatraColors {
    val Ink = Color(0xFF000000)
    val InkSoft = Color(0xFF0C0C0E)
    val Slate = Color(0xFF1A1A1F)
    val Mist = Color(0xFFF7F7F8)
    val MistDeep = Color(0xFFEBEBEF)

    /** Blue — primary actions / idle chrome (name Teal kept for call-sites). */
    val Teal = Color(0xFF4FA3FF)
    val TealDeep = Color(0xFF2B7FE8)
    val TealSoft = Color(0xFFA8D0FF)

    /** Green — connected / success. */
    val Green = Color(0xFF2ECC8A)
    val GreenDeep = Color(0xFF1FA971)
    val GreenSoft = Color(0xFF8AEFC0)

    /** Orange — connecting / warning. */
    val Amber = Color(0xFFF0A94A)
    val AmberSoft = Color(0xFFFFD78A)

    /** Red — failed / danger. */
    val Danger = Color(0xFFFF5A5F)

    /** Purple — distinctive Satra brand accent (mixed into pure dark / soft light). */
    val Purple = Color(0xFFA56BFF)
    val PurpleDeep = Color(0xFF7B3DF0)
    val PurpleSoft = Color(0xFFD6C4FF)
    val PurpleMist = Color(0xFF2A1848)

    val TextDark = Color(0xFF111114)
    val TextMutedDark = Color(0xFF6B6B76)
    val TextLight = Color(0xFFF2F2F4)
    val TextMutedLight = Color(0xFF9A9AA6)
}

private val LightColor = lightColorScheme(
    primary = SatraColors.PurpleDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF2A1258),
    secondary = SatraColors.TealDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E8FF),
    onSecondaryContainer = Color(0xFF0A2A4A),
    tertiary = SatraColors.Amber,
    onTertiary = SatraColors.Ink,
    tertiaryContainer = Color(0xFFFFE8C2),
    onTertiaryContainer = Color(0xFF3A2500),
    error = SatraColors.Danger,
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
    // Soft light mixed with brand purple — still reads as light, not purple-washed UI.
    background = Color(0xFFFFFBFF),
    onBackground = SatraColors.TextDark,
    surface = Color(0xFFFFFFFF),
    onSurface = SatraColors.TextDark,
    surfaceVariant = Color(0xFFF0EAF8),
    onSurfaceVariant = SatraColors.TextMutedDark,
    outline = Color(0xFFCFCFD6),
    outlineVariant = Color(0xFFE8E2F0),
    inverseSurface = SatraColors.InkSoft,
    inverseOnSurface = SatraColors.TextLight,
    inversePrimary = SatraColors.PurpleSoft,
    scrim = Color(0xFF000000),
    surfaceTint = SatraColors.Purple,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F5FC),
    surfaceContainer = Color(0xFFF1ECF8),
    surfaceContainerHigh = Color(0xFFE9E3F2),
    surfaceContainerHighest = Color(0xFFE0DAEB),
)

private val DarkColor = darkColorScheme(
    primary = SatraColors.PurpleSoft,
    onPrimary = SatraColors.Ink,
    primaryContainer = SatraColors.PurpleMist,
    onPrimaryContainer = SatraColors.PurpleSoft,
    secondary = SatraColors.Teal,
    onSecondary = SatraColors.Ink,
    secondaryContainer = Color(0xFF16324A),
    onSecondaryContainer = SatraColors.TealSoft,
    tertiary = SatraColors.AmberSoft,
    onTertiary = SatraColors.Ink,
    tertiaryContainer = Color(0xFF5A3A08),
    onTertiaryContainer = SatraColors.AmberSoft,
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    // Pure dark with a quiet purple undertone — not a purple screen.
    background = Color(0xFF000000),
    onBackground = SatraColors.TextLight,
    surface = Color(0xFF08060C),
    onSurface = SatraColors.TextLight,
    surfaceVariant = Color(0xFF1A1624),
    onSurfaceVariant = SatraColors.TextMutedLight,
    outline = Color(0xFF3A3A44),
    outlineVariant = Color(0xFF222228),
    inverseSurface = SatraColors.Mist,
    inverseOnSurface = SatraColors.InkSoft,
    inversePrimary = SatraColors.PurpleDeep,
    scrim = Color(0xFF000000),
    surfaceTint = SatraColors.Purple,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0810),
    surfaceContainer = Color(0xFF121018),
    surfaceContainerHigh = Color(0xFF1A1622),
    surfaceContainerHighest = Color(0xFF24202C),
)

val colorPing = SatraColors.TealDeep
val colorPingRed = SatraColors.Danger
val colorConfigType = SatraColors.Amber
val colorFabActive = SatraColors.Teal
val colorFabInactiveLight = Color(0xFF8FA0B0)
val colorFabInactiveDark = Color(0xFF3A4E60)
val dividerColorLight = Color(0xFFD5DEE8)
val dividerColorDark = Color(0xFF24384C)

val toastNormalBgLight = Color(0xD91A2A3A)
val toastNormalBgDark = Color(0xD924384C)
val toastSuccessBg = Color(0xD90F9F7E)
val toastErrorBg = Color(0xD9E5484D)
val toastInfoBg = Color(0xD914283C)
val toastIconCircleBg = Color(0x33FFFFFF)
val toastTextColor = Color.White

private val SatraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    ),
)

fun satraBackgroundBrush(dark: Boolean): Brush =
    com.v2ray.ang.ui.compose.satra.SatraTokens.backgroundBrush(dark)

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        // Satra brand colors must win over Material You dynamic schemes by default.
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SatraTypography,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
