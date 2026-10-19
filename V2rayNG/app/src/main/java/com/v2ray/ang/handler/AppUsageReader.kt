package com.v2ray.ang.handler

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

data class AppUsage(
    val uid: Int,
    val packageName: String?,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

/**
 * Per-app network totals from Android's own accounting ([NetworkStatsManager]).
 *
 * These are device-wide per-UID counters on Wi-Fi + mobile data, so on their own they include
 * traffic sent while the VPN was off or bypassed (Android credits tunnelled traffic to the app that
 * produced it, not to this VPN app). The Usage screen never shows them directly: the daemon's
 * [VpnAppUsageTracker] only records their growth while the tunnel is up. Needs "Usage access".
 */
object AppUsageReader {

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Raw per-uid totals in [startMs, endMs), merged over Wi-Fi and mobile (no package lookup). */
    fun queryRaw(context: Context, startMs: Long, endMs: Long): List<AppUsage> {
        if (!hasAccess(context)) return emptyList()
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val rx = HashMap<Int, Long>()
        val tx = HashMap<Int, Long>()
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            var stats: NetworkStats? = null
            try {
                stats = nsm.querySummary(type, null, startMs, endMs)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    rx[uid] = (rx[uid] ?: 0L) + bucket.rxBytes
                    tx[uid] = (tx[uid] ?: 0L) + bucket.txBytes
                }
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "AppUsageReader: query type=$type failed: ${e.message}")
            } finally {
                runCatching { stats?.close() }
            }
        }
        return (rx.keys + tx.keys).map { uid -> AppUsage(uid, null, rx[uid] ?: 0L, tx[uid] ?: 0L) }
    }

    /**
     * Groups per-uid byte counts into displayable rows: one per app with a package, the rest
     * (system uids) folded into one row with uid -1. Biggest first, zero rows dropped.
     */
    fun toRows(context: Context, usage: List<AppUsage>): List<AppUsage> {
        val pm = context.packageManager
        val result = usage.map { u ->
            val pkg = runCatching {
                val names = pm.getPackagesForUid(u.uid)
                names?.firstOrNull { pm.getLaunchIntentForPackage(it) != null } ?: names?.firstOrNull()
            }.getOrNull()
            u.copy(packageName = pkg)
        }
        // Many system uids have no package we can show; fold them into one row (uid -1).
        val (named, anonymous) = result.partition { it.packageName != null }
        val merged = named.toMutableList()
        val rest = anonymous.filter { it.totalBytes > 0 }
        if (rest.isNotEmpty()) {
            merged += AppUsage(-1, null, rest.sumOf { it.rxBytes }, rest.sumOf { it.txBytes })
        }
        return merged.filter { it.totalBytes > 0 }.sortedByDescending { it.totalBytes }
    }
}
