package com.v2ray.ang.handler

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.TrafficStats
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
 * Per-app network totals for the VPN usage meter.
 *
 * When Usage Access is granted, prefer [NetworkStatsManager] (Wi-Fi + mobile +
 * [ConnectivityManager.TYPE_VPN]) — under an active VPN, [TrafficStats] often under-counts QUIC
 * apps (YouTube) or credits the VPN UID while Telegram/TCP still looks fine. Fall back to
 * TrafficStats when Usage Access is missing.
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

    /**
     * Best-effort cumulative per-UID counters for metering.
     *
     * Prefer [NetworkStatsManager] (incl. [ConnectivityManager.TYPE_VPN]) when Usage Access is
     * granted — under an active VPN, [TrafficStats] often credits tunnelled bytes to the VPN app
     * or under-counts QUIC apps (YouTube), while Telegram/TCP still looks fine. Fall back to
     * TrafficStats when Usage Access is missing.
     *
     * Callers must only ever use growth between two snapshots.
     */
    fun queryRaw(context: Context, startMs: Long, endMs: Long): List<AppUsage> {
        if (hasAccess(context)) {
            val fromNet = queryNetworkStats(context, startMs, endMs)
            if (fromNet.isNotEmpty()) return fromNet
        }
        return queryTrafficStats(context)
    }

    /** Cumulative rx/tx since boot per installed-app UID (shared UIDs collapsed). */
    fun queryTrafficStats(context: Context): List<AppUsage> {
        val rx = HashMap<Int, Long>()
        val tx = HashMap<Int, Long>()
        val pm = context.packageManager
        val apps = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }.getOrElse { emptyList() }
        for (info in apps) {
            val uid = info.uid
            val r = TrafficStats.getUidRxBytes(uid)
            val t = TrafficStats.getUidTxBytes(uid)
            if (r == TrafficStats.UNSUPPORTED.toLong() || t == TrafficStats.UNSUPPORTED.toLong()) continue
            if (r < 0L && t < 0L) continue
            // Same UID may appear for several packages; counters are identical — keep one.
            if (uid !in rx) {
                rx[uid] = r.coerceAtLeast(0L)
                tx[uid] = t.coerceAtLeast(0L)
            }
        }
        return (rx.keys + tx.keys).map { uid ->
            AppUsage(uid, null, rx[uid] ?: 0L, tx[uid] ?: 0L)
        }
    }

    /** Raw per-uid totals in [startMs, endMs), Wi-Fi + mobile + VPN. Needs Usage Access. */
    fun queryNetworkStats(context: Context, startMs: Long, endMs: Long): List<AppUsage> {
        if (!hasAccess(context)) return emptyList()
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val rx = HashMap<Int, Long>()
        val tx = HashMap<Int, Long>()
        val types = intArrayOf(
            ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_VPN,
        )
        for (type in types) {
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
