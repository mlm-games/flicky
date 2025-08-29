package app.flicky.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import coil.compose.AsyncImage

@Composable
fun AppIcon(name: String, url: String?, size: androidx.compose.ui.unit.Dp = 56.dp) {
    AsyncImage(model = url, contentDescription = name, modifier = Modifier.size(size))
}

@Composable
fun AppTexts(
    name: String,
    installedLabel: String?,
    newLabel: String?,
    summary: String,
) {
    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    installedLabel?.let {
        Text("Installed: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    newLabel?.let {
        Text("New: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}