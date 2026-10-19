package com.v2ray.ang.ui.compose.satra

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v2ray.ang.R
import com.v2ray.ang.ui.apppicker.AppListViewModel

/**
 * Searchable app list with icons. [multi] = false closes on first tap; otherwise a Done button
 * returns the whole selection.
 */
@Composable
fun AppPickerSheet(
    title: String,
    selected: Set<String>,
    multi: Boolean,
    onDismiss: () -> Unit,
    onResult: (Set<String>) -> Unit,
    disabled: Set<String> = emptySet(),
) {
    val vm: AppListViewModel = viewModel()
    LaunchedEffect(Unit) { vm.ensureLoaded() }
    val apps by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf(selected) }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.appName.contains(q, true) || it.packageName.contains(q, true) }
    }
    SatraSheet(onDismiss = onDismiss) {
        SheetTitle(title)
        SatraSearchField(
            value = query, onValueChange = { query = it },
            placeholder = stringResource(R.string.sx_search),
            clearLabel = stringResource(R.string.sx_clear),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 460.dp)) {
            if (loading && apps.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { SatraSpinner() }
            } else {
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        val isOn = app.packageName in picked
                        val isDisabled = app.packageName in disabled
                        SatraRow(
                            title = app.appName,
                            subtitle = if (isDisabled) stringResource(R.string.sx_already_limited) else app.packageName,
                            leading = { AppIcon(app.packageName, size = 40.dp) },
                            enabled = !isDisabled,
                            trailing = if (multi) ({
                                if (isOn) Icon(SatraIcons.Check, null, tint = Satra.colors.accent, modifier = Modifier.size(22.dp))
                            }) else null,
                            onClick = {
                                if (multi) {
                                    picked = if (isOn) picked - app.packageName else picked + app.packageName
                                } else {
                                    onResult(setOf(app.packageName))
                                }
                            },
                        )
                    }
                }
            }
        }
        if (multi) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                SatraButton(stringResource(R.string.sx_done), { onResult(picked) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
