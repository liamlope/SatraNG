package com.v2ray.ang.ui.home

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.compose.liquid.SatraGlassCard
import com.v2ray.ang.ui.compose.liquid.SatraGlassIconButton
import com.v2ray.ang.ui.compose.liquid.SatraGlassToggle
import com.v2ray.ang.ui.compose.liquid.onGlassPrimary
import com.v2ray.ang.ui.compose.liquid.onGlassSecondary
import com.v2ray.ang.ui.compose.satra.SatraTokens
import com.v2ray.ang.ui.compose.satra.SatraUsageRing
import com.v2ray.ang.ui.subscription.SubEditActivity
import com.v2ray.ang.util.Utils

@Composable
fun SatraSubsTab(
    modifier: Modifier,
    subscriptions: List<Pair<String, SubscriptionItem>>,
    serverCounts: Map<String, Int>,
    onRefresh: () -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onOpenEdit: (String?) -> Unit,
    onOpenManage: () -> Unit = {},
) {
    val context = LocalContext.current
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.satra_subs_title),
                style = MaterialTheme.typography.headlineMedium,
                color = onGlassPrimary(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.satra_sub_refresh), color = SatraTokens.TealDeep)
            }
            SatraGlassIconButton(
                onClick = onOpenManage,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24dp),
                    contentDescription = stringResource(R.string.satra_sub_settings),
                    tint = SatraTokens.Purple,
                    modifier = Modifier.size(22.dp),
                )
            }
            SatraGlassIconButton(
                onClick = {
                    onOpenEdit(null)
                    context.startActivity(Intent(context, SubEditActivity::class.java))
                },
                modifier = Modifier.size(44.dp).clip(CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = stringResource(R.string.satra_subs_add),
                    tint = SatraTokens.TealDeep,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (subscriptions.isEmpty()) {
            SatraGlassCard {
                Text(stringResource(R.string.satra_sub_empty), color = onGlassPrimary())
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(subscriptions, key = { it.first }) { (id, sub) ->
                SatraGlassCard(
                    onClick = {
                        onOpenEdit(id)
                        context.startActivity(
                            Intent(context, SubEditActivity::class.java).putExtra("subId", id),
                        )
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sub.showUsage) {
                            SatraUsageRing(
                                fraction = sub.usageFraction(),
                                warn = sub.isNearQuota(0.9f) || sub.isExpired(),
                                size = 52.dp,
                            )
                            Spacer(Modifier.size(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                sub.profileTitle?.takeIf { it.isNotBlank() } ?: sub.remarks,
                                style = MaterialTheme.typography.titleMedium,
                                color = onGlassPrimary(),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val count = serverCounts[id] ?: 0
                            Text(
                                stringResource(R.string.satra_sub_servers_count, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = onGlassSecondary(),
                            )
                            if (sub.url.isNotBlank()) {
                                Text(
                                    sub.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onGlassSecondary(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (sub.lastUpdated > 0L) {
                                Text(
                                    Utils.formatTimestamp(sub.lastUpdated),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onGlassSecondary(),
                                )
                            }
                            if (sub.showUsage) {
                                val used = sub.usedBytes()
                                Text(
                                    if (sub.usageFraction() != null)
                                        "${used.toTrafficString()} / ${sub.total.toTrafficString()}"
                                    else
                                        "${used.toTrafficString()} · ${stringResource(R.string.satra_sub_unlimited)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = onGlassPrimary(),
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_24dp),
                                contentDescription = stringResource(R.string.satra_sub_settings),
                                tint = SatraTokens.Purple,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable {
                                        onOpenEdit(id)
                                        context.startActivity(
                                            Intent(context, SubEditActivity::class.java)
                                                .putExtra("subId", id),
                                        )
                                    }
                                    .padding(2.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            SatraGlassToggle(
                                checked = sub.enabled,
                                onCheckedChange = { onToggleEnabled(id, it) },
                                onLabel = stringResource(R.string.satra_settings_on),
                                offLabel = stringResource(R.string.satra_settings_off),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.satra_subs_enabled),
                                style = MaterialTheme.typography.labelSmall,
                                color = onGlassSecondary(),
                            )
                        }
                    }
                }
            }
        }
    }
}
