package app.flicky.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

private data class NavItem(val label: String, val icon: ImageVector, val index: Int)

private val navItems = listOf(
    NavItem("Browse", Icons.Default.Explore, 0),
    NavItem("Categories", Icons.Default.Category, 1),
    NavItem("Updates", Icons.Default.Update, 2),
    NavItem("Settings", Icons.Default.Settings, 3)
)

@Composable
fun MobileMainScaffold(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val isTablet = widthDp >= 900

    if (isTablet) {
        // NavigationRail for tablet
        Row(Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                header = {
                    Column(Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Default.Shop, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Flicky",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            ) {
                navItems.forEach { item ->
                    NavigationRailItem(
                        selected = selectedIndex == item.index,
                        onClick = { onSelect(item.index) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
            VerticalDivider()
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        // Bottom Navigation for phones
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 3.dp
                ) {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = selectedIndex == item.index,
                            onClick = { onSelect(item.index) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
}