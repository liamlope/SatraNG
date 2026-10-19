package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The only consumer of the core's outbound counters (reading them resets them).
 * Runs inside the daemon process for as long as the core runs, once per second:
 * ledger, live speed, session record, hybrid subscription delta, per-rule accounting and
 * enforcement. Everything the UI shows is read back from [UsageStore].
 */
object UsageSampler {

    /** Monotonic totals since the daemon started; the speed notification diffs against these. */
    data class Totals(
        val proxyUp: Long = 0L,
        val proxyDown: Long = 0L,
        val directUp: Long = 0L,
        val directDown: Long = 0L,
    )

    private const val TICK_MS = 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    var totals = Totals()
        private set

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        appContext = app
        finalizeStaleSession()
        val startedAt = System.currentTimeMillis()
        val serverName = MmkvManager.getSelectServer()?.let { MmkvManager.decodeServerConfig(it)?.remarks }.orEmpty()
        job = scope.launch {
            runCatching { UsageStore.prune() }
            runCatching { VpnAppUsageTracker.begin(app) }
            var lastAppSample = System.currentTimeMillis()
            var sessionUp = 0L
            var sessionDown = 0L
            var lastTick = System.currentTimeMillis()
            var secondsCarry = 0L
            var tickCount = 0
            var globalNotified = false
            while (isActive) {
                delay(TICK_MS)
                val now = System.currentTimeMillis()
                val dt = (now - lastTick).coerceAtLeast(1L)
                lastTick = now
                try {
                    val stats = CoreServiceManager.queryAllOutboundTrafficStats()
                    var pUp = 0L; var pDown = 0L; var dUp = 0L; var dDown = 0L
                    val byTag = HashMap<String, Long>()
                    for (s in stats) {
                        if (s.tag == AppConfig.TAG_BLOCKED) continue
                        val isUp = s.direction == AppConfig.UPLINK
                        val isDown = s.direction == AppConfig.DOWNLINK
                        if (!isUp && !isDown) continue
                        if (s.tag == AppConfig.TAG_DIRECT) {
                            if (isUp) dUp += s.value else dDown += s.value
                        } else {
                            if (isUp) pUp += s.value else pDown += s.value
                            if (s.tag.startsWith(UsageRule.COUNT_TAG_PREFIX)) {
                                byTag[s.tag] = (byTag[s.tag] ?: 0L) + s.value
                            }
                        }
                    }
                    val t = totals
                    totals = Totals(t.proxyUp + pUp, t.proxyDown + pDown, t.directUp + dUp, t.directDown + dDown)
                    val up = pUp + dUp
                    val down = pDown + dDown
                    sessionUp += up
                    sessionDown += down

                    UsageStore.addTraffic(now, up, down)
                    secondsCarry += dt
                    if (secondsCarry >= 1000L) {
                        UsageStore.addConnectedSeconds(now, secondsCarry / 1000L)
                        secondsCarry %= 1000L
                    }
                    if (pUp + pDown > 0L) HybridUsageTracker.addLocalDelta(pUp + pDown)

                    UsageStore.publishLive(
                        LiveUsage(
                            running = true,
                            upBps = up * 1000L / dt,
                            downBps = down * 1000L / dt,
                            sessionStartMs = startedAt,
                            sessionUp = sessionUp,
                            sessionDown = sessionDown,
                            updatedAt = now,
                        ),
                    )

                    var reload = false
                    for ((rule, event) in UsageLimitEngine.account(byTag, now)) {
                        when (event) {
                            LimitEvent.Exceeded -> { UsageNotifier.limitExceeded(app, rule); reload = true }
                            LimitEvent.Warned -> UsageNotifier.limitWarning(app, rule)
                            LimitEvent.Released -> reload = true
                            LimitEvent.None -> Unit
                        }
                    }
                    if (reload) CoreServiceManager.reloadForPolicyChange()

                    if (now - lastAppSample >= VpnAppUsageTracker.SAMPLE_EVERY_MS) {
                        lastAppSample = now
                        runCatching { VpnAppUsageTracker.sample(app, now) }
                    }

                    if (++tickCount % 5 == 0 && !globalNotified) {
                        globalNotified = checkGlobalLimit(app, now)
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "UsageSampler: tick failed", e)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        val running = job?.isActive == true
        job?.cancel()
        job = null
        if (!running) return
        appContext?.let { ctx -> scope.launch { runCatching { VpnAppUsageTracker.end(ctx) } } }
        val live = UsageStore.live()
        val end = System.currentTimeMillis()
        if (live.sessionStartMs > 0L) {
            val server = MmkvManager.getSelectServer()?.let { MmkvManager.decodeServerConfig(it)?.remarks }.orEmpty()
            UsageStore.appendSession(SessionRecord(live.sessionStartMs, end, live.sessionUp, live.sessionDown, server))
        }
        UsageStore.publishLive(LiveUsage(running = false, updatedAt = end))
    }

    /** A previous daemon died without [stop]; keep its session instead of losing it. */
    private fun finalizeStaleSession() {
        val raw = UsageStore.getBool("live:run", false)
        val ts = UsageStore.getLong("live:ts", 0L)
        val start = UsageStore.getLong("live:ss", 0L)
        if (raw && start > 0L && ts > start) {
            UsageStore.appendSession(
                SessionRecord(start, ts, UsageStore.getLong("live:su", 0L), UsageStore.getLong("live:sd", 0L)),
            )
        }
        UsageStore.publishLive(LiveUsage(running = false, updatedAt = System.currentTimeMillis()))
    }

    /** @return true once the global limit has been enforced for this window. */
    private fun checkGlobalLimit(context: Context, now: Long): Boolean {
        val g = UsageRulesRepository.global()
        if (!g.enabled || g.limitBytes <= 0L) return false
        val pk = UsagePeriods.periodKey(g.period, now)
        val storedPk = UsageStore.getString("g:pk")
        if (storedPk != pk) {
            UsageStore.putString("g:pk", pk)
            UsageStore.putLong("g:bonus", 0L)
        }
        val used = UsageStore.totalSince(UsagePeriods.periodStart(g.period, now), now)
        val cap = g.limitBytes + UsageStore.getLong("g:bonus", 0L)
        if (used < cap) return false
        UsageNotifier.globalExceeded(context, g)
        com.v2ray.ang.core.LauncherManager.stopService(context)
        return true
    }
}
