package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem

/**
 * Parses 3x-ui / Clash-compatible subscription response headers into [SubscriptionItem] fields.
 *
 * Header format:
 * `Subscription-Userinfo: upload=111; download=222; total=322122547200; expire=1893456000`
 */
object SubscriptionUserinfoParser {

    fun applyHeaders(subscription: SubscriptionItem, headers: Map<String, String>) {
        val normalized = headers.mapKeys { it.key.lowercase() }

        normalized["subscription-userinfo"]?.let { raw ->
            parseUserinfo(raw).forEach { (key, value) ->
                when (key) {
                    "upload" -> subscription.upload = value
                    "download" -> subscription.download = value
                    "total" -> subscription.total = value
                    "expire" -> subscription.expire = value
                }
            }
        }

        normalized["profile-title"]?.takeIf { it.isNotBlank() }?.let {
            subscription.profileTitle = decodeMaybeBase64Title(it)
        }
        normalized["announce"]?.takeIf { it.isNotBlank() }?.let { subscription.announce = it }
        normalized["support-url"]?.takeIf { it.isNotBlank() }?.let { subscription.supportUrl = it }
        normalized["profile-web-page-url"]?.takeIf { it.isNotBlank() }?.let {
            subscription.profileWebPageUrl = it
            if (subscription.supportUrl.isNullOrBlank()) subscription.supportUrl = it
        }
        normalized["profile-update-interval"]?.toLongOrNull()?.let {
            subscription.profileUpdateInterval = it
            if (it > 0) {
                subscription.updateInterval = it * 60 // hours -> minutes
            }
        }
    }

    private fun parseUserinfo(raw: String): Map<String, Long> {
        val out = linkedMapOf<String, Long>()
        raw.split(';').forEach { part ->
            val kv = part.trim().split('=', limit = 2)
            if (kv.size == 2) {
                val key = kv[0].trim().lowercase()
                val value = kv[1].trim().toLongOrNull() ?: return@forEach
                out[key] = value
            }
        }
        return out
    }

    private fun decodeMaybeBase64Title(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("base64:", ignoreCase = true)) {
            val b64 = trimmed.substringAfter(':')
            return runCatching {
                String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
            }.getOrDefault(trimmed)
        }
        return trimmed
    }
}
