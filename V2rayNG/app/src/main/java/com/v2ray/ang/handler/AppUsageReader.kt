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
 * This is device-wide per-UID usage on Wi-Fi + mobile data, i.e. it includes traffic the app sent
 * while the VPN was off or bypassed. Android attributes tunnelled traffic to the originating app,
 * not to this VPN app. It needs the user to grant "Usage access" once. Numbers are approximate
 * (bucketed by the OS) but are the best per-app breakdown available without root.
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

    /** Per-uid totals in [startMs, endMs), merged over Wi-Fi and mobile, biggest first. */
    fun query(context: Context, startMs: Long, endMs: Long): List<AppUsage> {
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
        val pm = context.packageManager
        val result = (rx.keys + tx.keys).map { uid ->
            val pkg = runCatching {
                val names = pm.getPackagesForUid(uid)
                names?.firstOrNull { pm.getLaunchIntentForPackage(it) != null } ?: names?.firstOrNull()
            }.getOrNull()
            AppUsage(uid, pkg, rx[uid] ?: 0L, tx[uid] ?: 0L)
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
