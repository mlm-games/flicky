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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flicky.R
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.AppSettingsSchema
import app.flicky.ui.components.global.DropdownSettingDialog
import app.flicky.ui.components.global.InputDialog
import app.flicky.ui.components.global.MyScreenScaffold
import app.flicky.ui.components.global.AnimationConfig
import app.flicky.ui.components.global.SettingsAction
import app.flicky.ui.components.global.SettingsItem
import app.flicky.ui.components.global.SettingsSection
import app.flicky.ui.components.global.SettingsToggle
import app.flicky.ui.components.global.SliderSettingDialog
import app.flicky.ui.components.snackbar.SnackbarManager
import app.flicky.helper.tvFocusRing
import app.flicky.viewmodel.SettingsViewModel
import com.theonionsarewatching.yapchik.AutoDetect
import com.theonionsarewatching.yapchik.SoftkeyMode
import com.theonionsarewatching.yapchik.Yapchik
import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.annotations.CategoryDefinition
import io.github.mlmgames.settings.core.backup.ExportResult
import io.github.mlmgames.settings.core.backup.ImportResult
import io.github.mlmgames.settings.core.resources.StringResourceProvider
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
@Suppress("NonObservableLocale")
fun SettingsScreen(
    vm: SettingsViewModel,
    onRepositoriesClick: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val stringProvider: StringResourceProvider = koinInject()

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

    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val keypadDevice = remember(context) { AutoDetect.deviceLooksLikeKeypad(context) }
    var softkeyMode by remember { mutableStateOf(Yapchik.mode) }
    var showSoftkeyMode by remember { mutableStateOf(false) }

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
            meta.resolvedTitle(stringProvider).lowercase().contains(query) ||
                    meta.resolvedDescription(stringProvider).lowercase().contains(query) ||
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
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.tvFocusRing(shape = CircleShape)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }

                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.tvFocusRing(shape = CircleShape)
                            ) {
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
                                        title = meta.resolvedTitle(stringProvider),
                                        description = meta.resolvedDescription(stringProvider)
                                            .takeIf { it.isNotBlank() },
                                        isChecked = v,
                                        enabled = enabled,
                                        onCheckedChange = { vm.updateSetting(field.name, it) }
                                    )
                                }

                                Dropdown::class -> {
                                    val idx = field.toUiDropdownIndex(settings)
                                        ?: (field.get(settings) as? Int) ?: 0
                                    val options = meta.dropdownLabels(field, stringProvider)
                                    SettingsItem(
                                        title = meta.resolvedTitle(stringProvider),
                                        subtitle = options.getOrNull(idx) ?: stringResource(R.string.unknown),
                                        description = meta.resolvedDescription(stringProvider)
                                            .takeIf { it.isNotBlank() },
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
                                        title = meta.resolvedTitle(stringProvider),
                                        subtitle = subtitle,
                                        description = meta.resolvedDescription(stringProvider)
                                            .takeIf { it.isNotBlank() },
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
                                        title = meta.resolvedTitle(stringProvider),
                                        subtitle = cur.ifBlank { stringResource(R.string.optional) },
                                        description = meta.resolvedDescription(stringProvider)
                                            .takeIf { it.isNotBlank() },
                                        enabled = enabled,
                                        onClick = {
                                            currentField = field
                                            showTextInput = true
                                        }
                                    )
                                }

                                Button::class -> {
                                    SettingsAction(
                                        title = meta.resolvedTitle(stringProvider),
                                        description = meta.resolvedDescription(stringProvider)
                                            .takeIf { it.isNotBlank() },
                                        buttonText = stringResource(R.string.run),
                                        enabled = enabled,
                                        onClick = {
                                            val actionClass = meta.actionClass
                                            if (actionClass != null) {
                                                vm.emitActionFromScreen(actionClass)
                                            }
                                        }
                                    )
                                }

                                else -> {
                                    SettingsItem(
                                        title = meta.resolvedTitle(stringProvider),
                                        subtitle = stringResource(R.string.unknown),
                                        description = meta.resolvedDescription(stringProvider)
                                            .takeIf { it.isNotBlank() },
                                        enabled = false,
                                        onClick = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Only shows if not searching / if "softkey" matches
            val showSoftkeys = keypadDevice &&
                    (searchQuery.isBlank() || "softkey".contains(searchQuery.lowercase()))

            if (showSoftkeys) {
                item(key = "softkeys") {
                    SettingsSection(title = stringResource(R.string.softkeys)) {
                        SettingsItem(
                            title = stringResource(R.string.softkey_bar),
                            subtitle = stringResource(
                                when (softkeyMode) {
                                    SoftkeyMode.ON -> R.string.softkey_mode_on
                                    SoftkeyMode.OFF -> R.string.softkey_mode_off
                                    SoftkeyMode.AUTO -> R.string.softkey_mode_auto
                                }
                            ),
                            description = stringResource(R.string.softkey_bar_desc),
                            onClick = { showSoftkeyMode = true }
                        )
                    }
                }
            }

            // Only shows if not searching / if "repo" matches
            val showRepos = searchQuery.isBlank() ||
                    "repository".contains(searchQuery.lowercase()) ||
                    "repo".contains(searchQuery.lowercase())

            if (showRepos) {
                item(key = "repositories") {
                    SettingsSection(title = stringResource(R.string.repositories)) {
                        SettingsItem(
                            title = stringResource(R.string.manage_repositories),
                            description = stringResource(R.string.manage_repositories_desc),
                            onClick = onRepositoriesClick
                        )
                    }
                }
            }
        }
    }

    if (showDropdown) {
        val field = currentField
        val meta = field?.meta
        if (field != null && meta != null) {
            val idx = field.toUiDropdownIndex(settings)
                ?: (field.get(settings) as? Int) ?: 0
            DropdownSettingDialog(
                title = meta.resolvedTitle(stringProvider),
                options = meta.dropdownLabels(field, stringProvider),
                selectedIndex = idx,
                onDismiss = { showDropdown = false },
                onOptionSelected = { i ->
                    val current = field.get(settings)
                    if (current is Int) {
                        vm.updateSetting(field.name, i)
                    } else {
                        field.fromUiDropdownIndex(i)?.let { vm.updateSetting(field.name, it) }
                    }
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
                title = meta.resolvedTitle(stringProvider),
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
                title = meta.resolvedTitle(stringProvider),
                label = meta.resolvedTitle(stringProvider),
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

    if (showSoftkeyMode) {
        DropdownSettingDialog(
            title = stringResource(R.string.softkeys),
            options = listOf(
                stringResource(R.string.softkey_mode_on),
                stringResource(R.string.softkey_mode_off),
                stringResource(R.string.softkey_mode_auto)
            ),
            selectedIndex = SoftkeyMode.values().indexOf(softkeyMode),
            onDismiss = { showSoftkeyMode = false },
            onOptionSelected = { index ->
                val mode = SoftkeyMode.values()[index]
                softkeyMode = mode
                Yapchik.mode = mode
                showSoftkeyMode = false
            }
        )
    }
}
