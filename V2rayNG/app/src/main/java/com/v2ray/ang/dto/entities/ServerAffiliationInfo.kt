package com.v2ray.ang.dto.entities

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var countryCode: String? = null,
    var countryName: String? = null,
    var resolvedIp: String? = null,
    var geoUpdatedAt: Long = 0L,
)
