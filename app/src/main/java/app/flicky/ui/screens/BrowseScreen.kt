@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.flicky.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.flicky.AppGraph
import app.flicky.R
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppSettings
import app.flicky.di.AppDependencies
import app.flicky.install.TaskStage
import app.flicky.ui.components.AppIcon
import app.flicky.ui.components.AppTexts
import app.flicky.ui.components.VoiceSearchButton
import app.flicky.ui.components.AdaptiveAppCard
import app.flicky.ui.components.global.FlickyDialog
import app.flicky.viewmodel.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BrowseScreen(
    apps: LazyPagingItems<FDroidApp>,
    query: String,
    sort: SortOption,
    onSortChange: (SortOption) -> Unit,
    onSearchChange: (String) -> Unit,
    onAppClick: (FDroidApp) -> Unit,
    onCategoriesClick: (String?) -> Unit,
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
    val s by AppGraph.settings.settingsFlow.collectAsState(initial = AppSettings())

    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    var installFromTarget by remember { mutableStateOf<FDroidApp?>(null) }
    var installVariants by remember { mutableStateOf<List<AppVariant>>(emptyList()) }
    val installer = AppGraph.installer
    val installerTasks by installer.tasks.collectAsState(initial = emptyMap())

    val resolvedError = errorMessage?.asString()

    fun onShowInstallFrom(app: FDroidApp) {
        installFromTarget = app
        scope.launch(Dispatchers.IO) {
            val vars = AppGraph.db.appDao().variantsFor(app.packageName).sortedByDescending { it.versionCode }
            withContext(Dispatchers.Main) {
                installVariants = vars
            }
        }
    }

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
                    color = MaterialTheme.colorScheme.surface,
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
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
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

                        IconButton(
                            onClick = onSyncClick,
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_sync))
                            }
                        }

                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                containerColor = MaterialTheme.colorScheme.background,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.force_sync)) },
                                    onClick = {
                                        menuOpen = false
                                        onForceSyncClick()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.clear_all_apps)) },
                                    onClick = {
                                        menuOpen = false
                                        onClearAppsClick()
                                    },
                                    leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.nav_categories)) },
                                    onClick = {
                                        menuOpen = false
                                        onCategoriesClick(null)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) }
                                )
                            }
                        }
                    }
                }

                val status = syncStatusRes?.asString()
                if (isSyncing) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    if (!status.isNullOrBlank()) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        val Body: @Composable () -> Unit = {
            if (apps.itemCount == 0 && !isSyncing) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SearchOff, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_apps_found),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (query.isNotEmpty()) {
                        Text(
                            stringResource(R.string.try_different_search),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                if (s.useListLayout) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(apps.itemCount, key = { idx -> apps[idx]?.packageName ?: "placeholder_$idx" }) { idx ->
                            apps[idx]?.let { app ->
                                AppListRow(
                                    app = app,
                                    onClick = { onAppClick(app) },
                                    onLongClick = { onShowInstallFrom(app) }
                                )
                            }
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            count = apps.itemCount,
                            key = { idx -> apps[idx]?.packageName ?: "placeholder_$idx" }
                        ) { idx ->
                            apps[idx]?.let { app ->
                                val key = app.packageName
                                val focusRequester = focusRequesters.getOrPut(key) { FocusRequester() }

                                Box(
                                    modifier = Modifier
                                        .focusRequester(focusRequester)
                                        .onFocusChanged {
                                            if (it.isFocused) lastFocusedKey = key
                                        }
                                ) {
                                    AdaptiveAppCard(
                                        app = app,
                                        onClick = { onAppClick(app) },
                                        onLongClick = { onShowInstallFrom(app) }
                                    )
                                }

                                LaunchedEffect(key) {
                                    if (lastFocusedKey == key) {
                                        delay(50)
                                        try {
                                            focusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            // Ignore if focus request fails
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isTv) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Body()
            }
        }
        else {
            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                isRefreshing = isSyncing,
                onRefresh = onSyncClick
            ) {
                Body()
            }
        }
        installFromTarget?.let { target ->
            InstallFromDialog(
                app = target,
                variants = installVariants,
                installerTasks = installerTasks,
                onDismiss = { installFromTarget = null },
                onInstall = { v ->
                    scope.launch {
                        installer.install(v)
                    }
                },
                onCancelInstall = { pkg ->
                    installer.cancel(pkg)
                }
            )
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
private fun InstallFromDialog(
    app: FDroidApp,
    variants: List<AppVariant>,
    installerTasks: Map<String, TaskStage>,
    onDismiss: () -> Unit,
    onInstall: (AppVariant) -> Unit,
    onCancelInstall: (String) -> Unit
) {
    val stage = installerTasks[app.packageName]
    val isWorking = stage != null && stage !is TaskStage.Finished && stage !is TaskStage.Cancelled
    val progress = when (stage) {
        is TaskStage.Downloading -> stage.progress.coerceIn(0f, 0.99f)
        is TaskStage.Verifying -> 0.995f
        is TaskStage.Installing -> (0.99f + 0.01f * stage.progress).coerceIn(0.99f, 1f)
        else -> 0f
    }

    LaunchedEffect(stage) {
        if (stage is TaskStage.Finished && stage.success) onDismiss()
    }

    FlickyDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = stringResource(R.string.install_from),
        confirmButton = {
            TextButton(
                onClick = { if (!isWorking) onDismiss() },
                enabled = !isWorking,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) { Text(stringResource(R.string.action_close)) }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (variants.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_variants_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(variants.take(20), key = { "${it.repositoryUrl}_${it.versionCode}" }) { v ->
                        val compat = v.isCompatible
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusable()
                                .semantics { role = Role.Button }
                                .clickable(enabled = compat && !isWorking) { onInstall(v) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        v.repositoryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "v${v.versionName} (${v.versionCode}) • ${formatBytes(v.size)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!compat) {
                                        Text(
                                            text = stringResource(id = R.string.incompatible),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onInstall(v) },
                                    enabled = compat && !isWorking,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) { Text(stringResource(R.string.action_install)) }
                            }
                        }
                    }
                }
            }

            if (isWorking) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = when (stage) {
                            is TaskStage.Downloading -> stringResource(R.string.downloading)
                            is TaskStage.Verifying -> stringResource(R.string.verifying)
                            is TaskStage.Installing -> stringResource(R.string.installing)
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { onCancelInstall(app.packageName) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
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
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
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
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        optionText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
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
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        dividerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
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
                placeholder = {
                    Text(
                        stringResource(R.string.search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (localQuery.isNotEmpty()) {
                        IconButton(onClick = { localQuery = ""; onImmediateChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.action_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun AppListRow(app: FDroidApp, onClick: () -> Unit, onLongClick: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.name, app.iconUrl, size = 56.dp)
            Column(Modifier.weight(1f)) {
                val installedRepo = AppDependencies.installedRepo
                val installedVn = remember { mutableStateOf<String?>(null) }
                LaunchedEffect(app.packageName) {
                    installedVn.value = installedRepo.getVersionName(app.packageName)
                }
                val installedLabel = installedVn.value?.let { if (!it.startsWith("v")) "v$it" else it }
                AppTexts(
                    name = app.name,
                    installedLabel = installedLabel,
                    newLabel = app.version,
                    summary = app.summary
                )
            }
            Text(
                text = app.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}