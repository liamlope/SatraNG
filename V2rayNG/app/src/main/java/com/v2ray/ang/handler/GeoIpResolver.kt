package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a profile's server host to a country code (cached in affiliation + memory).
 */
object GeoIpResolver {

    private val memoryCache = ConcurrentHashMap<String, Pair<String?, String?>>()
    private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

    data class GeoResult(
        val countryCode: String?,
        val countryName: String?,
        val ip: String?,
    )

    fun resolveForProfile(guid: String, profile: ProfileItem, force: Boolean = false): GeoResult {
        if (!force) {
            MmkvManager.decodeServerAffiliationInfo(guid)?.let { aff ->
                if (!aff.countryCode.isNullOrBlank() &&
                    System.currentTimeMillis() - aff.geoUpdatedAt < CACHE_TTL_MS
                ) {
                    return GeoResult(aff.countryCode, aff.countryName, aff.resolvedIp)
                }
            }
        }

        val host = profile.server?.trim().orEmpty()
        if (host.isBlank()) return GeoResult(null, null, null)

        memoryCache[host.lowercase(Locale.US)]?.let { (code, name) ->
            MmkvManager.encodeServerGeo(guid, code, name, null)
            return GeoResult(code, name, null)
        }

        // Hint from remarks (e.g. "DE-Frankfurt", "Germany", flag emoji region)
        guessFromRemarks(profile.remarks)?.let { hinted ->
            memoryCache[host.lowercase(Locale.US)] = hinted.countryCode to hinted.countryName
            MmkvManager.encodeServerGeo(guid, hinted.countryCode, hinted.countryName, null)
            // Still try network resolve in background-friendly path below if needed
            if (!force) return hinted
        }

        val ip = resolveHostToIp(host)
        val remote = lookupGeo(ip ?: host)
        val code = remote?.countryCode?.uppercase(Locale.US)
        val name = remote?.countryName ?: countryNameForCode(code)
        memoryCache[host.lowercase(Locale.US)] = code to name
        MmkvManager.encodeServerGeo(guid, code, name, ip ?: remote?.ip)
        return GeoResult(code, name, ip ?: remote?.ip)
    }

    private fun resolveHostToIp(host: String): String? = runCatching {
        InetAddress.getByName(host).hostAddress
    }.onFailure {
        LogUtil.w(AppConfig.TAG, "GeoIp DNS failed for $host: ${it.message}")
    }.getOrNull()

    private fun lookupGeo(query: String): GeoResult? {
        return try {
            val api = AppConfig.IP_API_URL.trimEnd('/')
            val url = if (query.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) || query.contains(":")) {
                "$api/$query"
            } else {
                "$api/$query"
            }
            val content = HttpUtil.getUrlContent(
                UrlContentRequest(url = url, timeout = 4000, httpPort = 0)
            ) ?: return null
            val info = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null
            val code = listOf(info.country_code, info.countryCode, info.location?.country_code)
                .firstOrNull { !it.isNullOrBlank() }
                ?.uppercase(Locale.US)
            val name = listOf(info.country_name, info.country)
                .firstOrNull { !it.isNullOrBlank() }
                ?: countryNameForCode(code)
            val ip = listOf(info.ip, info.clientIp, info.ip_addr, info.query)
                .firstOrNull { !it.isNullOrBlank() }
            GeoResult(code, name, ip)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "GeoIp lookup failed: ${e.message}")
            null
        }
    }

    private fun guessFromRemarks(remarks: String): GeoResult? {
        val upper = remarks.uppercase(Locale.US)
        // ISO-like token at start: "DE-", "US ", "NL|"
        val iso = Regex("""\b([A-Z]{2})\b""").find(upper)?.groupValues?.getOrNull(1)
        if (iso != null && Locale.getISOCountries().contains(iso)) {
            return GeoResult(iso, countryNameForCode(iso), null)
        }
        val names = mapOf(
            "GERMANY" to "DE", "FRANKFURT" to "DE", "NETHERLANDS" to "NL", "AMSTERDAM" to "NL",
            "FRANCE" to "FR", "PARIS" to "FR", "UNITED STATES" to "US", "USA" to "US",
            "TURKEY" to "TR", "ISTANBUL" to "TR", "IRAN" to "IR", "TEHRAN" to "IR",
            "UK" to "GB", "LONDON" to "GB", "SINGAPORE" to "SG", "JAPAN" to "JP", "TOKYO" to "JP",
            "CANADA" to "CA", "SWEDEN" to "SE", "FINLAND" to "FI", "POLAND" to "PL",
        )
        for ((token, code) in names) {
            if (upper.contains(token)) {
                return GeoResult(code, countryNameForCode(code), null)
            }
        }
        return null
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
