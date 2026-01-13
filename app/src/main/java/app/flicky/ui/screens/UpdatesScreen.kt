package app.flicky.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.R
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.AppUpdatePreference
import app.flicky.helper.DeviceUtils
import app.flicky.helper.cardAsFocusGroup
import app.flicky.install.TaskStage
import app.flicky.ui.components.AppIcon
import app.flicky.ui.components.AppTexts
import app.flicky.ui.components.DebugOverlay
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.ui.routes.UpdatesActions
import app.flicky.viewmodel.UpdatesUi

@Composable
fun UpdatesScreen(
    ui: UpdatesUi,
    actions: UpdatesActions,
    installerTasks: Map<String, TaskStage>,
    isBatchUpdating: Boolean = false,
    batchProgress: Float = 0f
) {
    val cfg = LocalConfiguration.current
    val gridCells = remember(cfg.screenWidthDp) {
        GridCells.Adaptive(minSize = 320.dp)
    }
    val ctx = LocalContext.current
    val isTV = remember { DeviceUtils.isTV(ctx.packageManager) }

    val suppressed = ui.suppressed
    var showIgnored by rememberSaveable { mutableStateOf(false) }
    var showInstalled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(suppressed) {
        if (suppressed.isEmpty() && showIgnored) showIgnored = false
    }

    val errorCount = remember(installerTasks) {
        installerTasks.count { (_, stage) ->
            stage is TaskStage.Finished && !stage.success
        }
    }
    var dismissedErrors by remember { mutableStateOf(false) }

    val errMap by AppGraph.installer.errors.collectAsState(initial = emptyMap())
    val failedPkgs = installerTasks.filterValues { s ->
        s is TaskStage.Finished && !s.success
    }.keys

    val failedDetails = remember(errMap, failedPkgs, ui.updates, ui.suppressed) {
        val nameByPkg = (ui.updates + ui.suppressed + ui.installed).associateBy({ it.packageName }, { it.name })
        failedPkgs.mapNotNull { pkg ->
            errMap[pkg]?.let { msg -> (nameByPkg[pkg] ?: pkg) to msg }
        }
    }

    MyScreenScaffold(
        title = when {
            suppressed.isNotEmpty() && ui.updates.isNotEmpty() && !isTV -> ""
            else -> stringResource(R.string.nav_updates)
        },
        actions = {
            if (isBatchUpdating) {
                // Show batch progress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { batchProgress },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.primary
                    )
//                    Text(
//                        stringResource(
//                            R.string.updating_count,
//                            (batchProgress * ui.updates.size).toInt(),
//                            ui.updates.size
//                        ),
//                        style = typography.bodyMedium
//                    )
                    TextButton(
                        onClick = { actions.cancelBatch() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            } else if (ui.updates.isNotEmpty()) {
                Button(
                    onClick = actions::updateAll,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.update_all, ui.updates.size))
                }
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
            if (errorCount > 0 && !dismissedErrors) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBanner(
                        message = stringResource(R.string.installation_errors, errorCount),
                        details = failedDetails,
                        onDismiss = { dismissedErrors = true }
                    )
                }
            }

            if (ui.updates.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyUpdatesCard()
                }
            } else {
                val activeUpdates = ui.updates.filter { app ->
                    val stage = installerTasks[app.packageName]
                    stage != null && stage !is TaskStage.Finished && stage !is TaskStage.Cancelled
                }

                if (activeUpdates.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.updating),
                            style = typography.titleSmall,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(activeUpdates, key = { "active_${it.packageName}" }) { app ->
                        UpdateCard(
                            app = app,
                            stage = installerTasks[app.packageName],
                            installedVersionName = ui.installedVersionsName[app.packageName],
                            installedVersionCode = ui.installedVersionsCode[app.packageName],
                            actions = actions,
                            pref = ui.ignoredPrefs[app.packageName],
                            isTV = isTV,
                            isActive = true
                        )
                    }
                }

                val pendingUpdates = ui.updates.filter { app ->
                    val stage = installerTasks[app.packageName]
                    stage == null || stage is TaskStage.Finished || stage is TaskStage.Cancelled
                }

                if (pendingUpdates.isNotEmpty()) {
                    if (activeUpdates.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.available_updates),
                            style = typography.titleSmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(pendingUpdates, key = { "update_${it.packageName}" }) { app ->
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
                        isTV = isTV,
                        isIgnored = true
                    )
                }
            }

            if (ui.installed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showInstalled = !showInstalled }) {
                            Icon(
                                if (showInstalled) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.installed_apps, ui.installed.size),
                                style = typography.titleMedium,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }

                if (showInstalled) {
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

    val settings by AppGraph.settings.settingsFlow.collectAsState(initial = AppSettings())
    Box(Modifier.fillMaxSize()) {
        DebugOverlay(visible = settings.showDebugInfo)
    }
}

