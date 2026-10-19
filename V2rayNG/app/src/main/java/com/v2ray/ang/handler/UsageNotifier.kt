package com.v2ray.ang.handler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.extension.toTrafficString

/** Limit notifications (warning, exceeded, global) with their quick actions. */
object UsageNotifier {
    private const val CHANNEL_ID = "satra_usage_limits"
    private const val BASE_ID = 7400
    const val ACTION = "com.v2ray.ang.action.USAGE_LIMIT"
    const val EXTRA_RULE = "rule"
    const val EXTRA_OP = "op"
    const val OP_BONUS = "bonus"
    const val OP_GLOBAL_BONUS = "global_bonus"
    const val BONUS_BYTES = 100L * 1024 * 1024
    const val GLOBAL_BONUS_BYTES = 500L * 1024 * 1024
    const val EXTRA_OPEN_TAB = "satra_open_tab"

    fun limitExceeded(context: Context, rule: UsageRule) {
        val ctx = AppLocaleManager.localizedContext(context)
        val title = ctx.getString(R.string.sx_notif_limit_title, rule.label)
        val body = ctx.getString(
            R.string.sx_notif_limit_body,
            rule.limitBytes.toTrafficString(),
            periodPhrase(ctx, rule.period),
        )
        post(ctx, BASE_ID + (rule.id.hashCode() and 0xfff), title, body, rule, OP_BONUS)
    }

    fun limitWarning(context: Context, rule: UsageRule) {
        val ctx = AppLocaleManager.localizedContext(context)
        val title = ctx.getString(R.string.sx_notif_warn_title, rule.label)
        val used = UsageLimitEngine.usedNow(rule)
        val body = ctx.getString(
            R.string.sx_notif_warn_body,
            used.toTrafficString(),
            rule.limitBytes.toTrafficString(),
            periodPhrase(ctx, rule.period),
        )
        post(ctx, BASE_ID + 0x1000 + (rule.id.hashCode() and 0xfff), title, body, null, null)
    }

    fun globalExceeded(context: Context, limit: GlobalLimit) {
        val ctx = AppLocaleManager.localizedContext(context)
        val title = ctx.getString(R.string.sx_notif_global_title)
        val body = ctx.getString(
            R.string.sx_notif_global_body,
            limit.limitBytes.toTrafficString(),
            periodPhrase(ctx, limit.period),
        )
        post(ctx, BASE_ID + 0x2000, title, body, null, OP_GLOBAL_BONUS)
    }

    fun periodPhrase(ctx: Context, period: LimitPeriod): String = ctx.getString(
        when (period) {
            LimitPeriod.DAILY -> R.string.sx_period_today
            LimitPeriod.WEEKLY -> R.string.sx_period_this_week
            LimitPeriod.MONTHLY -> R.string.sx_period_this_month
        },
    )

    private fun post(ctx: Context, id: Int, title: String, body: String, rule: UsageRule?, op: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, id,
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN_TAB, "usage")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_satra)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (op != null) {
            val intent = Intent(ACTION).setPackage(ctx.packageName)
                .putExtra(EXTRA_OP, op).putExtra(EXTRA_RULE, rule?.id)
                .putExtra("nid", id)
            val pi = PendingIntent.getBroadcast(
                ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val label = if (op == OP_GLOBAL_BONUS) R.string.sx_notif_action_plus_500 else R.string.sx_notif_action_plus_100
            builder.addAction(0, ctx.getString(label), pi)
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "UsageNotifier: notify failed", e)
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.sx_notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Handles the "+100 MB" / "+500 MB" actions. Only touches the store; the daemon notices on its next tick. */
    class ActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            when (intent.getStringExtra(EXTRA_OP)) {
                OP_BONUS -> intent.getStringExtra(EXTRA_RULE)?.let { UsageStore.addBonus(it, BONUS_BYTES) }
                OP_GLOBAL_BONUS -> {
                    UsageStore.putLong("g:bonus", UsageStore.getLong("g:bonus", 0L) + GLOBAL_BONUS_BYTES)
                }
            }
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
        }
    }
}
