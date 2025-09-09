package app.flicky.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import app.flicky.AppGraph
import app.flicky.data.repository.AppSettings
import coil.compose.AsyncImage

@Composable
fun AppIcon(name: String, url: String?, size: Dp = 56.dp) {
    val settings by AppGraph.settings.settingsFlow.collectAsState(initial = AppSettings())
    if (settings.showAppIcons) {
        AsyncImage(model = url, contentDescription = name, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size))
    }
}

@Composable
fun AppTexts(
    name: String,
    installedLabel: String?,
    newLabel: String?,
    summary: String,
) {
    Text(name, style = typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    installedLabel?.let {
        Text("Installed: $it", style = typography.bodySmall, color = colorScheme.onSurfaceVariant)
    }
    newLabel?.let {
        Text("New: $it", style = typography.bodySmall, color = colorScheme.primary)
    }
    Text(summary, style = typography.bodySmall, color = colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}