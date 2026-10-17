package com.v2ray.ang.ui.compose.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.v2ray.ang.ui.compose.satra.isReduceMotionEnabled
import dev.liquidglass.compose.LocalLiquidGlassTier
import dev.liquidglass.compose.LiquidGlassProviderState
import dev.liquidglass.compose.liquidGlassProvider
import dev.liquidglass.compose.rememberLiquidGlassProviderState
import dev.liquidglass.core.GlassRenderTier

val LocalSatraGlassState = compositionLocalOf<LiquidGlassProviderState?> { null }

/**
 * Root glass backdrop provider. Place once behind chrome; children use [LocalSatraGlassState].
 */
@Composable
fun SatraGlassProvider(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberLiquidGlassProviderState()
    val tier = rememberSatraGlassTier()
    val context = LocalContext.current
    if (context.isReduceMotionEnabled()) {
        state.requestedTier = GlassRenderTier.SCRIM
    } else {
        state.requestedTier = null
    }
    CompositionLocalProvider(
        LocalSatraGlassState provides state,
        LocalLiquidGlassTier provides tier.toAbdullajon(),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .liquidGlassProvider(state),
            content = content,
        )
    }
}

@Composable
fun rememberSatraGlassStateOrNull(): LiquidGlassProviderState? = LocalSatraGlassState.current
