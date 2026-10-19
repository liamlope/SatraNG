package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CdnRangesTest {
    @Test
    fun matchesBundledRanges() {
        assertEquals(CdnRanges.Cdn.CLOUDFLARE, CdnRanges.match("104.18.12.34"))
        assertEquals(CdnRanges.Cdn.CLOUDFLARE, CdnRanges.match("2606:4700::6810:1"))
        assertEquals(CdnRanges.Cdn.FASTLY, CdnRanges.match("151.101.1.69"))
        assertEquals(CdnRanges.Cdn.ARVAN, CdnRanges.match("185.143.233.5"))
    }

    @Test
    fun ignoresOtherAddressesAndHostnames() {
        assertNull(CdnRanges.match("8.8.8.8"))
        assertNull(CdnRanges.match("104.32.0.1"))
        assertNull(CdnRanges.match("example.com"))
        assertNull(CdnRanges.match(null))
    }

    @Test
    fun tunnelMeansIranianEntryForeignExitWithoutCdn() {
        assertTrue(CdnRanges.isTunnel("IR", "DE", null))
        assertFalse(CdnRanges.isTunnel("IR", "IR", null))
        assertFalse(CdnRanges.isTunnel("DE", "DE", null))
        assertFalse(CdnRanges.isTunnel("IR", "DE", CdnRanges.Cdn.ARVAN))
        assertFalse(CdnRanges.isTunnel("IR", null, null))
    }
}
