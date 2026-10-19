package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.GeoIpResolver
data class ServerRowUiModel(
    val guid: String,
    val profile: ProfileItem,
    val remarks: String,
    val statistics: String,
    val typeDescription: String,
    val testDelayMillis: Long,
    val subscriptionBadge: String,
    val countryCode: String? = null,
    val countryName: String? = null,
    val favorite: Boolean = false,
    val exitVerified: Boolean = false,
    val geoState: GeoState = GeoState.Unknown,
    /** Country of the server address (entry). Tunnel detection only; never the flag. */
    val entryCountryCode: String? = null,
    /** CDN the server address belongs to (domain fronting), e.g. "Cloudflare". */
    val cdn: String? = null,
    /** Entry in Iran, exit abroad. */
    val tunnel: Boolean = false,
    /** Last measured download speed, bytes/s; <0 failed, 0 never tested. */
    val speedBps: Long = 0L,
    /** Proxy bytes used through this config so far (approximate, this device only). */
    val usedBytes: Long = 0L,
) {
    val flag: String get() = GeoIpResolver.flagEmoji(countryCode)
    val locationLabel: String
        get() = GeoIpResolver.countryNameForCode(countryCode)
            ?: countryName?.takeIf { it.isNotBlank() }
            ?: countryCode
            ?: ""
}

/** Where a config's exit-country detection stands, for the row's location line. */
enum class GeoState { Unknown, Locating, Failed, Located }

internal data class ServerGroupUiState(
    val servers: List<ServersCache> = emptyList(),
    val rows: List<ServerRowUiModel> = emptyList(),
)

fun buildServerRowUiModel(
    server: ServersCache,
    subscriptionRemarks: String,
): ServerRowUiModel {
    val profile = server.profile
    val aff = com.v2ray.ang.handler.MmkvManager.decodeServerAffiliationInfo(server.guid)
    val geo = GeoIpResolver.display(aff)
    return ServerRowUiModel(
        guid = server.guid,
        profile = profile,
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = serverProtocolDescription(profile),
        testDelayMillis = server.testDelayMillis,
        subscriptionBadge = subscriptionRemarks.firstOrNull()?.toString().orEmpty(),
        countryCode = geo.countryCode,
        countryName = geo.countryName,
        favorite = aff?.favorite == true,
        exitVerified = geo.exitVerified,
        entryCountryCode = aff?.entryCountryCode,
        cdn = cdnOf(aff, profile)?.label,
        tunnel = com.v2ray.ang.handler.CdnRanges.isTunnel(aff?.entryCountryCode, geo.countryCode, cdnOf(aff, profile)),
        speedBps = aff?.speedBps ?: 0L,
        usedBytes = runCatching { com.v2ray.ang.handler.UsageStore.serverTraffic(server.guid) }.getOrDefault(0L),
        geoState = when {
            geo.exitVerified -> GeoState.Located
            geo.countryCode == null && (aff?.geoAttemptAt ?: 0L) > 0L -> GeoState.Failed
            else -> GeoState.Unknown
        },
    )
}

internal fun cdnOf(aff: com.v2ray.ang.dto.entities.ServerAffiliationInfo?, profile: ProfileItem): com.v2ray.ang.handler.CdnRanges.Cdn? =
    com.v2ray.ang.handler.CdnRanges.match(aff?.entryIp ?: profile.server)

private fun serverProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { network ->
        if (network.isNotBlank() && !network.equals("tcp", ignoreCase = true)) {
            parts.add(network)
        }
    }
    profile.security?.let { security ->
        if (security.isNotBlank()) {
            parts.add(
                if (profile.insecure == true && security.equals("tls", ignoreCase = true)) {
                    "$security insecure"
                } else {
                    security
                }
            )
        }
    }
    return parts.joinToString(" / ")
}
