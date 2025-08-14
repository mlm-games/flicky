package app.flicky.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flicky.data.model.FDroidApp
import coil.compose.AsyncImage

@Composable
fun AppDetailScreen(
    app: FDroidApp,
    installedVersionCode: Long?,
    isInstalling: Boolean,
    progress: Float,
    onInstall: ()->Unit,
    onOpen: ()->Unit,
    onUninstall: ()->Unit,
    error: String?
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(360.dp).padding(20.dp)) {
            AsyncImage(model = app.iconUrl, contentDescription = app.name, modifier = Modifier.size(120.dp))
            Spacer(Modifier.height(12.dp))
            Text(app.name, style = MaterialTheme.typography.titleLarge)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))

            if (isInstalling) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Installing... ${(progress * 100).toInt()}%")
            } else {
                if (installedVersionCode != null) {
                    Row {
                        Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f)) { Text("Uninstall") }
                    }
                } else {
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Install") }
                }
            }
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("v${app.version}") })
                AssistChip(onClick = {}, label = { Text(app.category) })
                AssistChip(onClick = {}, label = { Text("${app.size / (1024*1024)} MB") })
            }
            if (app.antiFeatures.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Anti-features:", color = MaterialTheme.colorScheme.tertiary)
                FlowRowSpaced(app.antiFeatures) { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            }
        }
        Divider()
        Column(Modifier.weight(1f).padding(20.dp)) {
            Text(app.summary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (app.screenshots.isNotEmpty()) {
                Text("Screenshots", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(app.screenshots) { url ->
                        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(240.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Text(app.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FlowRowSpaced(items: List<String>, item: @Composable (String)->Unit) {
    Column {
        var row = mutableListOf<String>()
        items.forEachIndexed { idx, s ->
            row.add(s)
            if (row.size == 3 || idx == items.lastIndex) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item(it) }
                }
                Spacer(Modifier.height(4.dp))
                row = mutableListOf()
            }
        }
    }
}