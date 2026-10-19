package com.v2ray.ang.handler

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.os.RemoteException
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.util.Calendar
import java.util.Locale
import org.json.JSONObject

/**
 * Persists daily VPN traffic buckets and reads per-UID NetworkStats when permitted.
 */
object UsageMonitorStore {

    private const val KEY_DAILY = "usage_daily_bytes_json"
    private const val KEY_HOURLY = "usage_hourly_bytes_json"

    data class AppUsageRow(
        val packageName: String,
        val label: String,
        val bytes: Long,
    )

    data class HourBucket(
        val hour: Int,
        val bytes: Long,
    )

    fun recordLocalDeltaSample(deltaBytes: Long) {
        if (deltaBytes <= 0L) return
        val dayKey = dayKeyNow()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val daily = JSONObject(MmkvManager.decodeSettingsString(KEY_DAILY).orEmpty().ifBlank { "{}" })
        daily.put(dayKey, daily.optLong(dayKey, 0L) + deltaBytes)
        MmkvManager.encodeSettings(KEY_DAILY, daily.toString())
        val hourly = JSONObject(MmkvManager.decodeSettingsString(KEY_HOURLY).orEmpty().ifBlank { "{}" })
        val hk = "$dayKey-$hour"
        hourly.put(hk, hourly.optLong(hk, 0L) + deltaBytes)
        MmkvManager.encodeSettings(KEY_HOURLY, hourly.toString())
    }

    fun dailyBytes(days: Int = 7): List<Pair<String, Long>> {
        val daily = JSONObject(MmkvManager.decodeSettingsString(KEY_DAILY).orEmpty().ifBlank { "{}" })
        val cal = Calendar.getInstance()
        val out = ArrayList<Pair<String, Long>>(days)
        for (i in (days - 1) downTo 0) {
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = String.format(
                Locale.US,
                "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
            out += key to daily.optLong(key, 0L)
        }
        return out
    }

    fun peakHoursToday(): List<HourBucket> {
        val dayKey = dayKeyNow()
        val hourly = JSONObject(MmkvManager.decodeSettingsString(KEY_HOURLY).orEmpty().ifBlank { "{}" })
        return (0..23).map { h ->
            HourBucket(h, hourly.optLong("$dayKey-$h", 0L))
        }.sortedByDescending { it.bytes }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun topAppsToday(context: Context, limit: Int = 10): List<AppUsageRow> {
        if (!hasUsageStatsPermission(context)) return emptyList()
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return emptyList()
        val start = startOfDayMs()
        val end = System.currentTimeMillis()
        val byUid = HashMap<Int, Long>()
        try {
            queryBucket(nsm, ConnectivityManager.TYPE_WIFI, start, end, byUid)
            queryBucket(nsm, ConnectivityManager.TYPE_MOBILE, start, end, byUid)
        } catch (e: RemoteException) {
            LogUtil.e(AppConfig.TAG, "UsageMonitor: NetworkStats query failed", e)
            return emptyList()
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "UsageMonitor: missing usage stats permission", e)
            return emptyList()
        }
        val pm = context.packageManager
        return byUid.entries
            .mapNotNull { (uid, bytes) ->
                if (bytes <= 0L) return@mapNotNull null
                val pkgs = pm.getPackagesForUid(uid) ?: return@mapNotNull null
                val pkg = pkgs.firstOrNull() ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    pkg
                }
                AppUsageRow(pkg, label, bytes)
            }
            .sortedByDescending { it.bytes }
            .take(limit)
    }

    private fun queryBucket(
        nsm: NetworkStatsManager,
        networkType: Int,
        start: Long,
        end: Long,
        byUid: MutableMap<Int, Long>,
    ) {
        val stats = nsm.querySummary(networkType, null, start, end) ?: return
        val bucket = NetworkStats.Bucket()
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket)
            val total = bucket.rxBytes + bucket.txBytes
            if (total > 0L) {
                byUid[bucket.uid] = (byUid[bucket.uid] ?: 0L) + total
            }
        }
        stats.close()
    }

    private fun dayKeyNow(): String {
        val cal = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun startOfDayMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
