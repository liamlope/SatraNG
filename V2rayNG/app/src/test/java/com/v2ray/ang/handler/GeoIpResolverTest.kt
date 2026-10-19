package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeoIpResolverTest {
    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun aff(code: String?, verified: Boolean, ageMs: Long, attemptAgeMs: Long = 100 * day) = ServerAffiliationInfo(
        countryCode = code, exitIpVerified = verified, geoUpdatedAt = now - ageMs, geoAttemptAt = now - attemptAgeMs,
    )

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
    fun display_neverGuessesFromRemarks_unknownIsGlobe() {
        assertNull(GeoIpResolver.display(null, now).countryCode)
        assertNull(GeoIpResolver.display(aff(null, false, 0), now).countryCode)
    }

    @Test
    fun display_respectsTtlPerSource() {
        assertEquals("DE", GeoIpResolver.display(aff("DE", true, 6 * day), now).countryCode)
        assertNull(GeoIpResolver.display(aff("DE", true, 8 * day), now).countryCode)
        assertEquals("NL", GeoIpResolver.display(aff("NL", false, 2 * day), now).countryCode)
        assertNull(GeoIpResolver.display(aff("NL", false, 4 * day), now).countryCode)
        assertTrue(GeoIpResolver.display(aff("DE", true, day), now).exitVerified)
    }

    @Test
    fun needsLookup_backgroundRespectsBackoffAndVerifiedCache() {
        // nothing known -> look it up
        assertTrue(GeoIpResolver.needsLookup(null, now))
        // verified and fresh -> never in background
        assertFalse(GeoIpResolver.needsLookup(aff("DE", true, day), now))
        // soft guess -> upgrade to an exit measurement, unless we tried a minute ago
        assertTrue(GeoIpResolver.needsLookup(aff("NL", false, day), now))
        assertFalse(GeoIpResolver.needsLookup(aff("NL", false, day, attemptAgeMs = 60_000), now))
    }

    @Test
    fun needsLookup_pingTestRefreshesOldExitsOnly() {
        assertFalse(GeoIpResolver.needsLookup(aff("DE", true, 60 * 60 * 1000), now, force = true))
        assertTrue(GeoIpResolver.needsLookup(aff("DE", true, 7 * 60 * 60 * 1000), now, force = true))
        assertTrue(GeoIpResolver.needsLookup(aff("NL", false, 1000), now, force = true))
    }

    @Test
    fun normalizeCountryCode_acceptsIsoAndEnglishNames() {
        assertEquals("DE", GeoIpResolver.normalizeCountryCode("de"))
        assertEquals("NL", GeoIpResolver.normalizeCountryCode("Netherlands"))
        assertNull(GeoIpResolver.normalizeCountryCode("ZZ"))
        assertNull(GeoIpResolver.normalizeCountryCode(""))
    }

    @Test
    fun isIpLiteral_handlesV4AndV6() {
        assertTrue(GeoIpResolver.isIpLiteral("1.2.3.4"))
        assertTrue(GeoIpResolver.isIpLiteral("2001:db8::1"))
        assertFalse(GeoIpResolver.isIpLiteral("example.com"))
    }
}
