package app.flicky.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

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
                header = {
                    Column(Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Shop, contentDescription = null)
                        Text("Flicky")
                    }
                }
            ) {
                NavigationRailItem(
                    selected = selectedIndex == 0,
                    onClick = { onSelect(0) },
                    icon = { Icon(Icons.Default.Explore, null) },
                    label = { Text("Browse") }
                )
                NavigationRailItem(
                    selected = selectedIndex == 1,
                    onClick = { onSelect(1) },
                    icon = { Icon(Icons.Default.Category, null) },
                    label = { Text("Categories") }
                )
                NavigationRailItem(
                    selected = selectedIndex == 2,
                    onClick = { onSelect(2) },
                    icon = { Icon(Icons.Default.Update, null) },
                    label = { Text("Updates") }
                )
                NavigationRailItem(
                    selected = selectedIndex == 3,
                    onClick = { onSelect(3) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
            Divider()
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        // Bottom Navigation for phones
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedIndex == 0,
                        onClick = { onSelect(0) },
                        icon = { Icon(Icons.Default.Explore, null) },
                        label = { Text("Browse") }
                    )
                    NavigationBarItem(
                        selected = selectedIndex == 1,
                        onClick = { onSelect(1) },
                        icon = { Icon(Icons.Default.Category, null) },
                        label = { Text("Categories") }
                    )
                    NavigationBarItem(
                        selected = selectedIndex == 2,
                        onClick = { onSelect(2) },
                        icon = { Icon(Icons.Default.Update, null) },
                        label = { Text("Updates") }
                    )
                    NavigationBarItem(
                        selected = selectedIndex == 3,
                        onClick = { onSelect(3) },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
}