@Composable
private fun UpdateCard(
    app: FDroidApp,
    stage: TaskStage?,
    installedVersionName: String?,
    installedVersionCode: Long?,
    actions: UpdatesActions,
    pref: AppUpdatePreference?,
    isTV: Boolean,
    isActive: Boolean = false,
    isIgnored: Boolean = false
) {
    val containerColor = when {
        isActive -> colorScheme.primaryContainer.copy(alpha = 0.3f)
        isIgnored -> colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> colorScheme.surface
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = colorScheme.onSurface
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { actions.openDetails(app) }
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

                            // Show preferred repo if set
                            if (pref?.preferredRepoUrl != null && !isIgnored) {
                                Spacer(Modifier.height(4.dp))
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            app.repository,
                                            style = typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                }

                if (!isIgnored) {
                    IgnoreMenu(
                        pref = pref,
                        currentVersionCode = app.versionCode.toLong(),
                        onIgnoreThisVersion = { actions.ignoreThisVersion(app) },
                        onIgnoreAll = { actions.ignoreAll(app) },
                        onStopIgnoring = { actions.stopIgnoring(app) },
                        isTV = isTV
                    )
                } else {
                    IconButton(
                        onClick = { actions.stopIgnoring(app) },
                        modifier = Modifier.focusProperties { canFocus = true }
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = stringResource(R.string.stop_ignoring),
                            tint = colorScheme.primary
                        )
                    }
                }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                val label = when (stage) {
                    is TaskStage.Downloading -> "Downloading ${(progress * 100).toInt()}%"
                    is TaskStage.Verifying -> "Verifying"
                    is TaskStage.Installing -> "Installing ${(progress * 100).toInt()}%"
                    is TaskStage.Finished -> if (stage.success) "Completed" else "Failed"
                    is TaskStage.Cancelled -> "Cancelled"
                    else -> ""
                }

                Text(
                    label,
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            } else if (!isIgnored) {
                Button(
                    onClick = { actions.updateOne(app) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    enabled = stage !is TaskStage.Finished || !stage.success
                ) {
                    Text(stringResource(R.string.action_update))
                }
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
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { onOpenDetails() },
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
    pref: AppUpdatePreference?,
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
            modifier = Modifier
                .focusProperties { canFocus = true }
                .semantics { role = Role.Button }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options)
            )
        }

        val isEffectivelyIgnored = pref?.ignoreUpdates == true ||
                ((pref?.ignoreVersionCode ?: 0L) >= currentVersionCode)

        DropdownMenu(
            expanded = open,
            containerColor = colorScheme.background,
            onDismissRequest = { open = false }
        ) {
            if (isEffectivelyIgnored) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stop_ignoring)) },
                    onClick = {
                        open = false
                        onStopIgnoring()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ignore_this_version)) },
                    onClick = {
                        open = false
                        onIgnoreThisVersion()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ignore_all_updates)) },
                    onClick = {
                        open = false
                        onIgnoreAll()
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyUpdatesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.no_updates),
                    style = typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    details: List<Pair<String, String>> = emptyList(),
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.errorContainer
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, contentDescription = null, tint = colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text(message, modifier = Modifier.weight(1f), style = typography.bodyMedium, color = colorScheme.onErrorContainer)
                if (details.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Hide details" else "Show details")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss), tint = colorScheme.onErrorContainer)
                }
            }
            if (expanded && details.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                details.forEach { (name, msg) ->
                    Text("• $name: $msg", style = typography.labelSmall, color = colorScheme.onErrorContainer)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}