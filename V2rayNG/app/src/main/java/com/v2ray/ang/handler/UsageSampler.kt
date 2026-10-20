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
        val serverGuid = MmkvManager.getSelectServer().orEmpty()
        val serverName = serverGuid.takeIf { it.isNotBlank() }?.let { MmkvManager.decodeServerConfig(it)?.remarks }.orEmpty()
        job = scope.launch {
            runCatching { UsageStore.prune() }
            runCatching { VpnAppUsageTracker.begin(app) }
            var lastAppSample = System.currentTimeMillis()
            var proxySinceAppSample = 0L
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
                    val byUid = HashMap<Int, Long>()
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
                            UidTunnelMeter.parseUid(s.tag)?.let { uid ->
                                byUid[uid] = (byUid[uid] ?: 0L) + s.value
                            }
                        }
                    }
                    // APP lim-* traffic is also that package's tunnel use (rule matched first).
                    for (rule in UsageRulesRepository.rules()) {
                        if (rule.kind != RuleKind.APP || rule.packageName.isBlank()) continue
                        val delta = byTag[rule.outboundTag] ?: 0L
                        if (delta <= 0L) continue
                        val uid = runCatching {
                            app.packageManager.getPackageUid(rule.packageName, 0)
                        }.getOrNull() ?: continue
                        byUid[uid] = (byUid[uid] ?: 0L) + delta
                    }

                    val t = totals
                    totals = Totals(t.proxyUp + pUp, t.proxyDown + pDown, t.directUp + dUp, t.directDown + dDown)
                    val up = pUp + dUp
                    val down = pDown + dDown
                    sessionUp += up
                    sessionDown += down
                    proxySinceAppSample += pUp + pDown

                    UsageStore.addTraffic(now, up, down)
                    secondsCarry += dt
                    if (secondsCarry >= 1000L) {
                        UsageStore.addConnectedSeconds(now, secondsCarry / 1000L)
                        secondsCarry %= 1000L
                    }
                    if (pUp + pDown > 0L) {
                        HybridUsageTracker.addLocalDelta(pUp + pDown)
                        UsageStore.addServerTraffic(serverGuid, pUp + pDown)
                    }

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

                    // 1) Exact datapath UID counters (appuid-* / APP lim-*) — used for APP limits.
                    // 2) Fill shortfall for the Apps list UI only. Never feed fill into LimitEvaluator:
                    //    a large guessed chunk would jump past a small cap and still leave TUN bypass.
                    val tickProxy = pUp + pDown
                    val blockedUids = runCatching {
                        UsageLimitEngine.blockedUids(app, now)
                    }.getOrDefault(emptySet())
                    val exact = runCatching {
                        VpnAppUsageTracker.recordCoreUidBytes(now, byUid)
                    }.getOrDefault(emptyMap())
                    runCatching {
                        VpnAppUsageTracker.fillUnattributed(now, tickProxy, exact, blockedUids)
                    }

                    val hasAppLimits = UsageRulesRepository.rules().any { it.enabled && it.matchesApps }
                    var appUidByTag = emptyMap<String, Long>()
                    if (hasAppLimits && exact.isNotEmpty()) {
                        appUidByTag = UsageLimitEngine.appUidDeltasForRules(app, exact)
                    }
                    if (now - lastAppSample >= VpnAppUsageTracker.SAMPLE_EVERY_MS) {
                        lastAppSample = now
                        proxySinceAppSample = 0L
                        runCatching { VpnAppUsageTracker.sample(app, now) }
                    }

                    var reload = false
                    for ((rule, event) in UsageLimitEngine.account(byTag, now, appUidByTag)) {
                        when (event) {
                            LimitEvent.Exceeded -> {
                                LogUtil.w(AppConfig.TAG, "UsageSampler: limit exceeded ${rule.label}")
                                UsageNotifier.limitExceeded(app, rule)
                                // Soft core reload (keeps VPN up). Full VPN restart broke apps/login
                                // and stopped metering; stopLoop already drops proxy keep-alives.
                                reload = true
                            }
                            LimitEvent.Warned -> UsageNotifier.limitWarning(app, rule)
                            LimitEvent.Released -> reload = true
                            LimitEvent.None -> Unit
                        }
                    }
                    if (reload) CoreServiceManager.reloadForPolicyChange()

                    tickCount++
                    if (tickCount % 30 == 0) {
                        logStalledDomainLimits(byTag, pUp + pDown)
                    }
                    if (tickCount % 5 == 0 && !globalNotified) {
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

    /**
     * Domain/app limits only move when traffic hits their `lim-*` outbound. If the tunnel is
     * busy but a rule stays at zero, the matcher (domains/sniff/UID) is wrong — log once a while.
     */
    private fun logStalledDomainLimits(byTag: Map<String, Long>, proxyBytes: Long) {
        if (proxyBytes <= 0L) return
        for (rule in UsageRulesRepository.rules()) {
            if (!rule.enabled || rule.alwaysBlock) continue
            val delta = byTag[rule.outboundTag] ?: 0L
            if (delta > 0L) continue
            if (UsageLimitEngine.usedNow(rule) > 0L) continue
            LogUtil.w(
                AppConfig.TAG,
                "UsageLimit: rule ${rule.label} (${rule.kind}) still 0 while proxy active — check sniff/domains/UID",
            )
        }
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
