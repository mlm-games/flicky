package app.flicky.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.ui.components.VoiceSearchButton
import app.flicky.ui.components.cards.AdaptiveAppCard

@Composable
fun BrowseScreen(
    apps: List<FDroidApp>,
    query: String,
    sort: SortOption,
    onSortChange: (SortOption)->Unit,
    onSearchChange: (String)->Unit,
    onAppClick: (FDroidApp)->Unit,
    onSyncClick: () -> Unit,
    onForceSyncClick: () -> Unit,
    isSyncing: Boolean,
    progress: Float,
    errorMessage: String?,
    onDismissError: () -> Unit
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "sync_progress_anim")
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar on error
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            // Header row: Search + Voice + Sync + Sort + Menu
            Row(verticalAlignment = CenterVertically) {
                TextField(
                    value = query,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search apps...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                VoiceSearchButton { onSearchChange(it) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSyncClick, enabled = !isSyncing) { Text("Sync Now") }
                Spacer(Modifier.width(8.dp))
                SortButton(sort = sort, onSortChange = onSortChange)
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Force Sync (Clear cache)") },
                            onClick = {
                                menuOpen = false
                                onForceSyncClick()
                            }
                        )
                    }
                }
            }

            // Progress bar (only when syncing)
            if (isSyncing) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))
            if (apps.isEmpty()) {
                Text("No apps found", color = MaterialTheme.colorScheme.outline)
            } else {
                val columns = when {
                    widthDp > 1400 -> 6
                    widthDp > 1200 -> 5
                    widthDp > 900 -> 4
                    widthDp > 600 -> 3
                    else -> 2
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AdaptiveAppCard(
                            app = app,
                            autofocus = false,
                            onClick = { onAppClick(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortButton(sort: SortOption, onSortChange: (SortOption)->Unit) {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }) { Text("Sort: ${sort.name}") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Sort by") },
            text = {
                Column {
                    SortOption.entries.forEach {
                        TextButton(onClick = { onSortChange(it); open=false }) { Text(it.name) }
                    }
                }
            },
            confirmButton = {}
        )
    }
}