package app.flicky.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.flicky.R
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.ui.components.VoiceSearchButton
import app.flicky.ui.components.cards.AdaptiveAppCard
import app.flicky.ui.dialogs.FlickyDialog
import app.flicky.viewmodel.UiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    apps: LazyPagingItems<FDroidApp>,
    query: String,
    sort: SortOption,
    onSortChange: (SortOption) -> Unit,
    onSearchChange: (String) -> Unit,
    onAppClick: (FDroidApp) -> Unit,
    onSyncClick: () -> Unit,
    onForceSyncClick: () -> Unit,
    onClearAppsClick: () -> Unit,
    isSyncing: Boolean,
    isTv: Boolean,
    syncStatusRes: UiText?,
    progress: Float,
    errorMessage: UiText?,
    onDismissError: () -> Unit
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "sync_progress_anim")
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortDialog by remember { mutableStateOf(false) }

    val resolvedError = errorMessage?.asString()
    LaunchedEffect(resolvedError) {
        resolvedError?.let {
            snackbarHostState.showSnackbar(it)
            onDismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TvAwareDockedSearchBar(
                            query = query,
                            isTv = isTv,
                            onImmediateChange = onSearchChange,
                            onCommit = onSearchChange,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sortLabel = when (sort) {
                            SortOption.Name -> stringResource(R.string.sort_name)
                            SortOption.Updated -> stringResource(R.string.sort_updated)
                            SortOption.Size -> stringResource(R.string.sort_size)
                            SortOption.Added -> stringResource(R.string.sort_added)
                        }
                        AssistChip(
                            onClick = { showSortDialog = true },
                            label = { Text(stringResource(R.string.sort_prefix, sortLabel)) },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.sort_by),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        Spacer(Modifier.weight(1f))

                        FilledTonalButton(
                            onClick = onSyncClick,
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isSyncing) stringResource(R.string.syncing) else stringResource(R.string.action_sync))
                        }

                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.force_sync)) },
                                    onClick = {
                                        menuOpen = false
                                        onForceSyncClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.clear_all_apps)) },
                                    onClick = {
                                        menuOpen = false
                                        onClearAppsClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ClearAll, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }

                val status = syncStatusRes?.asString()
                if (isSyncing) {
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(4.dp))
                    if (!status.isNullOrBlank()) {
                        Text(
                            text = status,
                            style = typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (apps.itemCount == 0 && !isSyncing) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_apps_found),
                        style = typography.titleLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (query.isNotEmpty()) {
                        Text(
                            stringResource(R.string.try_different_search),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(apps.itemCount, key = { idx -> apps[idx]?.packageName ?: "placeholder_$idx" }) { idx ->
                        apps[idx]?.let { app ->
                            AdaptiveAppCard(app = app, onClick = { onAppClick(app) })
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        SortDialog(
            currentSort = sort,
            onSortSelected = {
                onSortChange(it)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }
}

@Composable
private fun SortDialog(
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
                val optionText = when (option) {
                    SortOption.Name -> stringResource(R.string.sort_name)
                    SortOption.Updated -> stringResource(R.string.sort_updated)
                    SortOption.Size -> stringResource(R.string.sort_size)
                    SortOption.Added -> stringResource(R.string.sort_added)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortSelected(option) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSort == option,
                        onClick = { onSortSelected(option) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colorScheme.primary,
                            unselectedColor = colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        optionText,
                        style = typography.bodyLarge,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun UiText.asString(): String {
    return when (this) {
        is UiText.StringResource -> stringResource(this.resId, *this.args.toTypedArray())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvAwareDockedSearchBar(
    query: String,
    isTv: Boolean,
    onImmediateChange: (String) -> Unit,
    onCommit: (String) -> Unit
) {
    var localQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { if (query != localQuery) localQuery = query }

    var active by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val okKeys = remember { setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter) }

    val onActiveChange : (Boolean) -> Unit = { isActive ->
        active = isActive
        if (!isActive) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    val colors1 = SearchBarDefaults.colors(
        colorScheme.surfaceVariant.copy(alpha = 0.5f),
        colorScheme.surfaceVariant.copy(alpha = 0.7f),
    )
    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = localQuery,
                onQueryChange = { new ->
                    localQuery = new
                    onImmediateChange(new)
                },
                onSearch = {
                    onCommit(localQuery)
                    active = false
                    keyboard?.hide()
                    focusManager.clearFocus()
                },
                expanded = false,
                onExpandedChange = onActiveChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (localQuery.isNotEmpty()) {
                        IconButton(onClick = { localQuery = ""; onImmediateChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                    else {
                        VoiceSearchButton {
                            localQuery = it
                            onImmediateChange(it)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.surfaceVariant,
                    focusedTextColor = colorScheme.onSurface,
                    unfocusedTextColor = colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth(1f)
            )
        },
        expanded = false,
        onExpandedChange = onActiveChange,
        modifier = Modifier.fillMaxWidth(1f)
            .onPreviewKeyEvent { e ->
                when {
                    isTv && !active && e.type == KeyEventType.KeyDown && e.key in okKeys -> {
                        active = true; keyboard?.show(); true
                    }
                    isTv && active && e.type == KeyEventType.KeyDown && e.key == Key.Back -> {
                        active = false; keyboard?.hide(); true
                    }
                    else -> false
                }
            },
        shape = SearchBarDefaults.dockedShape,
        colors = colors1,
        tonalElevation = SearchBarDefaults.TonalElevation,
        shadowElevation = SearchBarDefaults.ShadowElevation,
        content = { /* suggestions/history later? */ },
    )
}