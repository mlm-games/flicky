package app.flicky.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flicky.data.settings.*
import app.flicky.ui.components.SettingsItem
import app.flicky.ui.components.SettingsSection
import app.flicky.ui.components.SettingsToggle
import app.flicky.viewmodel.SettingsViewModel

@Composable
fun SettingsScreenGenerated(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    val repos by vm.repositories.collectAsState()
    val manager = remember { SettingsManager() }
    val grouped = manager.getByCategory()

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        // Auto-generated settings sections
        SettingCategory.entries.forEach { cat ->
            val items = grouped[cat] ?: emptyList()
            if (items.isEmpty()) return@forEach

            SettingsSection(title = cat.name.lowercase().replaceFirstChar { it.uppercase() }) {
                items.forEach { (prop, ann) ->
                    val enabled = manager.isEnabled(settings, prop, ann)

                    when (ann.type) {
                        SettingType.TOGGLE -> {
                            val current = prop.get(settings) as? Boolean ?: false
                            SettingsToggle(
                                title = ann.title,
                                checked = current,
                                onCheckedChange = { vm.update(prop.name, it) }
                            )
                        }

                        SettingType.DROPDOWN -> {
                            val current = prop.get(settings) as? Int ?: 0
                            val options = ann.options.toList()
                            var openDialog by remember { mutableStateOf(false) }

                            SettingsItem(
                                title = ann.title,
                                subtitle = options.getOrNull(current) ?: "Unknown",
                                enabled = enabled
                            ) {
                                openDialog = true
                            }
                            if (openDialog) {
                                AlertDialog(
                                    onDismissRequest = { openDialog = false },
                                    title = { Text(ann.title) },
                                    text = {
                                        Column {
                                            options.forEachIndexed { idx, label ->
                                                ListItem(
                                                    headlineContent = { Text(label) },
                                                    trailingContent = {
                                                        RadioButton(
                                                            selected = current == idx,
                                                            onClick = {
                                                                vm.update(prop.name, idx)
                                                                openDialog = false
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    confirmButton = {}
                                )
                            }
                        }

                        SettingType.SLIDER -> {
                            val current = when (val v = prop.get(settings)) {
                                is Int -> v.toFloat()
                                is Float -> v
                                else -> 0f
                            }
                            Column(Modifier.padding(12.dp)) {
                                Text("${ann.title}: ${current.toInt()}")
                                Slider(
                                    value = current,
                                    onValueChange = { v ->
                                        if (prop.returnType.classifier == Int::class) vm.update(prop.name, v.toInt())
                                        else vm.update(prop.name, v)
                                    },
                                    valueRange = ann.min..ann.max,
                                    steps = ((ann.max - ann.min) / ann.step).toInt() - 1
                                )
                            }
                        }

                        SettingType.BUTTON -> {
                            Button(onClick = { /* custom action can be wired here */ }, modifier = Modifier.fillMaxWidth()) {
                                Text(ann.title)
                            }
                        }
                    }
                }
            }
        }

        // Dynamic Repositories Section
        SettingsSection(title = "Repositories") {
            repos.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(r.name)
                        Text(r.url, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = r.enabled, onCheckedChange = { vm.toggleRepository(r.url) })
                }
            }
            Spacer(Modifier.height(8.dp))
            var showAdd by remember { mutableStateOf(false) }
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Add Repository") }
            if (showAdd) {
                AddRepoDialog(
                    onDismiss = { showAdd = false },
                    onAdd = { name, url -> vm.addRepository(name, url); showAdd = false }
                )
            }
        }
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
            TextButton(onClick = { if (url.isNotBlank()) onAdd(name.ifBlank { url }, url) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}