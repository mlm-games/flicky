@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.flicky.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.flicky.R
import app.flicky.data.local.AppDao
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppSettings
import app.flicky.ui.components.SortDialog
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.SettingsRepository
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import org.koin.compose.koinInject
import app.flicky.ui.components.AppIcon
import app.flicky.ui.components.AppTexts
import app.flicky.ui.components.VoiceSearchButton
import app.flicky.ui.components.AdaptiveAppCard
import app.flicky.ui.components.global.FlickyDialog
import app.flicky.viewmodel.UiText
import app.flicky.helper.RequestFocusOnKey
import app.flicky.helper.TvFocusConfig
import app.flicky.helper.safeRequestFocus
import app.flicky.helper.tvFocusRing
import com.theonionsarewatching.yapchik.SoftkeyController
import com.theonionsarewatching.yapchik.Softkeys
import com.theonionsarewatching.yapchik.Yapchik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BrowseScreen(
    apps: LazyPagingItems<FDroidApp>,
    query: String,
    sort: SortOption,
    reverseSort: Boolean,
    onSortChange: (SortOption) -> Unit,
    onReverseSortChange: (Boolean) -> Unit,
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
    val appDao: AppDao = koinInject()
    val installer: Installer = koinInject()
    val installerTasks by installer.tasks.collectAsState(initial = emptyMap())
    val settings: SettingsRepository = koinInject()

    val widthDp = LocalConfiguration.current.screenWidthDp
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "sync_progress_anim")
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortDialog by remember { mutableStateOf(false) }
    val s by settings.settingsFlow.collectAsState(initial = AppSettings())

    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var tvSearchEditing by remember { mutableStateOf(false) }
    var tvSearchInteracted by rememberSaveable { mutableStateOf(false) }
    val sortChipFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    val clearSearch: () -> Unit = {
        tvSearchInteracted = true
        onSearchChange("")
        searchFocusRequester.safeRequestFocus()
    }

    val changeQuery: (String) -> Unit = { value ->
        if (isTv) tvSearchInteracted = true
        onSearchChange(value)
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val keypadSoftkeys = Yapchik.isActive
    val currentQuery = rememberUpdatedState(query)
    val softkeyController = remember { mutableStateOf<SoftkeyController?>(null) }

    DisposableEffect(activity, keypadSoftkeys) {
        val controller = if (keypadSoftkeys) activity?.let { Softkeys.of(it) } else null
        softkeyController.value = controller
        controller?.set {
            left(
                label = {
                    context.getString(
                        if (currentQuery.value.isBlank()) R.string.search
                        else R.string.action_clear
                    )
                }
            ) {
                if (currentQuery.value.isBlank()) {
                    searchFocusRequester.safeRequestFocus()
                } else {
                    clearSearch()
                }
            }
            right(context.getString(R.string.more_options)) { menuOpen = true }
        }
        onDispose {
            controller?.clear()
            softkeyController.value = null
        }
    }

    LaunchedEffect(query.isBlank()) {
        softkeyController.value?.invalidate()
    }

    if (isTv) {
        RequestFocusOnKey(
            key = apps.itemCount,
            focusRequester = sortChipFocusRequester,
            enabled = lastFocusedKey == null && !tvSearchEditing && !tvSearchInteracted && query.isEmpty() && apps.itemCount == 0,
            delayMs = TvFocusConfig.PRIMARY_FOCUS_DELAY
        )
    }

    val scope = rememberCoroutineScope()
    var installFromTarget by remember { mutableStateOf<FDroidApp?>(null) }
    var installVariants by remember { mutableStateOf<List<AppVariant>>(emptyList()) }

    val resolvedError = errorMessage?.asString()

    fun onShowInstallFrom(app: FDroidApp) {
        installFromTarget = app
        scope.launch(Dispatchers.IO) {
            val vars = appDao.variantsFor(app.packageName).sortedByDescending { it.versionCode }
            withContext(Dispatchers.Main) {
                installVariants = vars
            }
        }
    }

    BackHandler(
        enabled = isTv &&
            !tvSearchEditing &&
            query.isNotEmpty() &&
            !menuOpen &&
            !showSortDialog &&
            installFromTarget == null
    ) {
        clearSearch()
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
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FlickySearchBar(
                            query = query,
                            isTv = isTv,
                            onImmediateChange = changeQuery,
                            onCommit = changeQuery,
                            includeVoiceTrailing = !isTv,
                            modifier = Modifier.weight(1f),
                            focusRequester = searchFocusRequester,
                            onEditingChange = { editing ->
                                tvSearchEditing = editing
                                if (editing) tvSearchInteracted = true
                            }
                        )
                        if (isTv) {
                            IconButton(
                                onClick = clearSearch,
                                enabled = query.isNotEmpty(),
                                modifier = Modifier.tvFocusRing(shape = CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            VoiceSearchButton(onResult = changeQuery)
                        }
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = stringResource(R.string.sort_by),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    if (reverseSort) {
                                        Icon(
                                            Icons.Default.MoveDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .focusRequester(sortChipFocusRequester)
                                .tvFocusRing()
                        )

                        Spacer(Modifier.weight(1f))

                        IconButton(
                            onClick = onSyncClick,
                            enabled = !isSyncing,
                            modifier = Modifier.tvFocusRing(shape = CircleShape)
                        ) {
                            if (isSyncing) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_sync))
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.tvFocusRing(shape = CircleShape)
                            ) {
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
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
//                            .height(4.dp),
//                        color = MaterialTheme.colorScheme.primary,
//                        trackColor = MaterialTheme.colorScheme.surfaceVariant
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
                                val key = app.packageName
                                AppListRow(
                                    app = app,
                                    autofocus = isTv && !tvSearchEditing && (
                                        lastFocusedKey == key ||
                                            (lastFocusedKey == null && !tvSearchInteracted && query.isEmpty() && idx == 0)
                                        ),
                                    onClick = {
                                        lastFocusedKey = key
                                        onAppClick(app)
                                    },
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
                                AdaptiveAppCard(
                                    app = app,
                                    autofocus = isTv && !tvSearchEditing && (
                                        lastFocusedKey == key ||
                                            (lastFocusedKey == null && !tvSearchInteracted && query.isEmpty() && idx == 0)
                                        ),
                                    onClick = {
                                        lastFocusedKey = key
                                        onAppClick(app)
                                    },
                                    onLongClick = { onShowInstallFrom(app) }
                                )
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
        } else {
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
            reverseSort = reverseSort,
            onSortSelected = {
                onSortChange(it)
                showSortDialog = false
            },
            onReverseSortChange = onReverseSortChange,
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
        is TaskStage.Queued -> 0.995f
        is TaskStage.Installing -> (0.99f + 0.01f * stage.progress).coerceIn(0.99f, 1f)
        is TaskStage.NeedsConfirmation -> 1f
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
                            color = MaterialTheme.colorScheme.surfaceContainerLow
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
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
//                    color = MaterialTheme.colorScheme.primary,
//                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = when (stage) {
                            is TaskStage.Downloading -> stringResource(R.string.downloading)
                            is TaskStage.Verifying -> stringResource(R.string.verifying)
                            is TaskStage.Queued -> stringResource(R.string.waiting_queue)
                            is TaskStage.Installing -> stringResource(R.string.installing)
                            is TaskStage.NeedsConfirmation -> stringResource(R.string.waiting_confirmation)
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
fun UiText.asString(): String {
    return when (this) {
        is UiText.StringResource -> stringResource(this.resId, *this.args.toTypedArray())
    }
}

@Composable
private fun FlickySearchBar(
    query: String,
    isTv: Boolean,
    onImmediateChange: (String) -> Unit,
    onCommit: (String) -> Unit,
    includeVoiceTrailing: Boolean = true,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onEditingChange: (Boolean) -> Unit = {},
) {
    var localQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { if (query != localQuery) localQuery = query }

    val keyboard = LocalSoftwareKeyboardController.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    if (isTv) {
        TvSearchBar(
            query = localQuery,
            onQueryChange = {
                localQuery = it
                onImmediateChange(it)
            },
            onSearch = { onCommit(localQuery) },
            modifier = modifier,
            focusRequester = focusRequester,
            colors = fieldColors,
            onEditingChange = onEditingChange
        )
        return
    }

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
                    keyboard?.hide()
                },
                expanded = false,
                onExpandedChange = {},
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
                    when {
                        localQuery.isNotEmpty() -> {
                            IconButton(
                                onClick = {
                                    localQuery = ""
                                    onImmediateChange("")
                                }
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        includeVoiceTrailing -> {
                            VoiceSearchButton(
                                onResult = {
                                    localQuery = it
                                    onImmediateChange(it)
                                }
                            )
                        }
                    }
                },
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    )
            )
        },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier.fillMaxWidth(),
        shape = SearchBarDefaults.dockedShape,
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            dividerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        tonalElevation = SearchBarDefaults.TonalElevation,
        shadowElevation = SearchBarDefaults.ShadowElevation,
        content = { }
    )
}

@Composable
private fun TvSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    colors: TextFieldColors,
    onEditingChange: (Boolean) -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var barFocused by remember { mutableStateOf(false) }
    var editorWasFocused by remember { mutableStateOf(false) }
    var restoreFocusAfterEdit by remember { mutableStateOf(false) }
    var imeWasVisibleDuringEdit by remember { mutableStateOf(false) }

    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    val windowFocused = windowInfo.isWindowFocused
    val internalFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = focusRequester ?: internalFocusRequester
    val imeVisible = WindowInsets.isImeVisible
    val shape = SearchBarDefaults.dockedShape

    fun stopEditing(restoreFocus: Boolean) {
        if (!editing) return
        editing = false
        restoreFocusAfterEdit = restoreFocus
        imeWasVisibleDuringEdit = false
        onEditingChange(false)
        keyboard?.hide()
    }

    fun startEditing() {
        if (editing) return
        editorWasFocused = false
        restoreFocusAfterEdit = false
        imeWasVisibleDuringEdit = false
        editing = true
        onEditingChange(true)
    }

    BackHandler(enabled = editing) {
        stopEditing(restoreFocus = true)
    }

    LaunchedEffect(editing, windowFocused) {
        if (editing && windowFocused) {
            withFrameNanos { }
            if (fieldFocusRequester.safeRequestFocus()) {
                keyboard?.show()
            }
        } else if (!editing && restoreFocusAfterEdit) {
            withFrameNanos { }
            fieldFocusRequester.safeRequestFocus()
            restoreFocusAfterEdit = false
        }
    }

    LaunchedEffect(editing, imeVisible) {
        if (!editing) {
            imeWasVisibleDuringEdit = false
        } else if (imeVisible) {
            imeWasVisibleDuringEdit = true
        } else if (imeWasVisibleDuringEdit && windowInfo.isWindowFocused) {
            stopEditing(restoreFocus = true)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onEditingChange(false) }
    }

    if (editing) {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = {
                onSearch()
                stopEditing(restoreFocus = true)
            },
            expanded = false,
            onExpandedChange = {},
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
            colors = colors,
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(fieldFocusRequester)
                .tvFocusRing(shape = shape)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        editorWasFocused = true
                    } else if (editing && editorWasFocused && windowInfo.isWindowFocused) {
                        stopEditing(restoreFocus = false)
                    }
                }
        )
    } else {
        Surface(
            onClick = { startEditing() },
            modifier = modifier
                .fillMaxWidth()
                .height(SearchBarDefaults.InputFieldHeight)
                .focusRequester(fieldFocusRequester)
                .onFocusChanged { barFocused = it.hasFocus }
                .tvFocusRing(shape = shape),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (barFocused) 0.5f else 0.3f
            ),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = SearchBarDefaults.TonalElevation,
            shadowElevation = SearchBarDefaults.ShadowElevation
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(16.dp))
                Text(
                    text = query.ifEmpty { stringResource(R.string.search_hint) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (query.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AppListRow(
    app: FDroidApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    autofocus: Boolean = false
) {
    val installedRepo: InstalledAppsRepository = koinInject()
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    RequestFocusOnKey(
        key = app.packageName,
        focusRequester = focusRequester,
        enabled = autofocus,
        delayMs = TvFocusConfig.PRIMARY_FOCUS_DELAY
    )

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.name, app.iconUrl, size = 56.dp)
            Column(Modifier.weight(1f)) {
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

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
