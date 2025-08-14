package app.flicky.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flicky.data.model.FDroidApp

@Composable
fun UpdatesScreen(installed: List<FDroidApp>, updates: List<FDroidApp>, onUpdateAll:()->Unit, onUpdateOne:(FDroidApp)->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        if (updates.isNotEmpty()) {
            Button(onClick = onUpdateAll, modifier = Modifier.fillMaxWidth()) { Text("Update All (${updates.size})") }
            Spacer(Modifier.height(8.dp))
            Text("Available Updates", style = MaterialTheme.typography.titleMedium)
            updates.forEach { app ->
                ListItem(headlineContent = { Text(app.name) }, supportingContent = { Text("New: ${app.version}") },
                    trailingContent = { Button(onClick = { onUpdateOne(app) }) { Text("Update") } })
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
            Spacer(Modifier.height(20.dp))
        }
        Text("Installed Apps", style = MaterialTheme.typography.titleMedium)
        installed.forEach { app ->
            ListItem(headlineContent = { Text(app.name) }, supportingContent = { Text("v${app.version}") })
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        }
    }
}
