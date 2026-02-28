package app.flicky.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppSettings
import app.flicky.ui.components.AdaptiveAppCard
import app.flicky.ui.components.AppIcon
import app.flicky.ui.components.AppTexts
import app.flicky.R
import app.flicky.di.AppDependencies
import app.flicky.ui.components.global.MyScreenScaffold


@Composable
fun CategoriesScreen(
    isSyncing: Boolean,
    onAppClick: (FDroidApp) -> Unit,
    progress: Float,
    initialCategory: String = "All"
) {
    val categories by AppDependencies.appRepo.categories().collectAsState(initial = emptyList())
    val settingsState by AppGraph.settings.settingsFlow.collectAsState(initial = AppSettings())
    val sortOption = when (settingsState.defaultSort) {
        0 -> SortOption.Name
        1 -> SortOption.NameDesc
        2 -> SortOption.Updated
        3 -> SortOption.Size
        4 -> SortOption.Added
        else -> SortOption.Updated
    }
    val apps by AppDependencies.appRepo.appsFlow(
        query = "",
        sort = sortOption,
        hideAnti = false,
        showIncompatible = settingsState.showIncompatible
    ).collectAsState(initial = emptyList())

    var selected by rememberSaveable(initialCategory) { mutableStateOf(initialCategory) }
    val filtered by remember(selected, apps) {
        derivedStateOf { if (selected == "All") apps else apps.filter { it.category == selected } }
    }
    val cfg = LocalConfiguration.current
    val gridCells = remember(cfg.screenWidthDp) { GridCells.Adaptive(minSize = 220.dp) }
    val animatedProgress by animateFloatAsState(progress, label = "categories_sync_progress")

    MyScreenScaffold(
        title = stringResource(R.string.nav_categories),
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = colorScheme.background
        ) {
            if (settingsState.useListLayout) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Column {
                            if (isSyncing) {
                                Spacer(Modifier.height(8.dp))
                                LinearWavyProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    item {
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
                        item {
                            Text(
                                "No apps in this category",
                                style = typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(filtered, key = { it.packageName }) { app ->
                            CategoriesListRow(
                                app = app,
                                onClick = { onAppClick(app) }
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = gridCells,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                            }
                            if (isSyncing) {
                                Spacer(Modifier.height(8.dp))
                                LinearWavyProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

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
                                style = typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
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
            selectedContainerColor = colorScheme.primaryContainer,
            selectedLabelColor = colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun CategoriesListRow(app: FDroidApp, onClick: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = {})
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.name, app.iconUrl, size = 56.dp)
            Column(Modifier.weight(1f)) {
                AppTexts(
                    name = app.name,
                    installedLabel = null,
                    newLabel = null,
                    summary = app.summary
                )
            }
        }
    }
}