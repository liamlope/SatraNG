package com.v2ray.ang.handler

import android.content.Context
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig

/**
 * Per-app traffic while the VPN is connected, for the "Apps" list on the Usage screen.
 *
 * Android has no API that reports only the bytes an app sent through *this* VPN. What it does
 * have is per-UID accounting ([AppUsageReader]) where tunnelled traffic is credited to the app
 * that produced it. So the daemon takes a snapshot of those counters when the tunnel comes up and
 * then, about once a minute and once more when it stops, adds only the growth since the previous
 * snapshot to a per-day ledger. History from before the session, traffic while disconnected and
 * apps excluded by split tunneling never enter the ledger, so the list starts at zero the first
 * time tracking runs. Numbers are approximate: Android updates its counters in batches and spreads
 * them over its buckets. Limit counters are separate and exact (they come from the core itself).
 */
object VpnAppUsageTracker {
    const val SAMPLE_EVERY_MS = 60_000L

    private var last: HashMap<Int, LongArray>? = null
    private var anchorMs = 0L

    /** True when this session's app traffic is going through our tunnel at all. */
    fun applies(): Boolean =
        (MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: AppConfig.VPN) == AppConfig.VPN

    @Synchronized
    fun begin(context: Context, nowMs: Long = System.currentTimeMillis()) {
        last = null
        if (!applies() || !AppUsageReader.hasAccess(context)) return
        // Fixed anchor for the whole session: cumulative counters from here only ever grow.
        anchorMs = nowMs - 3 * 3_600_000L
        last = snapshot(context, nowMs)
    }

    /** Adds the growth since the previous sample to the ledger. Cheap enough to call every minute. */
    @Synchronized
    fun sample(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prev = last ?: run {
            // Usage access may have been granted after the tunnel started: start counting now.
            if (applies() && AppUsageReader.hasAccess(context)) begin(context, nowMs)
            return
        }
        val cur = snapshot(context, nowMs)
        if (cur.isEmpty()) return
        for ((uid, rx, tx) in fold(prev, cur, excludedUids(context))) {
            UsageStore.addAppTraffic(nowMs, uid, rx, tx)
        }
    }

    /**
     * Growth per UID since [prev] (which is advanced to [cur] in place). A UID seen for the first
     * time only sets its baseline, so nothing from before the session is ever counted; shrinking
     * counters (Android re-bucketing) count as zero; [excluded] UIDs are tracked but not reported.
     */
    internal fun fold(prev: HashMap<Int, LongArray>, cur: Map<Int, LongArray>, excluded: Set<Int>): List<Triple<Int, Long, Long>> {
        val out = ArrayList<Triple<Int, Long, Long>>()
        for ((uid, v) in cur) {
            val p = prev[uid]
            if (p == null) { prev[uid] = v.copyOf(); continue }
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
        // End in the future so the current bucket is counted whole instead of time-interpolated;
        // otherwise traffic from just before the session would leak in as the bucket fills.
        for (u in AppUsageReader.queryRaw(context, anchorMs, nowMs + 86_400_000L)) {
            map[u.uid] = longArrayOf(u.rxBytes, u.txBytes)
        }
        return map
    }

    /** Our own UID (the tunnel's carrier traffic) and apps that split tunneling keeps off the VPN. */
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
                    // Only the listed apps use the VPN: everything else is excluded.
                    val all = last?.keys.orEmpty()
                    out += all.filterNot { it in listed }
                }
            }
        }
        runCatching { context.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0).uid }.getOrNull()?.let { out += it }
        return out
    }
}
