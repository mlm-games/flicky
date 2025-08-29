package app.flicky.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flicky.R

private data class NavItem(@param:StringRes val labelResId: Int, val icon: ImageVector, val index: Int)

private val navItems = listOf(
    NavItem(R.string.nav_browse, Icons.Default.Explore, 0),
    NavItem(R.string.nav_categories, Icons.Default.Category, 1),
    NavItem(R.string.nav_updates, Icons.Default.Update, 2),
    NavItem(R.string.nav_settings, Icons.Default.Settings, 3)
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
                            stringResource(R.string.app_name),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            ) {
                navItems.forEach { item ->
                    val label = stringResource(item.labelResId)
                    NavigationRailItem(
                        selected = selectedIndex == item.index,
                        onClick = { onSelect(item.index) },
                        icon = { Icon(item.icon, contentDescription = label) },
                        label = { Text(label) },
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
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 3.dp
                ) {
                    navItems.forEach { item ->
                        val label = stringResource(item.labelResId)
                        NavigationBarItem(
                            selected = selectedIndex == item.index,
                            onClick = { onSelect(item.index) },
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
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