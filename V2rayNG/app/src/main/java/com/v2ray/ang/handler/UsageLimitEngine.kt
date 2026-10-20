package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.PackageUidResolver

/**
 * Turns [UsageRule]s into routing rules for the core.
 *
 * Counting: every active (not yet exhausted) rule gets its own clone of the main proxy outbound
 * (`lim-<id>`), so the core's per-outbound stats give an exact byte count for exactly the traffic
 * that rule matches (through the tunnel, both directions, protocol overhead of the proxy leg included).
 *
 * Blocking: once exhausted the same rule is pointed at the `block` outbound; the core is reloaded
 * in place (the TUN device stays up) and the rest of the VPN keeps working.
 *
 * App rules need UID routing, which the core only has with the Xray TUN engine + route-only
 * sniffing on Android 10+ ([canEnforceAppRules]). Domain rules work in every mode that uses the core's routing.
 */
object UsageLimitEngine {

    fun canEnforceAppRules(): Boolean = SettingsManager.canUseProcessRouting()

    /*
     * Why per-app limits need the Xray tunnel engine
     * ----------------------------------------------
     * Default engine (hev-socks5-tunnel): a small C tun2socks turns every packet from the TUN
     * device into a SOCKS5 connection to the core's local inbound. The core then only sees
     * "127.0.0.1 → destination"; which app opened the connection is lost, so no per-app routing
     * rule (and therefore no per-app counter or block) can ever match. Website limits still work
     * because they match on the destination domain.
     *
     * Xray tunnel engine: the core reads the TUN device itself. On Android 10+ it can ask the
     * system for the owning UID of each connection (ConnectivityManager.getConnectionOwnerUid), so
     * "process"/app routing rules match and each limited app gets its own counted outbound.
     * Route-only sniffing is turned on with it so the sniffed domain is used for rule matching
     * without rewriting the destination the app asked for.
     *
     * Trade-offs when it is on: the VPN restarts once; the Go network stack uses somewhat more CPU
     * than hev under sustained heavy traffic (slightly more battery on big downloads, no visible
     * difference for browsing/streaming); one extra system call per new connection for the UID
     * lookup; routing results are the same. It is upstream v2rayNG's own engine, so compatibility
     * is the same as stock v2rayNG with "hev tun" switched off. SatraNG therefore uses it by
     * default on Android 10+ (see AppConfig.DEFAULT_USE_HEV_TUNNEL); if the user switches back to
     * hev in Advanced settings, saving an app/category limit switches it on again quietly.
     */

    /** The user's answer to the one-time "turn on per-app limits" sheet: true, false or null (not asked). */
    fun engineChoice(): Boolean? = when (MmkvManager.decodeSettingsString(PREF_ENGINE_CHOICE)) {
        "on" -> true
        "off" -> false
        else -> null
    }

    fun setEngineChoice(on: Boolean) = MmkvManager.encodeSettings(PREF_ENGINE_CHOICE, if (on) "on" else "off")

    private const val PREF_ENGINE_CHOICE = "pref_satra_app_engine_choice"

    /** Switches to the engine that supports per-app routing. Caller restarts the VPN afterwards. */
    fun enableAppEnforcement() {
        MmkvManager.encodeSettings(AppConfig.PREF_USE_HEV_TUNNEL, false)
        MmkvManager.encodeSettings(AppConfig.PREF_ROUTE_ONLY_ENABLED, true)
        MmkvManager.encodeSettings(AppConfig.PREF_SNIFFING_ENABLED, true)
    }

    /** Domain limits need sniffing so SNI/host can match; turn it on whenever a site rule is saved. */
    fun enableDomainEnforcement() {
        MmkvManager.encodeSettings(AppConfig.PREF_SNIFFING_ENABLED, true)
    }

    /**
     * Side-effects before a rule is written into the core config.
     * @return true when the VPN [Builder] / engine must restart (not just reload routing).
     */
    fun prepareForRule(context: Context, rule: UsageRule): Boolean {
        var needsVpnRestart = false
        when (rule.kind) {
            RuleKind.DOMAIN -> {
                val before = MmkvManager.decodeSettingsBool(AppConfig.PREF_SNIFFING_ENABLED, AppConfig.DEFAULT_SNIFFING_ENABLED)
                enableDomainEnforcement()
                if (before == false) needsVpnRestart = true
            }
            RuleKind.APP, RuleKind.CATEGORY -> {
                val wasReady = canEnforceAppRules()
                enableAppEnforcement()
                if (!wasReady) needsVpnRestart = true
                if (rule.kind == RuleKind.APP && rule.packageName.isNotBlank()) {
                    if (ensurePackageUsesVpn(rule.packageName)) needsVpnRestart = true
                }
                if (rule.kind == RuleKind.CATEGORY) {
                    val cat = rule.appCategory
                    if (cat != null) {
                        for (pkg in AppCategories.packagesIn(context, cat)) {
                            if (ensurePackageUsesVpn(pkg)) needsVpnRestart = true
                        }
                    }
                }
            }
        }
        return needsVpnRestart
    }

