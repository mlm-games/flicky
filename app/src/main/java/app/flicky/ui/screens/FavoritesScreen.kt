@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.flicky.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.flicky.R
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.ui.components.AdaptiveAppCard
import app.flicky.ui.components.global.FlickyDialog
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.viewmodel.FavoritesUi

@Composable
fun FavoritesScreen(
    ui: FavoritesUi,
    sort: SortOption,
    onSortChange: (SortOption) -> Unit,
    onAppClick: (FDroidApp) -> Unit,
    onRemoveFavorite: (String) -> Unit
) {
    val cfg = LocalConfiguration.current
    val columns = when {
        cfg.screenWidthDp > 1400 -> 6
        cfg.screenWidthDp > 1200 -> 5
        cfg.screenWidthDp > 900 -> 4
        cfg.screenWidthDp > 600 -> 3
        else -> 2
    }

    var showSortDialog by remember { mutableStateOf(false) }
    var appToRemove by remember { mutableStateOf<FDroidApp?>(null) }

    MyScreenScaffold(
        title = stringResource(R.string.nav_favorites),
        actions = {
            if (ui.favorites.isNotEmpty()) {
                val sortLabel = when (sort) {
                    SortOption.Name -> stringResource(R.string.sort_name)
                    SortOption.Updated -> stringResource(R.string.sort_updated)
                    SortOption.Size -> stringResource(R.string.sort_size)
                    SortOption.Added -> stringResource(R.string.sort_added)
                }
                AssistChip(
                    onClick = { showSortDialog = true },
                    label = { Text(sortLabel) },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.sort_by),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    ) {
        if (ui.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (ui.favorites.isEmpty()) {
            EmptyFavoritesContent()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = ui.favorites,
                    key = { it.packageName }
                ) { app ->
                    FavoriteAppCard(
                        app = app,
                        isInstalled = ui.installedVersions.containsKey(app.packageName),
                        onClick = { onAppClick(app) },
                        onLongClick = { appToRemove = app }
                    )
                }
            }
        }
    }

    if (showSortDialog) {
        SortSelectionDialog(
            currentSort = sort,
            onSortSelected = {
                onSortChange(it)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    appToRemove?.let { app ->
        RemoveFavoriteDialog(
            appName = app.name,
            onConfirm = {
                onRemoveFavorite(app.packageName)
                appToRemove = null
            },
            onDismiss = { appToRemove = null }
        )
    }
}

@Composable
private fun FavoriteAppCard(
    app: FDroidApp,
    isInstalled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box {
        AdaptiveAppCard(
            app = app,
            onClick = onClick,
            onLongClick = onLongClick
        )
        
        // Installed indicator
        if (isInstalled) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = MaterialTheme.shapes.small,
                color = colorScheme.primaryContainer.copy(alpha = 0.9f)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.installed),
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp),
                    tint = colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun EmptyFavoritesContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.no_favorites),
                style = typography.titleLarge,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.no_favorites_hint),
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SortSelectionDialog(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    FlickyDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sort_by),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    ) {
        Column {
            SortOption.entries.forEach { option ->
                val label = when (option) {
                    SortOption.Name -> stringResource(R.string.sort_name)
                    SortOption.Updated -> stringResource(R.string.sort_updated)
                    SortOption.Size -> stringResource(R.string.sort_size)
                    SortOption.Added -> stringResource(R.string.sort_added)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { onSortSelected(option) })
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSort == option,
                        onClick = { onSortSelected(option) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun RemoveFavoriteDialog(
    appName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    FlickyDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.remove_favorite),
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.error
                )
            ) {
                Text(stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    ) {
        Text(
            stringResource(R.string.remove_favorite_confirm, appName),
            style = typography.bodyMedium
        )
    }
}