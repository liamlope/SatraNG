package com.v2ray.ang.handler

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import libv2ray.CoreCallbackHandler
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Semaphore

/**
 * Finds the country a config really exits in by sending one HTTPS request through it.
 *
 * A private core instance (own local HTTP inbound on a free loopback port, only this config's
 * outbounds) is started, queried, and torn down. Works for CDN-fronted and relayed configs because
 * it measures the actual exit rather than the address in the link. Intended for the `:tasks`
 * process (it is excluded from the VPN, so the probe never loops through a running tunnel).
 */
object ExitIpProbe {

    /** Keep at most two private cores alive at once. */
    private val gate = Semaphore(2)

    private val endpoints = listOf(
        AppConfig.IP_API_URL,
        "https://ipwho.is/",
        "https://ipapi.co/json/",
    )

    /** @return the stored result, or [GeoIpResolver.GeoResult.UNKNOWN] if the probe could not run. */
    fun probe(context: Context, guid: String): GeoIpResolver.GeoResult {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return GeoIpResolver.GeoResult.UNKNOWN
        if (profile.configType == EConfigType.CUSTOM) return GeoIpResolver.GeoResult.UNKNOWN
        if (!gate.tryAcquire(20, java.util.concurrent.TimeUnit.SECONDS)) return GeoIpResolver.GeoResult.UNKNOWN
        var controller: libv2ray.CoreController? = null
        try {
            val built = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
            if (!built.status) return GeoIpResolver.GeoResult.UNKNOWN
            val port = freeLoopbackPort() ?: return GeoIpResolver.GeoResult.UNKNOWN
            val json = withProbeInbound(built.content, port) ?: return GeoIpResolver.GeoResult.UNKNOWN

            controller = CoreNativeManager.newCoreController(object : CoreCallbackHandler {
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
                override fun onEmitStatus(l: Long, s: String?): Long = 0
            })
            controller.startLoop(json, 0)

            for (url in endpoints) {
                val body = HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = 6000, httpPort = port)) ?: continue
                val info = JsonUtil.fromJsonSafe(body, IPAPIInfo::class.java) ?: continue
                val ip = listOf(info.ip, info.clientIp, info.ip_addr, info.query).firstOrNull { !it.isNullOrBlank() }
                val country = listOf(info.country_code, info.countryCode, info.country, info.location?.country_code)
                    .firstOrNull { !it.isNullOrBlank() }
                val result = GeoIpResolver.storeExit(guid, ip, country)
                if (result.exitVerified) return result
            }
            MmkvManager.markServerGeoAttempt(guid)
            return GeoIpResolver.GeoResult.UNKNOWN
        } catch (e: Throwable) {
            LogUtil.w(AppConfig.TAG, "ExitIpProbe: $guid failed: ${e.message}")
            return GeoIpResolver.GeoResult.UNKNOWN
        } finally {
            runCatching { controller?.stopLoop() }
            gate.release()
        }
    }

    private fun freeLoopbackPort(): Int? = try {
        ServerSocket(0, 1, InetAddress.getByName(AppConfig.LOOPBACK)).use { it.localPort }
    } catch (_: Exception) {
        null
    }

    /** Adds one loopback HTTP inbound and routes everything to the config's primary outbound. */
    internal fun withProbeInbound(content: String, port: Int): String? {
        val root = JsonUtil.parseString(content) ?: return null
        val inbound = JsonObject().apply {
            addProperty("tag", "probe-in")
            addProperty("listen", AppConfig.LOOPBACK)
            addProperty("port", port)
            addProperty("protocol", "http")
            add("settings", JsonObject())
        }
        root.add("inbounds", JsonArray().apply { add(inbound) })
        return JsonUtil.toJson(root)
    }
}
