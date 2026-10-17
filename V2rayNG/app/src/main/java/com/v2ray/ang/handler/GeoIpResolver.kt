package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.LogUtil
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Country display for servers.
 *
 * Flags must reflect the **exit IP after connect** (via local proxy), not the config host —
 * many configs are Iran→abroad tunnels whose server IP is IR while the real exit is abroad.
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
     * UI-facing geo: prefer exit-verified cache; otherwise remarks hint only.
     * Never DNS-resolves the profile server host.
     */
    fun displayForProfile(guid: String, profile: ProfileItem): GeoResult {
        MmkvManager.decodeServerAffiliationInfo(guid)?.let { aff ->
            if (aff.exitIpVerified &&
                !aff.countryCode.isNullOrBlank() &&
                System.currentTimeMillis() - aff.geoUpdatedAt < CACHE_TTL_MS
            ) {
                return GeoResult(aff.countryCode, aff.countryName, aff.resolvedIp, exitVerified = true)
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

    private fun normalizeCountryCode(raw: String): String? {
        if (raw.isBlank()) return null
        val upper = raw.uppercase(Locale.US)
        if (upper.length == 2 && Locale.getISOCountries().contains(upper)) return upper
        // Some APIs return full country name in the country field
        return Locale.getISOCountries().firstOrNull { code ->
            Locale("", code).displayCountry.equals(raw, ignoreCase = true)
        }
    }

    private fun guessFromRemarks(remarks: String): GeoResult? {
        val upper = remarks.uppercase(Locale.US)
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
