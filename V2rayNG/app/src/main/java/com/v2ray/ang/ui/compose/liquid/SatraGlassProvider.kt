package com.v2ray.ang.ui.compose.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Formerly backed liquid-glass captures. Glass effect is removed — this is a plain
 * full-size container so existing call sites keep compiling without refraction.
 */
val LocalSatraGlassState = compositionLocalOf<Any?> { null }

@Composable
fun SatraGlassProvider(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalSatraGlassState provides null) {
        Box(modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun rememberSatraGlassStateOrNull(): Any? = LocalSatraGlassState.current
