package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeoIpResolverTest {

    @Test
    fun flagEmoji_buildsRegionalIndicator() {
        assertEquals("🇩🇪", GeoIpResolver.flagEmoji("DE"))
        assertEquals("🌐", GeoIpResolver.flagEmoji(null))
        assertEquals("🌐", GeoIpResolver.flagEmoji("XYZ"))
    }

    @Test
    fun countryNameForCode_returnsLocalizedName() {
        val name = GeoIpResolver.countryNameForCode("DE")
        assertTrue(name != null && name.isNotBlank())
    }

    @Test
    fun guessFromRemarks_usesTokensNotBlindIso() {
        val nl = GeoIpResolver.guessFromRemarks("Digi-Tunnel Netherlands")
        assertEquals("NL", nl?.countryCode)

        val bracket = GeoIpResolver.guessFromRemarks("Edge [DE] node")
        assertEquals("DE", bracket?.countryCode)

        // Protocol-ish "CF" must not become Central African Republic.
        val cloudflareish = GeoIpResolver.guessFromRemarks("CF-p.iivt")
        assertEquals(null, cloudflareish)
    }
}
