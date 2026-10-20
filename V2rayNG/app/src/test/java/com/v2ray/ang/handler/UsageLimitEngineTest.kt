package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
