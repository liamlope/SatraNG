package com.v2ray.ang.handler

import android.content.Context
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig

/**
 * Per-app traffic while the VPN is connected, for the "Apps" list and APP/CATEGORY limits.
 *
 * Android often credits tunnel *carrier* bytes to the VPN app UID. Client apps may show little or
 * nothing in TrafficStats. We therefore:
 * 1. Snapshot OS per-UID counters ([AppUsageReader]) and take growth (excluding our UID).
 * 2. Compare with the core's proxy-byte total for the same interval.
 * 3. Attribute any shortfall across apps that currently own sockets ([ProcNetUidScanner]) —
 *    proportional to connection count, same rule for every package.
 *
 * Numbers stay approximate; DOMAIN limits still use exact core `lim-*` stats.
 */
object VpnAppUsageTracker {
    const val SAMPLE_EVERY_MS = 30_000L

    /** Synthetic UID for VPN bytes we could not map to a client app (shown as system/other). */
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
     * @param coreProxyBytes uplink+downlink through the proxy this interval (from the core).
     *        Used as ground truth so the Apps list can catch up to "Total through VPN".
     * @return per-UID (rx+tx) growth this sample after attribution.
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
        val vpnUid = Process.myUid()
        // Raw growth including our UID (carrier) — used only to size the shortfall.
        val rawAll = fold(HashMap(prev.mapValues { it.value.copyOf() }), cur, emptySet())
        val carrierGrowth = rawAll.filter { it.first == vpnUid }.sumOf { it.second + it.third }
        // Advance baseline for every UID (including excluded) so next sample is clean.
        fold(prev, cur, emptySet())

        val osGrowth = HashMap<Int, Long>()
        for ((uid, rx, tx) in rawAll) {
            if (uid in excluded) continue
            val total = rx + tx
            if (total > 0L) osGrowth[uid] = (osGrowth[uid] ?: 0L) + total
        }

        val attributed = osGrowth.values.sum()
        val target = maxOf(coreProxyBytes, carrierGrowth)
        val shortfall = (target - attributed).coerceAtLeast(0L)
        if (shortfall > 0L) {
            val weights = ProcNetUidScanner.connectionCounts()
                .filterKeys { uid -> uid !in excluded && uid >= 10000 }
            val shares = ProcNetUidScanner.distributeByWeight(shortfall, weights)
            if (shares.isEmpty()) {
                osGrowth[UNATTRIBUTED_UID] = (osGrowth[UNATTRIBUTED_UID] ?: 0L) + shortfall
            } else {
                for ((uid, bytes) in shares) {
                    osGrowth[uid] = (osGrowth[uid] ?: 0L) + bytes
                }
            }
        }

        for ((uid, total) in osGrowth) {
            if (total <= 0L) continue
            // Treat as download-heavy (rx) for the ledger shape; total is what limits/UI use.
            UsageStore.addAppTraffic(nowMs, uid, total, 0L)
        }
        return osGrowth
    }

    /**
     * Growth per UID since [prev] (which is advanced to [cur] in place). A UID seen for the first
     * time only sets its baseline; shrinking counters count as zero; [excluded] UIDs are tracked
     * but not reported when the caller passes them here.
     */
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

    private fun excludedUids(context: Context): Set<Int> {
        val out = HashSet<Int>()
        out += Process.myUid()
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
