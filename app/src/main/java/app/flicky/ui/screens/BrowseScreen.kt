package app.flicky.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.ui.components.VoiceSearchButton
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    apps: List<FDroidApp>,
    sort: SortOption,
    onSortChange: (SortOption)->Unit,
    onSearchChange: (String)->Unit,
    onAppClick: (FDroidApp)->Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row {
            TextField(value = "", onValueChange = onSearchChange, placeholder = { Text("Search apps...") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            VoiceSearchButton { spoken -> onSearchChange(spoken) }
            Spacer(Modifier.width(12.dp))
            SortButton(sort = sort, onSortChange = onSortChange)
        }
        Spacer(Modifier.height(12.dp))
        if (apps.isEmpty()) {
            Text("No apps found", color = MaterialTheme.colorScheme.outline)
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 220.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    AppCardTv(app = app, onClick = { onAppClick(app) })
                }
            }
        }
    }
}

@Composable
private fun AppCardTv(app: FDroidApp, onClick: ()->Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            AsyncImage(model = app.iconUrl, contentDescription = app.name, modifier = Modifier.height(140.dp).fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(app.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(app.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

@Composable
private fun FilterButton(sort: SortOption, onSortChange: (SortOption)->Unit) {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }) { Text("Sort: ${sort.name}") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Sort by") },
            text = {
                Column {
                    SortOption.values().forEach {
                        TextButton(onClick = { onSortChange(it); open = false }) { Text(it.name) }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
@Composable
private fun SortButton(sort: SortOption, onSortChange: (SortOption)->Unit) {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }) { Text("Sort: ${sort.name}") }
    if (open) {
        AlertDialog(onDismissRequest = { open = false }, title = { Text("Sort by") },
            text = {
                Column {
                    SortOption.values().forEach {
                        TextButton(onClick = { onSortChange(it); open=false }) { Text(it.name) }
                    }
                }
            }, confirmButton = {})
    }
}
