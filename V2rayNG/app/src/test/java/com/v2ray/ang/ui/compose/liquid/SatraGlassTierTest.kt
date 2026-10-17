package com.v2ray.ang.ui.compose.liquid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SatraGlassTierTest {

    @Test
    fun api34UsesShader() {
        assertEquals(SatraGlassFidelity.Shader, resolveSatraGlassTier(34, reduceTransparencyOrMotion = false))
    }

    @Test
    fun api31UsesBlur() {
        assertEquals(SatraGlassFidelity.Blur, resolveSatraGlassTier(31, reduceTransparencyOrMotion = false))
    }

    @Test
    fun api24UsesScrim() {
        assertEquals(SatraGlassFidelity.Scrim, resolveSatraGlassTier(24, reduceTransparencyOrMotion = false))
    }

    @Test
    fun reduceMotionCapsToScrim() {
        assertEquals(SatraGlassFidelity.Scrim, resolveSatraGlassTier(34, reduceTransparencyOrMotion = true))
    }
}
