package app.flicky.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flicky.R
import app.flicky.data.external.UpdatesPreference
import app.flicky.data.model.FDroidApp
import app.flicky.helper.DeviceUtils
import app.flicky.helper.cardAsFocusGroup
import app.flicky.install.TaskStage
import app.flicky.ui.components.AppIcon
import app.flicky.ui.components.AppTexts
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.ui.routes.UpdatesActions
import app.flicky.viewmodel.UpdatesUi

@Composable
fun UpdatesScreen(
    ui: UpdatesUi,
    actions: UpdatesActions,
    installerTasks: Map<String, TaskStage>
) {
    val cfg = LocalConfiguration.current
    val gridCells = remember(cfg.screenWidthDp) { GridCells.Adaptive(minSize = 320.dp) }
    val ctx = LocalContext.current
    val isTV = remember { DeviceUtils.isTV(ctx.packageManager) }

    val suppressed = ui.suppressed

    var showIgnored by remember { mutableStateOf(false) }

    LaunchedEffect(suppressed) {
        if (suppressed.isEmpty() && showIgnored) showIgnored = false
    }


    MyScreenScaffold(
        // Hiding for space
        title = if (suppressed.isNotEmpty() && ui.updates.isNotEmpty() && !isTV) "" else stringResource(R.string.nav_updates),
        actions = {
            if (ui.updates.isNotEmpty()) {
                Button(
                    onClick = actions::updateAll,
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(stringResource(R.string.update_all, ui.updates.size)) }
            }
            AnimatedVisibility(visible = suppressed.isNotEmpty()) {
                OutlinedButton(onClick = { showIgnored = !showIgnored }) {
                    Text(
                        stringResource(
                            if (showIgnored) R.string.hide_ignored else R.string.show_ignored,
                            suppressed.size
                        )
                    )
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
            if (ui.updates.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyUpdatesCard()
                }
            } else {
                items(ui.updates, key = { "update_${it.packageName}" }) { app ->
                    UpdateCard(
                        app = app,
                        stage = installerTasks[app.packageName],
                        installedVersionName = ui.installedVersionsName[app.packageName],
                        installedVersionCode = ui.installedVersionsCode[app.packageName],
                        actions = actions,
                        pref = ui.ignoredPrefs[app.packageName],
                        isTV = isTV
                    )
                }
            }

            if (showIgnored && suppressed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ignored_updates),
                        style = typography.titleMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(suppressed, key = { "ignored_${it.packageName}" }) { app ->
                    UpdateCard(
                        app = app,
                        stage = installerTasks[app.packageName],
                        installedVersionName = ui.installedVersionsName[app.packageName],
                        installedVersionCode = ui.installedVersionsCode[app.packageName],
                        actions = actions,
                        pref = ui.ignoredPrefs[app.packageName],
                        isTV = isTV
                    )
                }
            }

            if (ui.installed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.installed_apps),
                        style = typography.titleMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(ui.installed, key = { "installed_${it.packageName}" }) { app ->
                    InstalledCard(
                        app = app,
                        installedVersionName = ui.installedVersionsName[app.packageName],
                        installedVersionCode = ui.installedVersionsCode[app.packageName],
                        onOpenDetails = { actions.openDetails(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(
    app: FDroidApp,
    stage: TaskStage?,
    installedVersionName: String?,
    installedVersionCode: Long?,
    actions: UpdatesActions,
    pref: UpdatesPreference?,
    isTV: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().cardAsFocusGroup(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier.weight(1f).clickable { actions.openDetails(app) }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(app.name, app.iconUrl)
                        val installedLabel = installedVersionName?.takeIf { it.isNotBlank() }
                            ?: installedVersionCode?.let { stringResource(R.string.version_prefix, it) }
                        Column(Modifier.weight(1f)) {
                            AppTexts(
                                name = app.name,
                                installedLabel = installedLabel,
                                newLabel = app.version,
                                summary = app.summary
                            )
                        }
                    }
                }
                IgnoreMenu(
                    pref = pref,
                    currentVersionCode = app.versionCode.toLong(),
                    onIgnoreThisVersion = { actions.ignoreThisVersion(app) },
                    onIgnoreAll = { actions.ignoreAll(app) },
                    onStopIgnoring = { actions.stopIgnoring(app) },
                    isTV = isTV
                )
            }
            Spacer(Modifier.height(8.dp))
            if (stage != null && stage !is TaskStage.Finished && stage !is TaskStage.Cancelled) {
                val progress = when (stage) {
                    is TaskStage.Downloading -> stage.progress
                    is TaskStage.Verifying -> 0.995f
                    is TaskStage.Installing -> stage.progress
                    else -> 0f
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                val label = when (stage) {
                    is TaskStage.Downloading -> "Downloading ${(progress * 100).toInt()}%"
                    is TaskStage.Verifying   -> "Verifying"
                    is TaskStage.Installing  -> "Installing ${(progress * 100).toInt()}%"
                    is TaskStage.Finished    -> if (stage.success) "Completed" else "Failed"
                    is TaskStage.Cancelled   -> "Cancelled"
                    else -> ""
                }
                Text(label, style = typography.labelSmall, color = colorScheme.onSurfaceVariant)
            } else {
                Button(
                    onClick = { actions.updateOne(app) },
                    modifier = Modifier.fillMaxWidth().focusable(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    )
                ) { Text(stringResource(R.string.action_update)) }
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
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).clickable { onOpenDetails() },
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(app.name, app.iconUrl)
            val installedLabelText = installedVersionName?.takeIf { it.isNotBlank() }
                ?: installedVersionCode?.let { stringResource(R.string.version_prefix, it) }
                ?: stringResource(R.string.unknown)
            val finalLabel = if (installedLabelText == app.version) {
                installedLabelText
            } else {
                stringResource(R.string.version_update_format, installedLabelText, app.version)
            }
            Column(Modifier.weight(1f)) {
                AppTexts(
                    name = app.name,
                    installedLabel = finalLabel,
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
                contentDescription = stringResource(R.string.more_options)
            )
        }
        val isEffectivelyIgnored =
            pref?.ignoreUpdates == true || ((pref?.ignoreVersionCode ?: 0L) >= currentVersionCode)

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (isEffectivelyIgnored) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stop_ignoring)) },
                    onClick = { open = false; onStopIgnoring() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ignore_this_version)) },
                    onClick = { open = false; onIgnoreThisVersion() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ignore_all_updates)) },
                    onClick = { open = false; onIgnoreAll() }
                )
            }
        }
    }
}

@Composable
private fun EmptyUpdatesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.no_updates),
                style = typography.bodyLarge,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}