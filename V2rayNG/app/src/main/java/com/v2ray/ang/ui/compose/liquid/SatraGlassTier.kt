package com.v2ray.ang.ui.compose.liquid

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.v2ray.ang.ui.compose.satra.isReduceMotionEnabled
import dev.liquidglass.core.GlassRenderTier

/**
 * Maps Abdullajon [GlassRenderTier] for Satra, honoring Reduce Transparency / Motion.
 */
enum class SatraGlassFidelity {
    Shader,
    Blur,
    Scrim,
}

fun GlassRenderTier.toSatra(): SatraGlassFidelity = when (this) {
    GlassRenderTier.SHADER -> SatraGlassFidelity.Shader
    GlassRenderTier.BLUR -> SatraGlassFidelity.Blur
    GlassRenderTier.SCRIM -> SatraGlassFidelity.Scrim
}

fun SatraGlassFidelity.toAbdullajon(): GlassRenderTier = when (this) {
    SatraGlassFidelity.Shader -> GlassRenderTier.SHADER
    SatraGlassFidelity.Blur -> GlassRenderTier.BLUR
    SatraGlassFidelity.Scrim -> GlassRenderTier.SCRIM
}

/**
 * Device capability capped by optional accessibility downgrade.
 * Pure mapping — unit-tested without Android framework.
 */
fun resolveSatraGlassTier(
    apiLevel: Int,
    reduceTransparencyOrMotion: Boolean,
): SatraGlassFidelity {
    val requested = if (reduceTransparencyOrMotion) GlassRenderTier.SCRIM else null
    return GlassRenderTier.select(apiLevel, requested).toSatra()
}

@Composable
fun rememberSatraGlassTier(): SatraGlassFidelity {
    val context = LocalContext.current
    return remember(context) {
        resolveSatraGlassTier(
            apiLevel = Build.VERSION.SDK_INT,
            reduceTransparencyOrMotion = context.isReduceMotionEnabled(),
        )
    }
}
