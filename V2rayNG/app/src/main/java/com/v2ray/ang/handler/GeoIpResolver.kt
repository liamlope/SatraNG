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
 * Country display for servers.
 *
 * Prefer exit-IP verified after connect; otherwise soft hints from remarks or
 * host geo after a successful ping (never overwrites a fresh exit-verified value).
 */
object GeoIpResolver {

    private val memoryCache = ConcurrentHashMap<String, Pair<String?, String?>>()
    private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

    data class GeoResult(
        val countryCode: String?,
        val countryName: String?,
        val ip: String?,
        val exitVerified: Boolean = false,
    )

    /**
     * UI-facing geo: any fresh cached country code, else remarks hint.
     * Never DNS-resolves on the UI thread.
     */
    fun displayForProfile(guid: String, profile: ProfileItem): GeoResult {
        MmkvManager.decodeServerAffiliationInfo(guid)?.let { aff ->
            if (!aff.countryCode.isNullOrBlank() &&
                System.currentTimeMillis() - aff.geoUpdatedAt < CACHE_TTL_MS
            ) {
                return GeoResult(
                    aff.countryCode,
                    aff.countryName,
                    aff.resolvedIp,
                    exitVerified = aff.exitIpVerified,
                )
            }
        }
        return guessFromRemarks(profile.remarks) ?: GeoResult(null, null, null)
    }

    /**
     * @deprecated Host-based geo is incorrect for tunnel configs; use [displayForProfile]
     * and [resolveExitAfterConnect] instead.
     */
    fun resolveForProfile(guid: String, profile: ProfileItem, force: Boolean = false): GeoResult {
        return displayForProfile(guid, profile)
    }

    /**
     * After VPN/proxy is up, query the public IP through the local SOCKS/HTTP proxy
     * and persist that country as the location flag for [guid].
     */
    fun resolveExitAfterConnect(guid: String): GeoResult {
        if (guid.isBlank()) return GeoResult(null, null, null)
        return try {
            val remote = SpeedtestManager.getRemoteIPInfo()
            val rawCountry = remote?.country?.trim().orEmpty()
            val code = normalizeCountryCode(rawCountry)
            val name = when {
                code != null -> countryNameForCode(code)
                rawCountry.length > 2 -> rawCountry
                else -> null
            }
            val ip = remote?.ipAddress
            if (code != null || ip != null) {
                MmkvManager.encodeServerGeo(
                    guid = guid,
                    countryCode = code,
                    countryName = name,
                    resolvedIp = ip,
                    exitIpVerified = true,
                )
                if (code != null) {
                    memoryCache[guid] = code to name
                }
            }
            GeoResult(code, name, ip, exitVerified = code != null)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Exit geo lookup failed: ${e.message}")
            GeoResult(null, null, null)
        }
    }

    /**
     * After a successful real-ping, enrich flag for [guid] without touching a fresh
     * exit-verified cache. Uses remarks first, then soft host geo.
     */
    fun enrichAfterPing(guid: String, profile: ProfileItem): GeoResult {
        if (guid.isBlank()) return GeoResult(null, null, null)
        MmkvManager.decodeServerAffiliationInfo(guid)?.let { aff ->
            if (aff.exitIpVerified &&
                !aff.countryCode.isNullOrBlank() &&
                System.currentTimeMillis() - aff.geoUpdatedAt < CACHE_TTL_MS
            ) {
                return GeoResult(aff.countryCode, aff.countryName, aff.resolvedIp, exitVerified = true)
            }
        }
        guessFromRemarks(profile.remarks)?.let { guessed ->
            if (!guessed.countryCode.isNullOrBlank()) {
                persistSoft(guid, guessed)
                return guessed
            }
        }
        val soft = resolveHostSoft(profile)
        if (!soft.countryCode.isNullOrBlank()) {
            persistSoft(guid, soft)
        }
        return soft
    }

    private fun persistSoft(guid: String, result: GeoResult) {
        MmkvManager.encodeServerGeo(
            guid = guid,
            countryCode = result.countryCode,
            countryName = result.countryName,
            resolvedIp = result.ip,
            exitIpVerified = false,
        )
        if (result.countryCode != null) {
            memoryCache[guid] = result.countryCode to result.countryName
        }
    }