    /**
     * When split tunneling is on, keep a limited package on the VPN path so metering and
     * blocking can see its traffic. Returns true if the per-app set changed (needs VPN restart).
     */
    fun ensurePackageUsesVpn(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY)) return false
        val bypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        val set = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.toMutableSet()
            ?: mutableSetOf()
        val changed = if (bypass) {
            // Listed apps bypass the VPN — remove so this package goes through the tunnel.
            set.remove(packageName)
        } else {
            // Only listed apps use the VPN — add this package.
            set.add(packageName)
        }
        if (changed) {
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, set)
            LogUtil.i(AppConfig.TAG, "UsageLimit: kept $packageName on VPN (split adjusted)")
        }
        return changed
    }

    /** True when split tunneling currently keeps [packageName] off the VPN. */
    fun isPackageBypassingVpn(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY)) return false
        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET).orEmpty()
        val bypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        return if (bypass) packageName in apps else packageName !in apps
    }

    /** Expands a YouTube preset so CDN hosts (googlevideo, ytimg, …) are counted and blocked. */
    fun expandSitePresetDomains(domains: List<String>): List<String> {
        val normalized = domains.mapNotNull { UsageRulesRepository.normalizeDomain(it) }.toMutableList()
        val hosts = normalized.map { it.removePrefix("domain:").removePrefix("full:") }.toSet()
        val looksLikeYoutube = hosts.any { h ->
            h == "youtube.com" || h == "youtu.be" || h == "googlevideo.com" || h.endsWith(".youtube.com")
        }
        if (looksLikeYoutube) {
            for (extra in YOUTUBE_DOMAINS) {
                val n = UsageRulesRepository.normalizeDomain(extra) ?: continue
                if (n !in normalized) normalized += n
            }
            if (normalized.none { it.startsWith("geosite:") }) {
                normalized += "geosite:youtube"
            }
        }
        return normalized.distinct()
    }

    val YOUTUBE_DOMAINS = listOf(
        "youtube.com",
        "youtu.be",
        "googlevideo.com",
        "ytimg.com",
        "youtubei.googleapis.com",
        "yt3.ggpht.com",
        "ggpht.com",
        "youtubekids.com",
    )

    /**
     * True when [rule] is currently supposed to be blocked by the config we build now. Checks the
     * cap again instead of trusting the stored flag, so raising a limit unblocks immediately.
     */
    fun isBlockedNow(rule: UsageRule, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (rule.alwaysBlock) return true
        if (!rule.enabled || rule.limitBytes <= 0L) return false
        val rt = UsageStore.ruleRuntime(rule.id) ?: return false
        return rt.blocked && rt.periodKey == UsagePeriods.periodKey(rule.period, nowMs) &&
            rt.used >= rule.limitBytes + rt.bonus
    }

    /**
     * Re-checks a rule right after the user edited it. A higher cap (or a different period)
     * releases the block at once; the warning re-arms when usage is back under 80 %.
     */
    fun reevaluate(rule: UsageRule, nowMs: Long = System.currentTimeMillis()) {
        val prev = UsageStore.ruleRuntime(rule.id) ?: return
        var rt = LimitEvaluator.step(rule.limitBytes, rule.period, prev, 0L, nowMs).runtime
        if (rule.limitBytes <= 0L) rt = rt.copy(blocked = false, warned = false)
        else if (rt.used < (rule.limitBytes + rt.bonus) * LimitEvaluator.WARN_FRACTION) rt = rt.copy(warned = false)
        if (rt != prev) UsageStore.resetRuleRuntime(rule.id, rt)
    }

    /** "Reset usage": the rule's counter starts from zero for the current period and any block lifts. */
    fun resetRule(rule: UsageRule, nowMs: Long = System.currentTimeMillis()) {
        UsageStore.resetRuleRuntime(rule.id, RuleRuntime(periodKey = UsagePeriods.periodKey(rule.period, nowMs)))
    }

    /** Adds the rule-derived outbounds and routing rules to a freshly built [config]. */
    fun applyToConfig(context: Context, config: V2rayConfig) {
        val rules = UsageRulesRepository.rules().filter { it.enabled }
        if (rules.isEmpty()) return
        val appOk = canEnforceAppRules()
        val proxy = config.outbounds.firstOrNull { it.tag == AppConfig.TAG_PROXY }
        val additions = ArrayList<V2rayConfig.RoutingBean.RulesBean>()
        val cloneTags = HashSet<String>()

        // First match wins in the core: single apps and websites before whole categories, so an
        // app with its own limit is counted there and not in its category.
        for (rule in rules.sortedBy { if (it.kind == RuleKind.CATEGORY) 1 else 0 }) {
            val matcher = when (rule.kind) {
                RuleKind.APP -> {
                    if (!appOk || rule.packageName.isBlank()) continue
                    val uids = PackageUidResolver.packageNamesToUids(context, listOf(rule.packageName))
                    if (uids.isEmpty()) continue
                    V2rayConfig.RoutingBean.RulesBean(process = uids)
                }
                RuleKind.DOMAIN -> {
                    if (rule.domains.isEmpty()) continue
                    V2rayConfig.RoutingBean.RulesBean(domain = rule.domains)
                }
                RuleKind.CATEGORY -> {
                    val cat = rule.appCategory ?: continue
                    if (!appOk) continue
                    val uids = PackageUidResolver.packageNamesToUids(context, AppCategories.packagesIn(context, cat)).distinct()
                    if (uids.isEmpty()) continue
                    V2rayConfig.RoutingBean.RulesBean(process = uids)
                }
            }
            if (isBlockedNow(rule)) {
                matcher.outboundTag = AppConfig.TAG_BLOCKED
            } else if (proxy != null) {
                if (cloneTags.add(rule.outboundTag)) {
                    val copy = JsonUtil.fromJson(JsonUtil.toJson(proxy), V2rayConfig.OutboundBean::class.java)
                    if (copy == null) continue
                    copy.tag = rule.outboundTag
                    config.outbounds.add(copy)
                }
                matcher.outboundTag = rule.outboundTag
            } else {
                // Balancer / policy-group main outbound: nothing to clone, so this rule can neither
                // be counted nor enforced until the user picks a single server.
                LogUtil.w(AppConfig.TAG, "UsageLimit: rule ${rule.label} skipped (no plain proxy outbound)")
                continue
            }
            additions += matcher
        }
        if (additions.isNotEmpty()) config.routing.rules.addAll(0, additions)
    }

    /**
     * Folds one second of per-outbound stats into the rules. Returns the events so the caller
     * (the sampler) can notify and reload the core.
     */
    fun account(
        statsByTag: Map<String, Long>,
        nowMs: Long,
    ): List<Pair<UsageRule, LimitEvent>> {
        val events = ArrayList<Pair<UsageRule, LimitEvent>>()
        for (rule in UsageRulesRepository.rules()) {
            if (!rule.enabled || rule.alwaysBlock) continue
            val delta = statsByTag[rule.outboundTag] ?: 0L
            val gen = UsageStore.ruleGeneration(rule.id)
            val previous = UsageStore.ruleRuntime(rule.id)
            val step = LimitEvaluator.step(rule.limitBytes, rule.period, previous, delta, nowMs)
            if (step.runtime != previous) {
                // The user reset this rule while we were computing: keep their reset.
                if (UsageStore.ruleGeneration(rule.id) != gen) continue
                UsageStore.putRuleRuntime(rule.id, step.runtime)
            }
            if (step.event != LimitEvent.None) events += rule to step.event
        }
        return events
    }

    /** Usage of a rule in its current window, for display. */
    fun usedNow(rule: UsageRule, nowMs: Long = System.currentTimeMillis()): Long {
        val rt = UsageStore.ruleRuntime(rule.id) ?: return 0L
        return if (rt.periodKey == UsagePeriods.periodKey(rule.period, nowMs)) rt.used else 0L
    }

    fun bonusNow(rule: UsageRule, nowMs: Long = System.currentTimeMillis()): Long {
        val rt = UsageStore.ruleRuntime(rule.id) ?: return 0L
        return if (rt.periodKey == UsagePeriods.periodKey(rule.period, nowMs)) rt.bonus else 0L
    }
}
