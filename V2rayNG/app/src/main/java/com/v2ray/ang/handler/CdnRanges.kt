package com.v2ray.ang.handler

import java.net.InetAddress

/**
 * Recognises configs whose server address is a CDN edge (domain fronting through Cloudflare,
 * Fastly or ArvanCloud), from bundled published IP ranges. Cloudflare's list can be refreshed
 * from cloudflare.com ([refreshIfStale]); the bundled lists are always the fallback.
 */
object CdnRanges {

    enum class Cdn(val label: String) { CLOUDFLARE("Cloudflare"), FASTLY("Fastly"), ARVAN("ArvanCloud") }

    private val bundled: Map<Cdn, List<String>> = mapOf(
        Cdn.CLOUDFLARE to listOf(
            "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22", "141.101.64.0/18",
            "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20", "197.234.240.0/22", "198.41.128.0/17",
            "162.158.0.0/15", "104.16.0.0/13", "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22",
            "2400:cb00::/32", "2606:4700::/32", "2803:f800::/32", "2405:b500::/32", "2405:8100::/32",
            "2a06:98c0::/29", "2c0f:f248::/32",
        ),
        Cdn.FASTLY to listOf(
            "23.235.32.0/20", "43.249.72.0/22", "103.244.50.0/24", "103.245.222.0/23", "103.245.224.0/24",
            "104.156.80.0/20", "140.248.64.0/18", "140.248.128.0/17", "146.75.0.0/17", "151.101.0.0/16",
            "157.52.64.0/18", "167.82.0.0/17", "167.82.128.0/20", "167.82.160.0/20", "167.82.224.0/20",
            "172.111.64.0/18", "185.31.16.0/22", "199.27.72.0/21", "199.232.0.0/16",
            "2a04:4e40::/32", "2a04:4e42::/32",
        ),
        Cdn.ARVAN to listOf(
            "185.143.232.0/22", "188.229.116.16/29", "94.101.182.0/27", "2.144.3.128/28", "89.45.48.64/28",
            "37.32.16.0/27", "37.32.17.0/27", "37.32.18.0/27", "37.32.19.0/27", "185.215.232.0/22",
        ),
    )

    private class Block(val net: ByteArray, val bits: Int)

    @Volatile private var blocks: Map<Cdn, List<Block>> = compile(bundled)

    private fun compile(src: Map<Cdn, List<String>>): Map<Cdn, List<Block>> =
        src.mapValues { (_, list) -> list.mapNotNull(::parse) }

    private fun parse(cidr: String): Block? {
        val parts = cidr.trim().split('/')
        if (parts.size != 2) return null
        val addr = literal(parts[0]) ?: return null
        val bits = parts[1].toIntOrNull() ?: return null
        if (bits < 0 || bits > addr.size * 8) return null
        return Block(addr, bits)
    }

    /** Parses an IP literal only (never a DNS lookup). */
    private fun literal(ip: String): ByteArray? {
        val s = ip.trim().removePrefix("[").removeSuffix("]")
        if (!GeoIpResolver.isIpLiteral(s)) return null
        return try { InetAddress.getByName(s).address } catch (_: Exception) { null }
    }

    private fun contains(b: Block, addr: ByteArray): Boolean {
        if (b.net.size != addr.size) return false
        var bits = b.bits
        var i = 0
        while (bits > 0) {
            val mask = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
            if ((b.net[i].toInt() and mask) != (addr[i].toInt() and mask)) return false
            bits -= 8; i++
        }
        return true
    }

    /** The CDN that [ip] belongs to, or null. */
    fun match(ip: String?): Cdn? {
        val addr = literal(ip ?: return null) ?: return null
        for ((cdn, list) in blocks) if (list.any { contains(it, addr) }) return cdn
        return null
    }

    // ---- optional refresh of Cloudflare's list ----

    private const val KEY_CF = "satra_cdn_cf_ranges"
    private const val KEY_AT = "satra_cdn_ranges_at"
    private const val REFRESH_MS = 7L * 24 * 60 * 60 * 1000

    /** Loads a previously downloaded list, if any. Call once at startup. */
    fun loadStored() {
        val stored = MmkvManager.decodeSettingsString(KEY_CF).orEmpty().lines().filter { it.isNotBlank() }
        if (stored.size >= 5) blocks = compile(bundled + (Cdn.CLOUDFLARE to stored))
    }

    /** At most weekly; off the main thread. Keeps the bundled list on any failure. */
    fun refreshIfStale(httpPort: Int = 0) {
        val now = System.currentTimeMillis()
        if (now - MmkvManager.decodeSettingsLong(KEY_AT, 0L) < REFRESH_MS) return
        MmkvManager.encodeSettings(KEY_AT, now)
        val lines = listOf("https://www.cloudflare.com/ips-v4", "https://www.cloudflare.com/ips-v6").flatMap { url ->
            com.v2ray.ang.util.HttpUtil.getUrlContent(com.v2ray.ang.dto.UrlContentRequest(url = url, timeout = 5000, httpPort = httpPort))
                ?.lines()?.map { it.trim() }?.filter { parse(it) != null }.orEmpty()
        }
        if (lines.size >= 5) {
            MmkvManager.encodeSettings(KEY_CF, lines.joinToString("\n"))
            blocks = compile(bundled + (Cdn.CLOUDFLARE to lines))
        }
    }

    /** Server in Iran but traffic leaves abroad: a tunnel / relay config. CDN fronting is not a tunnel. */
    fun isTunnel(entryCountry: String?, exitCountry: String?, cdn: Cdn?): Boolean =
        cdn == null && entryCountry.equals("IR", true) && !exitCountry.isNullOrBlank() && !exitCountry.equals("IR", true)
}
