package app.flicky.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flicky.R


@Composable
fun TvNavigationSidebar(selected: Int, onSelect: (Int)->Unit) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp),
        color = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(Modifier.padding(8.dp)) {
                Icon(
                    Icons.Default.Shop,
                    contentDescription = null,
                    tint = colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        stringResource(R.string.app_name),
                        style = typography.titleLarge,
                        color = colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.app_description),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            NavItem( stringResource(R.string.nav_browse), Icons.Default.Explore, selected == 0) { onSelect(0) }
            NavItem( stringResource(R.string.nav_categories), Icons.Default.Category, selected == 1) { onSelect(1) }
            NavItem( stringResource(R.string.nav_updates), Icons.Default.Update, selected == 2) { onSelect(2) }
            NavItem( stringResource(R.string.nav_settings), Icons.Default.Settings, selected == 3) { onSelect(3) }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick:()->Unit
) {
    val colors = colorScheme

    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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