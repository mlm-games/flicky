@file:OptIn(ExperimentalMaterial3Api::class)

package app.flicky.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flicky.R
import app.flicky.data.local.RepoConfig
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.remote.MirrorRegistry
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.AppSettingsSchema
import app.flicky.di.AppDependencies
import app.flicky.ui.components.global.ConfirmationDialog
import app.flicky.ui.components.global.DropdownSettingDialog
import app.flicky.ui.components.global.FlickyDialog
import app.flicky.ui.components.global.InputDialog
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.ui.components.global.AnimationConfig
import app.flicky.ui.components.global.SettingsAction
import app.flicky.ui.components.global.SettingsItem
import app.flicky.ui.components.global.SettingsSection
import app.flicky.ui.components.global.SettingsToggle
import app.flicky.ui.components.global.SliderSettingDialog
import app.flicky.ui.components.snackbar.SnackbarManager
import app.flicky.viewmodel.SettingsViewModel
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.annotations.CategoryDefinition
import io.github.mlmgames.settings.core.backup.ExportResult
import io.github.mlmgames.settings.core.backup.ImportResult
import io.github.mlmgames.settings.core.types.Button
import io.github.mlmgames.settings.core.types.Dropdown
import io.github.mlmgames.settings.core.types.Slider
import io.github.mlmgames.settings.core.types.TextInput
import io.github.mlmgames.settings.core.types.Toggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.reflect.KClass

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val repos by vm.repositories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val schema = remember { AppSettingsSchema }
    val snackbarManager: SnackbarManager = koinInject()

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    // Dialog states
    var showDropdown by remember { mutableStateOf(false) }
    var showSlider by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    var currentField by remember { mutableStateOf<SettingField<AppSettings, *>?>(null) }

    var showResetConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<RepositoryInfo?>(null) }
    var showAddRepo by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val cfg = LocalConfiguration.current

    val repoConfigs by produceState(
        initialValue = emptyMap(),
        key1 = repos
    ) {
        value = withContext(Dispatchers.IO) {
            val dao = AppDependencies.db.repoConfigDao()
            repos.associate { repo ->
                val base = repo.url.trimEnd('/')
                base to (dao.get(base) ?: RepoConfig(baseUrl = base, enabled = repo.enabled))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(fileUri)?.use { stream ->
                            stream.bufferedReader().readText()
                        }
                    }
                    if (json != null) {
                        val result = vm.importSettings(json)
                        when (result) {
                            is ImportResult.Success -> {
                                snackbarManager.show(
                                    context.getString(R.string.import_success, result.appliedCount)
                                )
                            }
                            is ImportResult.Error -> {
                                snackbarManager.show(result.message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    snackbarManager.show(context.getString(R.string.import_failed, e.message))
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                try {
                    val result = vm.exportSettings()
                    when (result) {
                        is ExportResult.Success -> {
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(fileUri)?.use { stream ->
                                    stream.bufferedWriter().use { writer ->
                                        writer.write(result.json)
                                    }
                                }
                            }
                            snackbarManager.show(context.getString(R.string.export_success))
                        }
                        is ExportResult.Error -> {
                            snackbarManager.show(result.message)
                        }
                    }
                } catch (e: Exception) {
                    snackbarManager.show(context.getString(R.string.export_failed, e.message))
                }
            }
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SettingsViewModel.UiEvent.Toast -> {
                    snackbarManager.show(context.getString(event.messageResId))
                }
                is SettingsViewModel.UiEvent.RequestExport -> {
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    exportLauncher.launch("flicky_settings_$timestamp.json")
                }
                is SettingsViewModel.UiEvent.OpenUrl -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                    context.startActivity(intent)
                }
            }
        }
    }

    fun categoryTitle(cat: KClass<*>): String {
        val annotation = cat.java.getAnnotation(CategoryDefinition::class.java)
        if (annotation != null && annotation.titleRes != 0) {
            return context.getString(annotation.titleRes)
        }
        return cat.simpleName ?: "Settings"
    }

    fun filterFields(fields: List<SettingField<AppSettings, *>>): List<SettingField<AppSettings, *>> {
        if (searchQuery.isBlank()) return fields
        val query = searchQuery.lowercase()
        return fields.filter { field ->
            val meta = field.meta ?: return@filter false
            meta.title.lowercase().contains(query) ||
                    meta.description.lowercase().contains(query) ||
                    field.name.lowercase().contains(query)
        }
    }

    MyScreenScaffold(
        title = if (isSearchActive) "" else stringResource(R.string.nav_settings),
        actions = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(AnimationConfig.STANDARD)) +
                            expandHorizontally(
                                expandFrom = Alignment.End,
                                animationSpec = tween(AnimationConfig.STANDARD)
                            )) togetherWith
                            (fadeOut(animationSpec = tween(AnimationConfig.QUICK)) +
                                    shrinkHorizontally(
                                        shrinkTowards = Alignment.End,
                                        animationSpec = tween(AnimationConfig.STANDARD)
                                    ))
                },
                label = "settings_search_bar_anim"
            ) { searchActive ->
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.search_settings),
                                style = typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                } else {
                                    isSearchActive = false
                                }
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        singleLine = true,
                        textStyle = typography.bodyMedium,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }

                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                containerColor = colorScheme.background,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_settings)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        importLauncher.launch(arrayOf("application/json"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_settings)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Upload, contentDescription = null)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                                        exportLauncher.launch("flicky_settings_$timestamp.json")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Settings cards by category (schema-driven)
            val categories = schema.orderedCategories()
            val grouped = schema.groupedByCategory()

            items(
                items = categories,
                key = { it.qualifiedName ?: it.simpleName ?: "cat" }
            ) { cat ->
                val allFields = grouped[cat].orEmpty()
                val fields = filterFields(allFields)

                AnimatedVisibility(visible = fields.isNotEmpty()) {
                    SettingsSection(title = categoryTitle(cat)) {
                        fields.forEach { field ->
                            val meta = field.meta ?: return@forEach
                            val enabled = schema.isEnabled(settings, field)

                            when (meta.type) {
                                Toggle::class -> {
                                    val v = (field.get(settings) as? Boolean) ?: false
                                    SettingsToggle(
                                        title = meta.title,
                                        description = meta.description.takeIf { it.isNotBlank() },
                                        isChecked = v,
                                        enabled = enabled,
                                        onCheckedChange = { vm.updateSetting(field.name, it) }
                                    )
                                }

                                Dropdown::class -> {
                                    val idx = (field.get(settings) as? Int) ?: 0
                                    val options = meta.options
                                    SettingsItem(
                                        title = meta.title,
                                        subtitle = options.getOrNull(idx) ?: stringResource(R.string.unknown),
                                        description = meta.description.takeIf { it.isNotBlank() },
                                        enabled = enabled,
                                        onClick = {
                                            currentField = field
                                            showDropdown = true
                                        }
                                    )
                                }

                                Slider::class -> {
                                    val subtitle = when (val v = field.get(settings)) {
                                        is Int -> v.toString()
                                        is Float -> String.format(Locale.getDefault(), "%.1f", v)
                                        is Double -> String.format(Locale.getDefault(), "%.1f", v)
                                        is Long -> v.toString()
                                        else -> ""
                                    }
                                    SettingsItem(
                                        title = meta.title,
                                        subtitle = subtitle,
                                        description = meta.description.takeIf { it.isNotBlank() },
                                        enabled = enabled,
                                        onClick = {
                                            currentField = field
                                            showSlider = true
                                        }
                                    )
                                }

                                TextInput::class -> {
                                    val cur = (field.get(settings) as? String).orEmpty()
                                    SettingsItem(
                                        title = meta.title,
                                        subtitle = cur.ifBlank { stringResource(R.string.optional) },
                                        description = meta.description.takeIf { it.isNotBlank() },
                                        enabled = enabled,
                                        onClick = {
                                            currentField = field
                                            showTextInput = true
                                        }
                                    )
                                }

                                Button::class -> {
                                    SettingsAction(
                                        title = meta.title,
                                        description = meta.description.takeIf { it.isNotBlank() },
                                        buttonText = stringResource(R.string.run),
                                        enabled = enabled,
                                        onClick = {
                                            vm.performAction(field.name)
                                            val current = field.get(settings)
                                            if (current is Long) {
                                                vm.updateSetting(field.name, System.currentTimeMillis())
                                            }
                                        }
                                    )
                                }

                                else -> {
                                    SettingsItem(
                                        title = meta.title,
                                        subtitle = stringResource(R.string.unknown),
                                        description = meta.description.takeIf { it.isNotBlank() },
                                        enabled = false,
                                        onClick = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Only shows if not searching / if "repo" matches
            val showRepos = searchQuery.isBlank() ||
                    "repository".contains(searchQuery.lowercase()) ||
                    "repo".contains(searchQuery.lowercase())

            if (showRepos) {
                item(key = "repos_header") {
                    Text(
                        text = stringResource(R.string.repositories),
                        style = typography.titleMedium,
                        color = colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Repository cards
                items(repos, key = { it.url }) { repo ->
                    val base = repo.url.trimEnd('/')
                    val config = repoConfigs[base] ?: RepoConfig(baseUrl = base, enabled = repo.enabled)

                    RepoConfigCard(
                        repo = repo,
                        config = config,
                        onConfigChange = { newCfg ->
                            scope.launch {
                                AppDependencies.db.repoConfigDao().upsert(newCfg)
                                vm.reloadConfigs()
                            }
                        },
                        onToggle = { vm.toggleRepository(repo.url) },
                        onTestMirrors = {
                            scope.launch {
                                val results = vm.testRepoMirrors(base)
                                val message = buildString {
                                    results.forEach { (url, ok, code, ms) ->
                                        append(if (ok) "✓" else "✗")
                                        append(" ").append(url).append("\n")
                                        append(" ")
                                        append(if (ok) "${ms}ms (HTTP $code)" else "HTTP $code / fail")
                                        append("\n")
                                    }
                                }
                                snackbarManager.show(message)
                            }
                        },
                        onForgetMirror = {
                            MirrorRegistry.clear(base)
                            snackbarManager.show(context.getString(R.string.forgot_mirror, repo.name))
                        },
                        onDelete = {
                            repoToDelete = repo
                            showDeleteConfirm = true
                        }
                    )
                }

                item(key = "repo_actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showAddRepo = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.add_repository))
                        }

                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.reset_to_defaults))
                        }
                    }
                }
            }
        }
    }

    if (showDropdown) {
        val field = currentField
        val meta = field?.meta
        if (field != null && meta != null) {
            val idx = (field.get(settings) as? Int) ?: 0
            DropdownSettingDialog(
                title = meta.title,
                options = meta.options,
                selectedIndex = idx,
                onDismiss = { showDropdown = false },
                onOptionSelected = { i ->
                    vm.updateSetting(field.name, i)
                    showDropdown = false
                }
            )
        }
    }

    if (showSlider) {
        val field = currentField
        val meta = field?.meta
        if (field != null && meta != null) {
            val cur = when (val v = field.get(settings)) {
                is Int -> v.toFloat()
                is Float -> v
                is Double -> v.toFloat()
                is Long -> v.toFloat()
                else -> 0f
            }

            SliderSettingDialog(
                title = meta.title,
                currentValue = cur,
                min = meta.min,
                max = meta.max,
                step = meta.step,
                onDismiss = { showSlider = false },
                onValueSelected = { value ->
                    when (field.get(settings)) {
                        is Int -> vm.updateSetting(field.name, value.toInt())
                        is Float -> vm.updateSetting(field.name, value)
                        is Double -> vm.updateSetting(field.name, value.toDouble())
                        is Long -> vm.updateSetting(field.name, value.toLong())
                    }
                    showSlider = false
                }
            )
        }
    }

    if (showTextInput) {
        val field = currentField
        val meta = field?.meta
        if (field != null && meta != null) {
            val current = (field.get(settings) as? String).orEmpty()

            InputDialog(
                title = meta.title,
                label = meta.title,
                value = current,
                placeholder = "",
                confirmText = stringResource(R.string.action_confirm),
                dismissText = stringResource(R.string.action_cancel),
                onConfirm = { newValue ->
                    vm.updateSetting(field.name, newValue)
                    showTextInput = false
                },
                onDismiss = { showTextInput = false },
                validator = { true }
            )
        }
    }

    if (showAddRepo) {
        AddRepoDialog(
            onDismiss = { showAddRepo = false },
            onAdd = { name, url, trustMode ->
                scope.launch {
                    vm.addRepository(name, url, trustMode)
                    AppDependencies.mirrorPolicyProvider.ensureDefault(url.trimEnd('/'))
                }
                showAddRepo = false
            }
        )
    }

    if (showResetConfirm) {
        ConfirmationDialog(
            title = stringResource(R.string.reset_repositories),
            message = stringResource(R.string.reset_repositories_confirm),
            confirmText = stringResource(R.string.reset),
            dismissText = stringResource(R.string.action_cancel),
            isDangerous = true,
            onConfirm = {
                showResetConfirm = false
                vm.resetRepositoriesToDefaults()
            },
            onDismiss = { showResetConfirm = false }
        )
    }

    if (showDeleteConfirm && repoToDelete != null) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_repository),
            message = stringResource(R.string.delete_repository_confirm, repoToDelete!!.name),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.action_cancel),
            isDangerous = true,
            onConfirm = {
                repoToDelete?.let { vm.deleteRepository(it.url) }
                showDeleteConfirm = false
                repoToDelete = null
            },
            onDismiss = {
                showDeleteConfirm = false
                repoToDelete = null
            }
        )
    }
}

