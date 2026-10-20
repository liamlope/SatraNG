package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
// ProcNetUidScanner + AppUsageReader helpers

class UsageLimitEngineTest {

    @Test
    fun expandSitePresetDomains_addsYoutubeCdnAndGeosite() {
        val out = UsageLimitEngine.expandSitePresetDomains(listOf("youtube.com"))
        assertTrue(out.any { it == "domain:youtube.com" || it.contains("youtube.com") })
        assertTrue(out.any { it.contains("googlevideo.com") })
        assertTrue(out.any { it.contains("ytimg.com") })
        assertTrue(out.any { it.contains("youtubei.googleapis.com") })
        assertTrue(out.contains("geosite:youtube"))
    }

    @Test
    fun expandSitePresetDomains_leavesUnrelatedSitesAlone() {
        val out = UsageLimitEngine.expandSitePresetDomains(listOf("example.com"))
        assertTrue(out.size == 1)
        assertTrue(out.single().contains("example.com"))
        assertTrue(out.none { it.contains("geosite:youtube") })
    }

    @Test
    fun mergeMaxByUid_takesLargerCountersPerUid() {
        val a = listOf(AppUsage(10, null, 100, 50), AppUsage(20, null, 5, 5))
        val b = listOf(AppUsage(10, null, 80, 90), AppUsage(30, null, 1, 2))
        val merged = AppUsageReader.mergeMaxByUid(a, b).associateBy { it.uid }
        assertEquals(100L, merged[10]!!.rxBytes)
        assertEquals(90L, merged[10]!!.txBytes)
        assertEquals(5L, merged[20]!!.rxBytes)
        assertEquals(1L, merged[30]!!.rxBytes)
        assertEquals(2L, merged[30]!!.txBytes)
    }

    @Test
    fun distributeByWeight_splitsAllBytesAcrossUids() {
        val shares = ProcNetUidScanner.distributeByWeight(1000L, mapOf(10 to 1, 20 to 3))
        assertEquals(1000L, shares.values.sum())
        assertEquals(250L, shares[10])
        assertEquals(750L, shares[20])
    }

    @Test
    fun distributeByWeight_emptyWeightsYieldsEmpty() {
        assertTrue(ProcNetUidScanner.distributeByWeight(100L, emptyMap()).isEmpty())
        assertTrue(ProcNetUidScanner.distributeByWeight(0L, mapOf(1 to 1)).isEmpty())
    }

    @Test
    fun uidTunnelMeter_tagRoundTrip() {
        assertEquals("appuid-10561", UidTunnelMeter.tagForUid(10561))
        assertEquals(10561, UidTunnelMeter.parseUid("appuid-10561"))
        assertEquals(null, UidTunnelMeter.parseUid("proxy"))
    }
}
