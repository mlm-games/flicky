package app.flicky.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flicky.data.external.UpdatesPreference
import app.flicky.data.model.FDroidApp
import app.flicky.helper.DeviceUtils
import app.flicky.ui.components.MyScreenScaffold
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    installed: List<FDroidApp>,
    updates: List<FDroidApp>,
    onUpdateAll: () -> Unit,
    onUpdateOne: (FDroidApp) -> Unit,
    onAppClick: (FDroidApp) -> Unit = {},
    installingPackages: Set<String> = emptySet(),
    installProgress: Map<String, Float> = emptyMap(),
    installedVersionsCode: Map<String, Long> = emptyMap(),
    installedVersionsName: Map<String, String> = emptyMap(),
    ignoredPrefs: Map<String, UpdatesPreference> = emptyMap(),
    onIgnoreThisVersion: (FDroidApp) -> Unit = {},
    onIgnoreAll: (FDroidApp) -> Unit = {},
    onStopIgnoring: (FDroidApp) -> Unit = {}
) {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val gridCells = remember(cfg.screenWidthDp) { GridCells.Adaptive(minSize = 320.dp) }

    MyScreenScaffold(
        title = "Updates",
        actions = {
            if (updates.isNotEmpty()) {
                Button(
                    onClick = onUpdateAll,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Update All (${updates.size})")
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
            // Empty updates state
            if (updates.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                "No updates available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(updates, key = { "update_${it.packageName}" }) { app ->
                    val installedVn = installedVersionsName[app.packageName]
                    val installedVc = installedVersionsCode[app.packageName]
                    val pref = ignoredPrefs[app.packageName]
                    UpdateCard(
                        app = app,
                        installing = app.packageName in installingPackages,
                        progress = installProgress[app.packageName] ?: 0f,
                        installedVersionName = installedVn,
                        installedVersionCode = installedVc,
                        onUpdate = { onUpdateOne(app) },
                        onClick = { onAppClick(app) },
                        pref = pref,
                        onIgnoreThisVersion = { onIgnoreThisVersion(app) },
                        onIgnoreAll = { onIgnoreAll(app) },
                        onStopIgnoring = { onStopIgnoring(app) }
                    )
                }
            }

            // Installed section
            if (installed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Installed Apps",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(installed, key = { "installed_${it.packageName}" }) { app ->
                    val installedVn = installedVersionsName[app.packageName]
                    val installedVc = installedVersionsCode[app.packageName]
                    InstalledCard(
                        app = app,
                        installedVersionName = installedVn,
                        installedVersionCode = installedVc,
                        onClick = { onAppClick(app) }
                    )
                }
            }
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
    onClick: () -> Unit,
    pref: UpdatesPreference?,
    onIgnoreThisVersion: () -> Unit,
    onIgnoreAll: () -> Unit,
    onStopIgnoring: () -> Unit
) {
    val ctx = LocalContext.current
    val isTV = remember { DeviceUtils.isTV(ctx.packageManager) }
    var menuOpen by remember { mutableStateOf(false) }

    ElevatedCard(
        // Card is NOT clickable; children are the focus/interaction points
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup() // predictable DPAD traversal among children
            .focusProperties { canFocus = false },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(0.dp)
                        .weight(1f)
                        .then(
                            if (!isTV) Modifier.clickable(onClick = onClick)
                            else Modifier
                                .focusProperties { canFocus = false }
                        )
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = app.iconUrl,
                            contentDescription = app.name,
                            modifier = Modifier.size(56.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val installedLabel = installedVersionName?.takeIf { it.isNotBlank() }
                                ?: installedVersionCode?.let { "v$it" }
                            installedLabel?.let {
                                Text(
                                    "Installed: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "New: ${app.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                app.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.focusable() // explicit TV focus target
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                            contentDescription = "More"
                        )
                    }

                    val isEffectivelyIgnored =
                        pref?.ignoreUpdates == true ||
                                ((pref?.ignoreVersionCode ?: 0) >= app.versionCode.toLong())

                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (isEffectivelyIgnored) {
                            DropdownMenuItem(
                                text = { Text("Stop ignoring updates") },
                                onClick = { menuOpen = false; onStopIgnoring() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Ignore this version") },
                                onClick = { menuOpen = false; onIgnoreThisVersion() }
                            )
                            DropdownMenuItem(
                                text = { Text("Ignore all updates") },
                                onClick = { menuOpen = false; onIgnoreAll() }
                            )
                        }
                    }
                }
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
                        .focusable(), // DPAD lands here; Enter triggers button, not the card
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Update")
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
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.name,
                modifier = Modifier.size(56.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val installedLabel = installedVersionName?.takeIf { it.isNotBlank() }
                    ?: installedVersionCode?.let { "v$it" }
                    ?: "Unknown"
                if (installedLabel == app.version) {
                    Text(
                        "Installed: $installedLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Installed: $installedLabel → ${app.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    app.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}