    /** Soft host geo (direct IP API) — fallback when remarks have no country hint. */
    fun resolveHostSoft(profile: ProfileItem): GeoResult {
        val host = profile.server?.trim().orEmpty()
        if (host.isBlank()) return GeoResult(null, null, null)
        return try {
            val ip = when {
                host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) -> host
                else -> InetAddress.getByName(host).hostAddress
            } ?: return GeoResult(null, null, null)
            val url = "http://ip-api.com/json/$ip?fields=status,country,countryCode,query"
            val body = HttpUtil.getUrlContent(
                UrlContentRequest(url = url, timeout = 3500, httpPort = 0),
            ) ?: return GeoResult(null, null, ip)
            val info = JsonUtil.fromJsonSafe(body, IPAPIInfo::class.java) ?: return GeoResult(null, null, ip)
            val code = normalizeCountryCode(
                listOf(info.country_code, info.countryCode, info.country)
                    .firstOrNull { !it.isNullOrBlank() }
                    .orEmpty(),
            )
            GeoResult(code, code?.let { countryNameForCode(it) }, info.query ?: ip, exitVerified = false)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Host soft geo failed: ${e.message}")
            GeoResult(null, null, null)
        }
    }

    private fun normalizeCountryCode(raw: String): String? {
        if (raw.isBlank()) return null
        val upper = raw.uppercase(Locale.US)
        if (upper.length == 2 && Locale.getISOCountries().contains(upper)) return upper
        return Locale.getISOCountries().firstOrNull { code ->
            Locale("", code).displayCountry.equals(raw, ignoreCase = true)
        }
    }

    /**
     * Remarks-based hint. Avoids blind `[A-Z]{2}` extraction (CF/WS/SS false flags).
     * Accepts explicit `[NL]` / `(DE)` brackets and known city/country tokens.
     */
    internal fun guessFromRemarks(remarks: String): GeoResult? {
        if (remarks.isBlank()) return null
        val upper = remarks.uppercase(Locale.US)
        Regex("""[\[(]([A-Z]{2})[\])]""").find(upper)?.groupValues?.getOrNull(1)?.let { iso ->
            if (Locale.getISOCountries().contains(iso)) {
                return GeoResult(iso, countryNameForCode(iso), null)
            }
        }
        val names = mapOf(
            "GERMANY" to "DE", "FRANKFURT" to "DE", "BERLIN" to "DE", "MUNICH" to "DE",
            "NETHERLANDS" to "NL", "HOLLAND" to "NL", "AMSTERDAM" to "NL",
            "FRANCE" to "FR", "PARIS" to "FR",
            "UNITED STATES" to "US", "USA" to "US", "AMERICA" to "US", "LOS ANGELES" to "US",
            "NEW YORK" to "US", "CHICAGO" to "US", "MIAMI" to "US", "SEATTLE" to "US",
            "TURKEY" to "TR", "ISTANBUL" to "TR",
            "IRAN" to "IR", "TEHRAN" to "IR",
            "UNITED KINGDOM" to "GB", "LONDON" to "GB", "ENGLAND" to "GB",
            "SINGAPORE" to "SG",
            "JAPAN" to "JP", "TOKYO" to "JP",
            "CANADA" to "CA", "TORONTO" to "CA",
            "SWEDEN" to "SE", "STOCKHOLM" to "SE",
            "FINLAND" to "FI", "HELSINKI" to "FI",
            "POLAND" to "PL", "WARSAW" to "PL",
            "HONG KONG" to "HK",
            "KOREA" to "KR", "SEOUL" to "KR",
            "AUSTRALIA" to "AU", "SYDNEY" to "AU",
            "BRAZIL" to "BR", "SAO PAULO" to "BR",
            "INDIA" to "IN", "MUMBAI" to "IN",
            "UAE" to "AE", "DUBAI" to "AE",
            "RUSSIA" to "RU", "MOSCOW" to "RU",
        )
        for ((token, code) in names.entries.sortedByDescending { it.key.length }) {
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
