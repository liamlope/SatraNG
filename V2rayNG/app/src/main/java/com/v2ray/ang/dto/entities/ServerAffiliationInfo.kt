package com.v2ray.ang.dto.entities

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var countryCode: String? = null,
    var countryName: String? = null,
    var resolvedIp: String? = null,
    var geoUpdatedAt: Long = 0L,
    /**
     * True when [countryCode]/[resolvedIp] came from the live exit IP after a successful connect
     * (through the local proxy), not from resolving the config server host.
     */
    var exitIpVerified: Boolean = false,
)
