package app.flicky.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.ui.components.cards.AdaptiveAppCard
import kotlin.Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onSyncClick: () -> Unit,
    isSyncing: Boolean,
    onAppClick: (FDroidApp) -> Unit,
    progress: Float
) {
    val categories by AppGraph.appRepo.categories().collectAsState(initial = emptyList())
    val apps by AppGraph.appRepo.appsFlow("", SortOption.Updated, hideAnti = false)
        .collectAsState(initial = emptyList())

    var selected by remember { mutableStateOf("All") }
    val filtered by remember(selected, apps) {
        mutableStateOf(if (selected == "All") apps else apps.filter { it.category == selected })
    }

    val cfg = LocalConfiguration.current
    val gridCells = remember(cfg.screenWidthDp) { GridCells.Adaptive(minSize = 220.dp) }
    val animatedProgress by animateFloatAsState(progress, label = "categories_sync_progress")

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "Categories",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyVerticalGrid(
                columns = gridCells,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            )
            {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
//                            FilledTonalButton(
//                                onClick = onSyncClick,
//                                enabled = !isSyncing
//                            ) {
//                                if (isSyncing) {
//                                    CircularProgressIndicator(
//                                        modifier = Modifier.size(16.dp),
//                                        strokeWidth = 2.dp
//                                    )
//                                    Spacer(Modifier.width(8.dp))
//                                    Text("Syncing…")
//                                } else {
//                                    Text("Sync Now")
//                                }
//                            }
                        }
                        if (isSyncing) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Filter chips row
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChipCategory(
                            label = "All",
                            count = apps.size,
                            selected = selected == "All",
                            onSelect = { selected = "All" }
                        )
                        categories.forEach { c ->
                            val count = apps.count { it.category == c }
                            FilterChipCategory(
                                label = c,
                                count = count,
                                selected = selected == c,
                                onSelect = { selected = c }
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "No apps in this category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Grid of apps
                    items(filtered, key = { it.packageName }) { app ->
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
private fun FilterChipCategory(label: String, count: Int, selected: Boolean, onSelect: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text("$label ($count)") },
        leadingIcon = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}