@Composable
private fun RepoConfigCard(
    repo: RepositoryInfo,
    config: RepoConfig,
    onConfigChange: (RepoConfig) -> Unit,
    onToggle: () -> Unit,
    onTestMirrors: () -> Unit,
    onForgetMirror: () -> Unit,
    onDelete: () -> Unit
) {
    var localConfig by remember(config) { mutableStateOf(config) }
    var showMenu by remember { mutableStateOf(false) }
    var openStrategy by remember { mutableStateOf(false) }
    var openTrust by remember { mutableStateOf(false) }

    val strategyFocusRequester = remember { FocusRequester() }
    val trustFocusRequester = remember { FocusRequester() }

    val updateConfig: (RepoConfig) -> Unit = { newConfig ->
        localConfig = newConfig
        onConfigChange(newConfig)
    }

    Surface(
        color = colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header with name and switch
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(repo.name, style = typography.bodyLarge)
                    Text(
                        repo.url,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options_for, repo.name),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            containerColor = colorScheme.background,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.test_ping)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onTestMirrors()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.forget_last_mirror)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onForgetMirror()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.delete_repository),
                                        color = colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Switch(
                        checked = repo.enabled,
                        onCheckedChange = { onToggle() }
                    )
                }
            }

            if (repo.enabled) {
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = localConfig.rotateMirrors,
                        onClick = {
                            updateConfig(localConfig.copy(rotateMirrors = !localConfig.rotateMirrors))
                        },
                        label = { Text(stringResource(R.string.rotate_mirrors)) }
                    )

                    FilterChip(
                        selected = localConfig.includeOnion,
                        onClick = {
                            updateConfig(localConfig.copy(includeOnion = !localConfig.includeOnion))
                        },
                        label = { Text(stringResource(R.string.use_onion)) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Mirror strategy dropdown
                val strategies = listOf("StickyLastGood", "RoundRobin", "CanonicalFirst")
                val strategyIdx = strategies.indexOf(localConfig.strategy).coerceAtLeast(0)

                ExposedDropdownMenuBox(
                    expanded = openStrategy,
                    onExpandedChange = { openStrategy = it },
                ) {
                    OutlinedTextField(
                        value = strategies[strategyIdx],
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.mirror_strategy)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openStrategy) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .focusRequester(strategyFocusRequester)
                    )

                    ExposedDropdownMenu(
                        expanded = openStrategy,
                        containerColor = colorScheme.background,
                        onDismissRequest = {
                            openStrategy = false
                            strategyFocusRequester.requestFocus()
                        },
                    ) {
                        strategies.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    openStrategy = false
                                    updateConfig(localConfig.copy(strategy = s))
                                    strategyFocusRequester.requestFocus()
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Trust options
                Text(
                    stringResource(R.string.trust),
                    style = typography.labelLarge
                )

                Spacer(Modifier.height(6.dp))

                val trustModes = listOf("HttpsOnly", "Pinned", "CustomCA", "InsecureHttp")
                val trustIdx = trustModes.indexOf(localConfig.trustMode).coerceAtLeast(0)

                ExposedDropdownMenuBox(
                    expanded = openTrust,
                    onExpandedChange = { openTrust = !openTrust },
                ) {
                    OutlinedTextField(
                        value = trustModes[trustIdx],
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.trust_mode)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = openTrust)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .focusRequester(trustFocusRequester)
                    )

                    ExposedDropdownMenu(
                        expanded = openTrust,
                        containerColor = colorScheme.background,
                        onDismissRequest = {
                            openTrust = false
                            trustFocusRequester.requestFocus()
                        }
                    ) {
                        trustModes.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    openTrust = false
                                    updateConfig(config.copy(trustMode = s))
                                    trustFocusRequester.requestFocus()
                                }
                            )
                        }
                    }
                }

                // Additional fields based on trust mode
                when (config.trustMode) {
                    "Pinned" -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = config.pins,
                            onValueChange = {
                                onConfigChange(config.copy(pins = it))
                            },
                            label = { Text(stringResource(R.string.pins_label)) },
                            placeholder = { Text(stringResource(R.string.pins_placeholder)) },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    "CustomCA" -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = config.caPem,
                            onValueChange = {
                                onConfigChange(config.copy(caPem = it))
                            },
                            label = { Text(stringResource(R.string.custom_ca_pem)) },
                            singleLine = false,
                            minLines = 4,
                            maxLines = 12,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRepoDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var allowInsecureHttp by remember { mutableStateOf(false) }

    val normalizedUrl = url.trim()
    val isHttpRepo = normalizedUrl.startsWith("http://", ignoreCase = true)
    val canAdd = normalizedUrl.isNotBlank() && (!isHttpRepo || allowInsecureHttp)

    FlickyDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.add_repository),
        confirmButton = {
            TextButton(
                onClick = {
                    if (canAdd) {
                        val trustMode = if (allowInsecureHttp) "InsecureHttp" else "HttpsOnly"
                        onAdd(name.ifBlank { url }, normalizedUrl, trustMode)
                    }
                },
                enabled = canAdd,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.primary,
                    disabledContentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    ) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                placeholder = { Text(stringResource(R.string.optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.url)) },
                placeholder = { Text("https://example.com/fdroid/repo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (isHttpRepo) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.insecure_http_warning),
                    style = typography.bodySmall,
                    color = colorScheme.error
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = allowInsecureHttp,
                        onCheckedChange = { allowInsecureHttp = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.allow_insecure_http),
                        style = typography.bodyMedium
                    )
                }
            }
        }
    }
}
