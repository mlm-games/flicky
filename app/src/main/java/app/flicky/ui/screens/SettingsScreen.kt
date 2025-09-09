package app.flicky.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.data.local.RepoConfig
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.Setting
import app.flicky.data.repository.SettingCategory
import app.flicky.data.repository.SettingType
import app.flicky.data.repository.SettingsManager
import app.flicky.ui.components.MyScreenScaffold
import app.flicky.ui.components.SettingsAction
import app.flicky.ui.components.SettingsItem
import app.flicky.ui.components.SettingsToggle
import app.flicky.ui.dialogs.ConfirmationDialog
import app.flicky.ui.dialogs.DropdownSettingDialog
import app.flicky.ui.dialogs.SliderSettingDialog
import app.flicky.viewmodel.SettingsViewModel
import kotlin.reflect.KProperty1
import app.flicky.R
import app.flicky.ui.dialogs.FlickyDialog
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    val repos by vm.repositories.collectAsState()
    val manager = remember { SettingsManager() }
    val scope = rememberCoroutineScope()

    var showDropdown by remember { mutableStateOf(false) }
    var showSlider by remember { mutableStateOf(false) }
    var currentProp by remember { mutableStateOf<KProperty1<AppSettings, *>?>(null) }
    var currentAnn by remember { mutableStateOf<Setting?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val grouped = remember { manager.getByCategory() }
    val cfg = LocalConfiguration.current
    val gridCells = remember(cfg.screenWidthDp) { GridCells.Adaptive(minSize = 420.dp) }

    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SettingsViewModel.UiEvent.Toast -> {
                    Toast.makeText(
                        context,
                        context.getString(event.messageResId),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is SettingsViewModel.UiEvent.RequestExport -> {
                    // TODO
                }
            }
        }
    }

    MyScreenScaffold(title = "Settings") {
        LazyVerticalGrid(
            columns = gridCells,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Settings by category
            for (category in SettingCategory.entries) {
                val itemsForCat = grouped[category] ?: emptyList()
                if (itemsForCat.isEmpty()) continue

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = category.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = typography.titleMedium,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                    )
                }

                items(itemsForCat, key = { it.first.name }) { (prop, ann) ->
                    val enabled = manager.isEnabled(settings, prop, ann)

                    when (ann.type) {
                        SettingType.TOGGLE -> {
                            val value = prop.get(settings) as? Boolean ?: false
                            SettingsToggle(
                                title = ann.title,
                                description = ann.description.takeIf { it.isNotBlank() },
                                isChecked = value,
                                enabled = enabled,
                                onCheckedChange = { vm.updateSetting(prop.name, it) }
                            )
                        }

                        SettingType.DROPDOWN -> {
                            val idx = prop.get(settings) as? Int ?: 0
                            val options = ann.options.toList()
                            SettingsItem(
                                title = ann.title,
                                subtitle = options.getOrNull(idx) ?: "Unknown",
                                description = ann.description.takeIf { it.isNotBlank() },
                                enabled = enabled
                            ) {
                                currentProp = prop
                                currentAnn = ann
                                showDropdown = true
                            }
                        }

                        SettingType.SLIDER -> {
                            val valueText = when (val v = prop.get(settings)) {
                                is Int -> v.toString()
                                is Float -> String.format(Locale.getDefault() , "%.1f", v)
                                else -> ""
                            }
                            SettingsItem(
                                title = ann.title,
                                subtitle = valueText,
                                description = ann.description.takeIf { it.isNotBlank() },
                                enabled = enabled
                            ) {
                                currentProp = prop
                                currentAnn = ann
                                showSlider = true
                            }
                        }

                        SettingType.BUTTON -> {
                            SettingsAction(
                                title = ann.title,
                                description = ann.description.takeIf { it.isNotBlank() },
                                buttonText = "Run",
                                enabled = enabled,
                                onClick = { vm.performAction(prop.name) }
                            )
                        }
                    }
                }
            }

            // Repositories header
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Repositories",
                    style = typography.titleMedium,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                )
            }

            // Repositories list with per-repo mirror/trust controls (unchanged from earlier step)
            items(repos, key = { it.url }) { r ->
                val base = r.url.trimEnd('/')

                // Ensure a default config exists
                LaunchedEffect(base) { AppGraph.mirrorPolicyProvider.ensureDefault(base) }

                var cfgState by remember {
                    mutableStateOf(
                        RepoConfig(
                            baseUrl = base,
                            enabled = r.enabled
                        )
                    )
                }
                LaunchedEffect(base) {
                    val dao = AppGraph.db.repoConfigDao()
                    cfgState = dao.get(base) ?: RepoConfig(baseUrl = base, enabled = r.enabled)
                }

                fun persist(newCfg: RepoConfig) {
                    cfgState = newCfg
                    scope.launch { AppGraph.db.repoConfigDao().upsert(newCfg) }
                }

                Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = typography.bodyLarge)
                                Text(
                                    r.url,
                                    style = typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = r.enabled,
                                onCheckedChange = {
                                    scope.launch { vm.toggleRepository(r.url) }
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Mirror policy
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilterChip(
                                selected = cfgState.rotateMirrors,
                                onClick = { persist(cfgState.copy(rotateMirrors = !cfgState.rotateMirrors)) },
                                label = { Text("Rotate mirrors") }
                            )
                            FilterChip(
                                selected = cfgState.includeOnion,
                                onClick = { persist(cfgState.copy(includeOnion = !cfgState.includeOnion)) },
                                label = { Text("Use onion") }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        var openStrategy by remember { mutableStateOf(false) }
                        val strategies = listOf("StickyLastGood", "RoundRobin", "CanonicalFirst")
                        val strategyIdx = strategies.indexOf(cfgState.strategy).coerceAtLeast(0)

                        ExposedDropdownMenuBox(
                            expanded = openStrategy,
                            onExpandedChange = { openStrategy = !openStrategy }
                        ) {
                            val anchorModifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .focusable()
                                .semantics { this.role = Role.Button }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { openStrategy = !openStrategy }
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown &&
                                        (ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionDown)
                                    ) { openStrategy = !openStrategy; true } else false
                                }

                            OutlinedTextField(
                                value = strategies[strategyIdx],
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(id = R.string.mirror_strategy)) }, // move label to strings.xml
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openStrategy) },
                                modifier = anchorModifier
                            )
                            ExposedDropdownMenu(expanded = openStrategy, onDismissRequest = { openStrategy = false }) {
                                strategies.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            openStrategy = false
                                            persist(cfgState.copy(strategy = s))
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Trust options
                        Text(stringResource(id = R.string.trust), style = typography.labelLarge)
                        Spacer(Modifier.height(6.dp))

                        var openTrust by remember { mutableStateOf(false) }
                        val trustModes = listOf("HttpsOnly", "Pinned", "CustomCA")
                        val trustIdx = trustModes.indexOf(cfgState.trustMode).coerceAtLeast(0)

                        ExposedDropdownMenuBox(
                            expanded = openTrust,
                            onExpandedChange = { openTrust = !openTrust }
                        ) {
                            val anchorModifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .focusable()
                                .semantics { this.role = Role.Button }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { openTrust = !openTrust }
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown &&
                                        (ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionDown)
                                    ) { openTrust = !openTrust; true } else false
                                }

                            OutlinedTextField(
                                value = trustModes[trustIdx],
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(id = R.string.trust_mode)) }, // move label to strings.xml
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openTrust) },
                                modifier = anchorModifier
                            )
                            ExposedDropdownMenu(expanded = openTrust, onDismissRequest = { openTrust = false }) {
                                trustModes.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            openTrust = false
                                            persist(cfgState.copy(trustMode = s))
                                        }
                                    )
                                }
                            }
                        }

                        if (cfgState.trustMode == "Pinned") {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = cfgState.pins,
                                onValueChange = { persist(cfgState.copy(pins = it)) },
                                label = { Text("Pins (sha256/BASE64, comma separated)") },
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (cfgState.trustMode == "CustomCA") {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = cfgState.caPem,
                                onValueChange = { persist(cfgState.copy(caPem = it)) },
                                label = { Text("Custom CA PEM") },
                                singleLine = false,
                                minLines = 4,
                                maxLines = 12,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Actions under the list
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    var showAdd by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showAdd = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Repository")
                    }
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.error
                        )
                    ) {
                        Text("Reset to defaults")
                    }
                    if (showAdd) {
                        AddRepoDialog(
                            onDismiss = { showAdd = false },
                            onAdd = { name, url ->
                                scope.launch {
                                    vm.addRepository(name, url)
                                    AppGraph.mirrorPolicyProvider.ensureDefault(url.trimEnd('/'))
                                }
                                showAdd = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDropdown && currentProp != null && currentAnn != null) {
        val prop = currentProp!!
        val ann = currentAnn!!
        val idx = prop.get(settings) as? Int ?: 0
        DropdownSettingDialog(
            title = ann.title,
            options = ann.options.toList(),
            selectedIndex = idx,
            onDismiss = { showDropdown = false },
            onOptionSelected = { i ->
                vm.updateSetting(prop.name, i)
                showDropdown = false
            }
        )
    }

    if (showSlider && currentProp != null && currentAnn != null) {
        val prop = currentProp!!
        val ann = currentAnn!!
        val cur = when (val v = prop.get(settings)) {
            is Int -> v.toFloat()
            is Float -> v
            else -> 0f
        }
        SliderSettingDialog(
            title = ann.title,
            currentValue = cur,
            min = ann.min,
            max = ann.max,
            step = ann.step,
            onDismiss = { showSlider = false },
            onValueSelected = { value ->
                when (prop.returnType.classifier) {
                    Int::class -> vm.updateSetting(prop.name, value.toInt())
                    Float::class -> vm.updateSetting(prop.name, value)
                }
                showSlider = false
            }
        )
    }

    if (showResetConfirm) {
        ConfirmationDialog(
            title = "Reset repositories",
            message = "This will remove all custom repositories and restore the default list. Continue?",
            confirmText = "Reset",
            dismissText = stringResource(R.string.action_cancel),
            isDangerous = true,
            onConfirm = {
                showResetConfirm = false
                vm.resetRepositoriesToDefaults()
            },
            onDismiss = { showResetConfirm = false }
        )
    }
}


@Composable
private fun AddRepoDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val canAdd = url.isNotBlank()

    FlickyDialog(
        onDismissRequest = onDismiss,
        title = stringResource(id = R.string.add_repository), // add to strings.xml
        confirmButton = {
            TextButton(
                onClick = { if (canAdd) onAdd(name.ifBlank { url }, url) },
                enabled = canAdd,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.primary,
                    disabledContentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            ) {
                Text(stringResource(id = R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.onSurfaceVariant
                )
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    ) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(id = R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(id = R.string.url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
