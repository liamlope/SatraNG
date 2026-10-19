package com.v2ray.ang.handler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.ui.usage.UsageMonitorActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Per-app volume/time limits. On hit, the package is VPN-disallowed and the tunnel
 * is restarted via [LauncherManager]. Notification actions grant temporary extras.
 */
object HybridAppLimitManager {

    private const val KEY_LIMITS = "hybrid_app_limits_json"
    private const val KEY_BLOCKED = "hybrid_app_limit_blocked"
    private const val CHANNEL_ID = "satra_app_limits"
    private const val NOTIF_BASE = 7100

    val ACTION_LIMIT: String
        get() = "${AngApplication.application.packageName}.action.APP_LIMIT"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_OP = "op"
    const val OP_PLUS_30MB = "plus_30mb"
    const val OP_PLUS_10MIN = "plus_10min"
    const val OP_CLEAR = "clear"
    const val OP_PAUSE_24H = "pause_24h"

    private const val PLUS_30MB = 30L * 1024L * 1024L
    private const val PLUS_10MIN_MS = 10L * 60L * 1000L
    private const val PAUSE_24H_MS = 24L * 60L * 60L * 1000L

    data class AppLimit(
        val packageName: String,
        val volumeLimitBytes: Long = 0L,
        val timeLimitMs: Long = 0L,
        val usedBytes: Long = 0L,
        val usedMs: Long = 0L,
        val sessionStartElapsed: Long = 0L,
        val pausedUntil: Long = 0L,
    )

    private var monitorJob: Job? = null
    private var lastSampleElapsed = 0L

    fun allLimits(): Map<String, AppLimit> = decodeAll()

    fun blockedPackages(): Set<String> =
        MmkvManager.decodeSettingsStringSet(KEY_BLOCKED)?.toSet().orEmpty()

    fun setVolumeLimit(packageName: String, bytes: Long?) {
        val map = decodeAll().toMutableMap()
        val cur = map[packageName] ?: AppLimit(packageName)
        if (bytes == null || bytes <= 0L) {
            map[packageName] = cur.copy(volumeLimitBytes = 0L)
        } else {
            map[packageName] = cur.copy(
                volumeLimitBytes = bytes,
                sessionStartElapsed = if (cur.sessionStartElapsed == 0L) {
                    SystemClock.elapsedRealtime()
                } else {
                    cur.sessionStartElapsed
                },
            )
        }
        encodeAll(map)
        unblockIfCleared(packageName, map[packageName])
    }

    fun setTimeLimit(packageName: String, ms: Long?) {
        val map = decodeAll().toMutableMap()
        val cur = map[packageName] ?: AppLimit(packageName)
        if (ms == null || ms <= 0L) {
            map[packageName] = cur.copy(timeLimitMs = 0L)
        } else {
            map[packageName] = cur.copy(
                timeLimitMs = ms,
                sessionStartElapsed = if (cur.sessionStartElapsed == 0L) {
                    SystemClock.elapsedRealtime()
                } else {
                    cur.sessionStartElapsed
                },
            )
        }
        encodeAll(map)
        unblockIfCleared(packageName, map[packageName])
    }

    fun clearLimit(packageName: String) {
        val map = decodeAll().toMutableMap()
        map.remove(packageName)
        encodeAll(map)
        removeBlocked(packageName)
        restartVpnIfRunning()
    }

    fun applyNotificationOp(packageName: String, op: String) {
        val map = decodeAll().toMutableMap()
        val cur = map[packageName] ?: AppLimit(packageName)
        when (op) {
            OP_PLUS_30MB -> {
                val nextVol = if (cur.volumeLimitBytes > 0L) {
                    cur.volumeLimitBytes + PLUS_30MB
                } else {
                    cur.usedBytes + PLUS_30MB
                }
                map[packageName] = cur.copy(volumeLimitBytes = nextVol, pausedUntil = 0L)
                removeBlocked(packageName)
            }
            OP_PLUS_10MIN -> {
                val nextTime = if (cur.timeLimitMs > 0L) {
                    cur.timeLimitMs + PLUS_10MIN_MS
                } else {
                    cur.usedMs + PLUS_10MIN_MS
                }
                map[packageName] = cur.copy(timeLimitMs = nextTime, pausedUntil = 0L)
                removeBlocked(packageName)
            }
            OP_CLEAR -> {
                map.remove(packageName)
                removeBlocked(packageName)
            }
            OP_PAUSE_24H -> {
                map[packageName] = cur.copy(pausedUntil = System.currentTimeMillis() + PAUSE_24H_MS)
                removeBlocked(packageName)
            }
        }
        encodeAll(map)
        restartVpnIfRunning()
    }

