package app.flicky.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TvNavigationSidebar(selected: Int, onSelect: (Int)->Unit) {
    Surface(
        modifier = Modifier.width(280.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.padding(8.dp)) {
                Icon(
                    Icons.Default.Shop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Flicky",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "F-Droid for TV",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            NavItem("Browse", Icons.Default.Explore, selected == 0) { onSelect(0) }
            NavItem("Categories", Icons.Default.Category, selected == 1) { onSelect(1) }
            NavItem("Updates", Icons.Default.Update, selected == 2) { onSelect(2) }
            NavItem("Settings", Icons.Default.Settings, selected == 3) { onSelect(3) }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick:()->Unit
) {
    val colors = MaterialTheme.colorScheme

    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) colors.primaryContainer else colors.surfaceVariant,
            contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
        )
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}