package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Country shown on a config row.
 *
 * Order of truth, never the remark text:
 *  1. **Exit IP** measured by sending a request *through that config* ([ExitIpProbe] / after connect).
 *  2. **Server address** resolved to an IP and looked up (approximate; CDN fronted or relay configs
 *     can differ from the real exit), shown only until an exit measurement exists.
 *  3. Unknown: the UI shows a neutral globe.
 *
 * Reads are pure MMKV (safe on the UI thread). All network work is explicit and off-thread.
 */
object GeoIpResolver {

    const val EXIT_TTL_MS = 7L * 24 * 60 * 60 * 1000
    const val HOST_TTL_MS = 3L * 24 * 60 * 60 * 1000
    /** A ping test re-measures exits older than this. */
    const val EXIT_REFRESH_ON_PING_MS = 6L * 60 * 60 * 1000
    const val ATTEMPT_BACKOFF_MS = 10L * 60 * 1000

    data class GeoResult(
        val countryCode: String?,
        val countryName: String?,
        val ip: String?,
        val exitVerified: Boolean = false,
    ) {
        companion object { val UNKNOWN = GeoResult(null, null, null) }
    }

    private val attempts = ConcurrentHashMap<String, Long>()

    // ---- pure policy (unit tested) ----

    fun isFresh(aff: ServerAffiliationInfo?, nowMs: Long): Boolean {
        aff ?: return false
        if (aff.countryCode.isNullOrBlank()) return false
        val ttl = if (aff.exitIpVerified) EXIT_TTL_MS else HOST_TTL_MS
        return nowMs - aff.geoUpdatedAt < ttl
    }

    /** True when a background lookup should run for this config now. */
    fun needsLookup(aff: ServerAffiliationInfo?, nowMs: Long, force: Boolean = false): Boolean {
        if (force) return !(aff?.exitIpVerified == true && nowMs - (aff.geoUpdatedAt) < EXIT_REFRESH_ON_PING_MS)
        if (isFresh(aff, nowMs) && aff?.exitIpVerified == true) return false
        val last = aff?.geoAttemptAt ?: 0L
        if (nowMs - last < ATTEMPT_BACKOFF_MS) return false
        return !isFresh(aff, nowMs) || aff?.exitIpVerified != true
    }

    // ---- UI read path ----

    fun displayForProfile(guid: String, profile: ProfileItem): GeoResult = display(MmkvManager.decodeServerAffiliationInfo(guid))

    internal fun display(aff: ServerAffiliationInfo?, nowMs: Long = System.currentTimeMillis()): GeoResult {
        if (!isFresh(aff, nowMs)) return GeoResult.UNKNOWN
        aff!!
        return GeoResult(aff.countryCode, aff.countryName, aff.resolvedIp, aff.exitIpVerified)
    }

    // ---- network paths (call off the main thread) ----

    /** Persists a measured exit IP for [guid]. */
    fun storeExit(guid: String, ip: String?, rawCountry: String?): GeoResult {
        val code = normalizeCountryCode(rawCountry.orEmpty())
        if (code == null) {
            markAttempt(guid)
            return GeoResult.UNKNOWN
        }
        val name = countryNameForCode(code)
        MmkvManager.encodeServerGeo(guid, code, name, ip, exitIpVerified = true)
        return GeoResult(code, name, ip, true)
    }

    /** After the VPN is up, ask through the local proxy where the traffic really leaves. */
    fun resolveExitAfterConnect(guid: String): GeoResult {
        if (guid.isBlank()) return GeoResult.UNKNOWN
        return try {
            val remote = SpeedtestManager.getRemoteIPInfo()
            storeExit(guid, remote?.ipAddress, remote?.country)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Exit geo lookup failed: ${e.message}")
            GeoResult.UNKNOWN
        }
    }

    /**
     * Fallback when no exit measurement exists: resolve the server address and look the IP up.
     * Never overwrites a fresh exit-verified value.
     */
    fun resolveHostSoft(guid: String, profile: ProfileItem, viaHttpPort: Int = 0): GeoResult {
        val now = System.currentTimeMillis()
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        if (aff?.exitIpVerified == true && isFresh(aff, now)) return display(aff, now)
        if (!needsLookup(aff, now)) return display(aff, now)
        val last = attempts[guid] ?: 0L
        if (now - last < ATTEMPT_BACKOFF_MS) return display(aff, now)
        attempts[guid] = now
        markAttempt(guid)

        val host = profile.server?.trim().orEmpty()
        if (host.isBlank()) return GeoResult.UNKNOWN
        return try {
            val ip = if (isIpLiteral(host)) host else InetAddress.getByName(host).hostAddress
            if (ip.isNullOrBlank()) return GeoResult.UNKNOWN
            val code = lookupIp(ip, viaHttpPort)
            if (code == null) GeoResult.UNKNOWN else {
                val name = countryNameForCode(code)
                MmkvManager.encodeServerGeo(guid, code, name, ip, exitIpVerified = false)
                GeoResult(code, name, ip, false)
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Host geo failed for ${host}: ${e.message}")
            GeoResult.UNKNOWN
        }
    }

    private fun markAttempt(guid: String) {
        MmkvManager.markServerGeoAttempt(guid)
    }

    private val geoEndpoints = listOf(
        "https://ipwho.is/%s",
        "https://ipapi.co/%s/json/",
    )

    private fun lookupIp(ip: String, httpPort: Int): String? {
        for (pattern in geoEndpoints) {
            for (port in listOf(httpPort, 0).distinct()) {
                val body = HttpUtil.getUrlContent(UrlContentRequest(url = pattern.format(ip), timeout = 4000, httpPort = port))
                    ?: continue
                val info = JsonUtil.fromJsonSafe(body, IPAPIInfo::class.java) ?: continue
                val code = normalizeCountryCode(
                    listOf(info.country_code, info.countryCode, info.country).firstOrNull { !it.isNullOrBlank() }.orEmpty(),
                )
                if (code != null) return code
            }
        }
        return null
    }

    internal fun isIpLiteral(host: String): Boolean =
        host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) || (host.contains(':') && host.matches(Regex("^[0-9a-fA-F:.%]+$")))

    internal fun normalizeCountryCode(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val upper = s.uppercase(Locale.US)
        if (upper.length == 2 && Locale.getISOCountries().contains(upper)) return upper
        return Locale.getISOCountries().firstOrNull { code ->
            Locale("", code).getDisplayCountry(Locale.ENGLISH).equals(s, ignoreCase = true)
        }
    }

    fun countryNameForCode(code: String?): String? {
        if (code.isNullOrBlank()) return null
        return try {
            Locale("", code).displayCountry.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            code
        }
    }

    fun flagEmoji(countryCode: String?): String {
        val cc = countryCode?.uppercase(Locale.US) ?: return "🌐"
        if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return "🌐"
        val first = Character.codePointAt(cc, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(cc, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
