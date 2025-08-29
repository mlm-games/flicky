package app.flicky.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.flicky.data.external.UpdatesPreference
import app.flicky.data.model.FDroidApp
import app.flicky.helper.DeviceUtils
import app.flicky.helper.cardAsFocusGroup
import app.flicky.ui.components.AppIcon
import app.flicky.ui.components.AppTexts
import app.flicky.ui.components.MyScreenScaffold
import app.flicky.ui.routes.UpdatesActions
import app.flicky.viewmodel.UpdatesUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UpdatesScreen(
    ui: UpdatesUiState,
    actions: UpdatesActions
) {
    val cfg = LocalConfiguration.current
    val gridCells = remember(cfg.screenWidthDp) { GridCells.Adaptive(minSize = 320.dp) }
    val ctx = LocalContext.current
    val isTV = remember { DeviceUtils.isTV(ctx.packageManager) }

    // Derive ignored/suppressed list (candidate update that is hidden due to ignore rules)
    val suppressed = remember(ui) {
        ui.installed.filter { app ->
            val cur = ui.installedVersionsCode[app.packageName] ?: 0L
            val candidate = app.versionCode.toLong() > cur
            if (!candidate) return@filter false
            val pref = ui.ignoredPrefs[app.packageName]
            pref?.ignoreUpdates == true || ((pref?.ignoreVersionCode ?: 0L) >= app.versionCode.toLong())
        }
    }

    var showIgnored by remember { mutableStateOf(false) }

    MyScreenScaffold(
        title = "Updates",
        actions = {
            if (ui.updates.isNotEmpty()) {
                Button(
                    onClick = actions::updateAll,
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text("Update All (${ui.updates.size})") }
            }
            if (suppressed.isNotEmpty()) {
                OutlinedButton(onClick = { showIgnored = !showIgnored }) {
                    Text(if (showIgnored) "Hide Ignored (${suppressed.size})" else "Show Ignored (${suppressed.size})")
                }
            }
        }
    ) {
        LazyVerticalGrid(
            columns = gridCells,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Updates section (or empty)
            if (ui.updates.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyUpdatesCard()
                }
            } else {
                items(ui.updates, key = { "update_${it.packageName}" }) { app ->
                    val installedVn = ui.installedVersionsName[app.packageName]
                    val installedVc = ui.installedVersionsCode[app.packageName]
                    val pref = ui.ignoredPrefs[app.packageName]
                    UpdateCard(
                        app = app,
                        installing = app.packageName in ui.installingPackages,
                        progress = ui.installProgress[app.packageName] ?: 0f,
                        installedVersionName = installedVn,
                        installedVersionCode = installedVc,
                        onUpdate = { actions.updateOne(app) },
                        onOpenDetails = { actions.openDetails(app) },
                        pref = pref,
                        onIgnoreThisVersion = { actions.ignoreThisVersion(app) },
                        onIgnoreAll = { actions.ignoreAll(app) },
                        onStopIgnoring = { actions.stopIgnoring(app) },
                        isTV = isTV
                    )
                }
            }

            // Installed section
            if (ui.installed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Installed Apps",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(ui.installed, key = { "installed_${it.packageName}" }) { app ->
                    val installedVn = ui.installedVersionsName[app.packageName]
                    val installedVc = ui.installedVersionsCode[app.packageName]
                    InstalledCard(
                        app = app,
                        installedVersionName = installedVn,
                        installedVersionCode = installedVc,
                        onOpenDetails = { actions.openDetails(app) }
                    )
                }
            }

            // Ignored/suppressed updates section
            if (showIgnored && suppressed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ignored updates",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(suppressed, key = { "ignored_${it.packageName}" }) { app ->
                    val installedVn = ui.installedVersionsName[app.packageName]
                    val installedVc = ui.installedVersionsCode[app.packageName]
                    val pref = ui.ignoredPrefs[app.packageName]
                    UpdateCard(
                        app = app,
                        installing = app.packageName in ui.installingPackages,
                        progress = ui.installProgress[app.packageName] ?: 0f,
                        installedVersionName = installedVn,
                        installedVersionCode = installedVc,
                        onUpdate = { actions.updateOne(app) },
                        onOpenDetails = { actions.openDetails(app) },
                        pref = pref,
                        onIgnoreThisVersion = { actions.ignoreThisVersion(app) },
                        onIgnoreAll = { actions.ignoreAll(app) },
                        onStopIgnoring = { actions.stopIgnoring(app) },
                        isTV = isTV
                    )
                }
            }
        }
    }
}

/* ---------- UI blocks (private, DRY) ---------- */

@Composable
private fun EmptyUpdatesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No updates available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UpdateCard(
    app: FDroidApp,
    installing: Boolean,
    progress: Float,
    installedVersionName: String?,
    installedVersionCode: Long?,
    onUpdate: () -> Unit,
    onOpenDetails: () -> Unit,
    pref: UpdatesPreference?,
    onIgnoreThisVersion: () -> Unit,
    onIgnoreAll: () -> Unit,
    onStopIgnoring: () -> Unit,
    isTV: Boolean
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .cardAsFocusGroup(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(Modifier.clickable { onOpenDetails() })
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(app.name, app.iconUrl)
                        Column(Modifier.weight(1f)) {
                            AppTexts(
                                name = app.name,
                                installedLabel = installedVersionName?.takeIf { it.isNotBlank() }
                                    ?: installedVersionCode?.let { "v$it" },
                                newLabel = app.version,
                                summary = app.summary
                            )
                        }
                    }
                }

                IgnoreMenu(
                    pref = pref,
                    currentVersionCode = app.versionCode.toLong(),
                    onIgnoreThisVersion = onIgnoreThisVersion,
                    onIgnoreAll = onIgnoreAll,
                    onStopIgnoring = onStopIgnoring,
                    isTV = isTV
                )
            }

            Spacer(Modifier.height(8.dp))

            if (installing) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Button(
                    onClick = onUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Update") }
            }
        }
    }
}

@Composable
private fun InstalledCard(
    app: FDroidApp,
    installedVersionName: String?,
    installedVersionCode: Long?,
    onOpenDetails: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { onOpenDetails() },
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(app.name, app.iconUrl)
            Column(Modifier.weight(1f)) {
                val installedLabel = installedVersionName?.takeIf { it.isNotBlank() }
                    ?: installedVersionCode?.let { "v$it" }
                    ?: "Unknown"
                AppTexts(
                    name = app.name,
                    installedLabel = if (installedLabel == app.version) installedLabel else "$installedLabel → ${app.version}",
                    newLabel = null,
                    summary = app.summary
                )
            }
        }
    }
}

@Composable
private fun IgnoreMenu(
    pref: UpdatesPreference?,
    currentVersionCode: Long,
    onIgnoreThisVersion: () -> Unit,
    onIgnoreAll: () -> Unit,
    onStopIgnoring: () -> Unit,
    isTV: Boolean
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.focusable()
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More"
            )
        }
        val isEffectivelyIgnored =
            pref?.ignoreUpdates == true || ((pref?.ignoreVersionCode ?: 0L) >= currentVersionCode)

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (isEffectivelyIgnored) {
                DropdownMenuItem(
                    text = { Text("Stop ignoring updates") },
                    onClick = { open = false; onStopIgnoring() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Ignore this version") },
                    onClick = { open = false; onIgnoreThisVersion() }
                )
                DropdownMenuItem(
                    text = { Text("Ignore all updates") },
                    onClick = { open = false; onIgnoreAll() }
                )
            }
        }
    }
}