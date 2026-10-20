package com.v2ray.ang.handler

import android.content.Context
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig

/**
 * Per-app traffic while the VPN is connected.
 *
 * Primary source: core `appuid-*` / APP `lim-*` outbound counters ([UidTunnelMeter]) — bytes counted
 * on the tunnel datapath with a known UID. Optional OS snapshots remain only as a weak supplement
 * when the core reports nothing for a UID (process lookup miss).
 */
object VpnAppUsageTracker {
    const val SAMPLE_EVERY_MS = 30_000L
    const val UNATTRIBUTED_UID = -2

    private var last: HashMap<Int, LongArray>? = null
    private var anchorMs = 0L

    fun applies(): Boolean =
        (MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: AppConfig.VPN) == AppConfig.VPN

    @Synchronized
    fun begin(context: Context, nowMs: Long = System.currentTimeMillis()) {
        last = null
        if (!applies()) return
        anchorMs = nowMs - 3 * 3_600_000L
        last = snapshot(context, nowMs)
        if (last.isNullOrEmpty()) last = null
    }

    /**
     * Records one tick of per-UID bytes from the core (already reset there).
     * @return the same map for APP-limit accounting.
     */
    @Synchronized
    fun recordCoreUidBytes(nowMs: Long, byUid: Map<Int, Long>): Map<Int, Long> {
        if (byUid.isEmpty()) return emptyMap()
        val excluded = excludedUidsStatic()
        val out = HashMap<Int, Long>()
        for ((uid, bytes) in byUid) {
            if (bytes <= 0L || uid in excluded) continue
            out[uid] = bytes
            UsageStore.addAppTraffic(nowMs, uid, bytes, 0L)
        }
        return out
    }

    /**
     * Optional OS-counter sample (supplement). Prefer [recordCoreUidBytes] for accuracy.
     */
    @Synchronized
    fun sample(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
        coreProxyBytes: Long = 0L,
    ): Map<Int, Long> {
        val prev = last ?: run {
            if (applies()) begin(context, nowMs)
            return emptyMap()
        }
        val cur = snapshot(context, nowMs)
        if (cur.isEmpty()) return emptyMap()
        val excluded = excludedUids(context)
        val growth = fold(prev, cur, excluded)
        val os = HashMap<Int, Long>()
        for ((uid, rx, tx) in growth) {
            val total = rx + tx
            if (total > 0L) {
                os[uid] = total
                UsageStore.addAppTraffic(nowMs, uid, rx, tx)
            }
        }
        // If core total dwarfs OS attribution, keep a single unattributed bucket so the UI
        // can show that metering fell back (should be rare when UidTunnelMeter is active).
        val attributed = os.values.sum()
        val shortfall = (coreProxyBytes - attributed).coerceAtLeast(0L)
        if (shortfall > 1024L && SettingsManager.canUseProcessRouting().not()) {
            os[UNATTRIBUTED_UID] = (os[UNATTRIBUTED_UID] ?: 0L) + shortfall
            UsageStore.addAppTraffic(nowMs, UNATTRIBUTED_UID, shortfall, 0L)
        }
        return os
    }

    internal fun fold(prev: HashMap<Int, LongArray>, cur: Map<Int, LongArray>, excluded: Set<Int>): List<Triple<Int, Long, Long>> {
        val out = ArrayList<Triple<Int, Long, Long>>()
        for ((uid, v) in cur) {
            val p = prev[uid]
            if (p == null) {
                prev[uid] = v.copyOf()
                continue
            }
            val dRx = v[0] - p[0]
            val dTx = v[1] - p[1]
            if (dRx > 0) p[0] = v[0]
            if (dTx > 0) p[1] = v[1]
            if (uid in excluded) continue
            if (dRx > 0 || dTx > 0) out += Triple(uid, dRx.coerceAtLeast(0L), dTx.coerceAtLeast(0L))
        }
        return out
    }

    @Synchronized
    fun end(context: Context) {
        runCatching { sample(context) }
        last = null
    }

    private fun snapshot(context: Context, nowMs: Long): HashMap<Int, LongArray> {
        val map = HashMap<Int, LongArray>()
        for (u in AppUsageReader.queryRaw(context, anchorMs, nowMs + 86_400_000L)) {
            map[u.uid] = longArrayOf(u.rxBytes, u.txBytes)
        }
        return map
    }

    private fun excludedUidsStatic(): Set<Int> {
        val out = HashSet<Int>()
        out += Process.myUid()
        return out
    }

    private fun excludedUids(context: Context): Set<Int> {
        val out = excludedUidsStatic().toMutableSet()
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY)) {
            val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET).orEmpty()
            if (apps.isNotEmpty()) {
                val pm = context.packageManager
                val bypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
                val listed = apps.mapNotNull { runCatching { pm.getApplicationInfo(it, 0).uid }.getOrNull() }.toSet()
                if (bypass) {
                    out += listed
                } else {
                    val all = last?.keys.orEmpty()
                    out += all.filterNot { it in listed }
                }
            }
        }
        runCatching { context.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0).uid }.getOrNull()?.let { out += it }
        return out
    }
}
