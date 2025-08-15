package app.flicky.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.Setting
import app.flicky.data.repository.SettingCategory
import app.flicky.data.repository.SettingType
import app.flicky.data.repository.SettingsManager
import app.flicky.ui.components.SettingsAction
import app.flicky.ui.components.SettingsItem
import app.flicky.ui.components.SettingsToggle
import app.flicky.ui.dialogs.DropdownSettingDialog
import app.flicky.ui.dialogs.SliderSettingDialog
import app.flicky.viewmodel.SettingsViewModel
import kotlin.reflect.KProperty1

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    val repos by vm.repositories.collectAsState()
    val manager = remember { SettingsManager() }

    var showDropdown by remember { mutableStateOf(false) }
    var showSlider by remember { mutableStateOf(false) }
    var currentProp by remember { mutableStateOf<KProperty1<AppSettings, *>?>(null) }
    var currentAnn by remember { mutableStateOf<Setting?>(null) }

    val grouped = remember { manager.getByCategory() }
    val cfg = LocalConfiguration.current
    // roughly 420dp per cell feels good on TV/phone acc. to ... u know
    val gridCells = remember(cfg.screenWidthDp) { GridCells.Adaptive(minSize = 420.dp) }

    LazyVerticalGrid(
        columns = gridCells,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Generate sections by category
        for (category in SettingCategory.entries) {
            val itemsForCat = grouped[category] ?: emptyList()
            if (itemsForCat.isEmpty()) continue

            // Category header
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            // category wise
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
                            is Float -> String.format("%.1f", v)
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
                            onClick = {
                                vm.performAction(prop.name)
                            }
                        )
                    }
                }
            }
        }

        // Repositories header
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Repositories",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
            )
        }

        // Repository items
        items(repos, key = { it.url }) { r ->
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            r.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = r.enabled, onCheckedChange = { vm.toggleRepository(r.url) })
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            var showAdd by remember { mutableStateOf(false) }
            Button(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Repository")
            }
            if (showAdd) {
                AddRepoDialog(
                    onDismiss = { showAdd = false },
                    onAdd = { name, url ->
                        vm.addRepository(name, url)
                        showAdd = false
                    }
                )
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
}

@Composable
private fun AddRepoDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Repository") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onAdd(name.ifBlank { url }, url) }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}