    fun startMonitoring(context: Context = AngApplication.application) {
        if (monitorJob != null) return
        lastSampleElapsed = SystemClock.elapsedRealtime()
        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (CoreServiceManager.isRunning()) {
                    tick(context)
                }
                delay(5_000L)
            }
        }
        LogUtil.d(AppConfig.TAG, "HybridAppLimit: monitor started")
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        LogUtil.d(AppConfig.TAG, "HybridAppLimit: monitor stopped")
    }

    private fun tick(context: Context) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val dt = (nowElapsed - lastSampleElapsed).coerceAtLeast(0L)
        lastSampleElapsed = nowElapsed
        val nowWall = System.currentTimeMillis()
        val map = decodeAll().toMutableMap()
        if (map.isEmpty()) return
        val active = map.values.filter {
            it.pausedUntil <= nowWall && (it.volumeLimitBytes > 0L || it.timeLimitMs > 0L)
        }
        if (active.isEmpty()) return
        // Do not call HybridUsageTracker.consumeOutboundStats here — NotificationManager owns that.
        // Volume uses NetworkStats UID totals when PACKAGE_USAGE_STATS is granted.
        val uidBytes = if (UsageMonitorStore.hasUsageStatsPermission(context)) {
            UsageMonitorStore.topAppsToday(context, limit = 64)
                .associate { it.packageName to it.bytes }
        } else {
            emptyMap()
        }
        var changed = false
        active.forEach { lim ->
            val bytes = uidBytes[lim.packageName] ?: lim.usedBytes
            val next = lim.copy(
                usedBytes = maxOf(lim.usedBytes, bytes),
                usedMs = lim.usedMs + dt,
                sessionStartElapsed = if (lim.sessionStartElapsed == 0L) nowElapsed else lim.sessionStartElapsed,
            )
            map[lim.packageName] = next
            changed = true
            val volumeHit = next.volumeLimitBytes > 0L && next.usedBytes >= next.volumeLimitBytes
            val timeHit = next.timeLimitMs > 0L && next.usedMs >= next.timeLimitMs
            if (volumeHit || timeHit) {
                enforceBlock(context, next, volumeHit)
            }
        }
        if (changed) encodeAll(map)
    }

    private fun enforceBlock(context: Context, limit: AppLimit, volumeHit: Boolean) {
        val set = MmkvManager.decodeSettingsStringSet(KEY_BLOCKED)?.toMutableSet() ?: mutableSetOf()
        if (limit.packageName in set) return
        set += limit.packageName
        MmkvManager.encodeSettings(KEY_BLOCKED, set)
        LogUtil.i(
            AppConfig.TAG,
            "HybridAppLimit: blocking ${limit.packageName} volumeHit=$volumeHit",
        )
        showLimitNotification(context, limit, volumeHit)
        restartVpnIfRunning()
    }

    private fun showLimitNotification(context: Context, limit: AppLimit, volumeHit: Boolean) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            limit.packageName.hashCode(),
            Intent(context, UsageMonitorActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(op: String, labelRes: Int, req: Int): NotificationCompat.Action {
            val i = Intent(ACTION_LIMIT).setPackage(context.packageName)
                .putExtra(EXTRA_PACKAGE, limit.packageName)
                .putExtra(EXTRA_OP, op)
            val pi = PendingIntent.getBroadcast(
                context,
                req,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action.Builder(0, context.getString(labelRes), pi).build()
        }
        val title = context.getString(R.string.satra_limit_notif_title)
        val body = context.getString(
            if (volumeHit) R.string.satra_limit_notif_volume else R.string.satra_limit_notif_time,
            limit.packageName,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_satra)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(action(OP_PLUS_30MB, R.string.satra_limit_action_plus_30mb, 1))
            .addAction(action(OP_PLUS_10MIN, R.string.satra_limit_action_plus_10min, 2))
            .addAction(action(OP_CLEAR, R.string.satra_limit_action_clear, 3))
            .addAction(action(OP_PAUSE_24H, R.string.satra_limit_action_pause_24h, 4))
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIF_BASE + (limit.packageName.hashCode() and 0xffff), notif)
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "HybridAppLimit: notify failed", e)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.satra_limit_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun unblockIfCleared(packageName: String, limit: AppLimit?) {
        if (limit == null) {
            removeBlocked(packageName)
            return
        }
        val volumeOk = limit.volumeLimitBytes <= 0L || limit.usedBytes < limit.volumeLimitBytes
        val timeOk = limit.timeLimitMs <= 0L || limit.usedMs < limit.timeLimitMs
        if (volumeOk && timeOk) removeBlocked(packageName)
    }

    private fun removeBlocked(packageName: String) {
        val set = MmkvManager.decodeSettingsStringSet(KEY_BLOCKED)?.toMutableSet() ?: return
        if (set.remove(packageName)) {
            MmkvManager.encodeSettings(KEY_BLOCKED, set)
        }
    }

    private fun restartVpnIfRunning() {
        if (!CoreServiceManager.isRunning()) return
        val ctx = AngApplication.application
        LauncherManager.restartService(ctx)
    }

    private fun decodeAll(): Map<String, AppLimit> {
        val raw = MmkvManager.decodeSettingsString(KEY_LIMITS).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val out = LinkedHashMap<String, AppLimit>()
            root.keys().forEach { pkg ->
                val o = root.getJSONObject(pkg)
                out[pkg] = AppLimit(
                    packageName = pkg,
                    volumeLimitBytes = o.optLong("volumeLimitBytes"),
                    timeLimitMs = o.optLong("timeLimitMs"),
                    usedBytes = o.optLong("usedBytes"),
                    usedMs = o.optLong("usedMs"),
                    sessionStartElapsed = o.optLong("sessionStartElapsed"),
                    pausedUntil = o.optLong("pausedUntil"),
                )
            }
            out
        }.getOrElse { emptyMap() }
    }

    private fun encodeAll(map: Map<String, AppLimit>) {
        val root = JSONObject()
        map.forEach { (pkg, lim) ->
            root.put(
                pkg,
                JSONObject()
                    .put("volumeLimitBytes", lim.volumeLimitBytes)
                    .put("timeLimitMs", lim.timeLimitMs)
                    .put("usedBytes", lim.usedBytes)
                    .put("usedMs", lim.usedMs)
                    .put("sessionStartElapsed", lim.sessionStartElapsed)
                    .put("pausedUntil", lim.pausedUntil),
            )
        }
        MmkvManager.encodeSettings(KEY_LIMITS, root.toString())
    }

    class LimitActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != ACTION_LIMIT) return
            val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
            val op = intent.getStringExtra(EXTRA_OP).orEmpty()
            if (pkg.isBlank() || op.isBlank()) return
            applyNotificationOp(pkg, op)
        }
    }
}
