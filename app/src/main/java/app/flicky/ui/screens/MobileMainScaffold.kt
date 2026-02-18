package app.flicky.ui.screens

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.R
import app.flicky.data.repository.AlertBanners
import app.flicky.ui.components.AlertBanner
import kotlinx.coroutines.launch

private data class NavItem(@param:StringRes val labelResId: Int, val icon: ImageVector, val index: Int)

private val navItems = listOf(
    NavItem(R.string.nav_browse, Icons.Default.Explore, 0),
//    NavItem(R.string.nav_categories, Icons.Default.Category, 2),
    NavItem(R.string.nav_updates, Icons.Default.Update, 1),
    NavItem(R.string.nav_favorites, Icons.Default.Favorite, 2),
    NavItem(R.string.nav_settings, Icons.Default.Settings, 3)
)

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun MobileMainScaffold(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val isTablet = widthDp >= 900

    val activeBanner = AlertBanners.activeBanners.firstOrNull()
    val settingsRepository = AppGraph.settings
    val dismissedIds by settingsRepository.settingsFlow.collectAsState(initial = app.flicky.data.repository.AppSettings()).value.let { settings ->
        remember { mutableStateOf(settings.dismissedAlertBannerIds) }
    }
    val scope = rememberCoroutineScope()
    
    var localDismissedIds by remember { mutableStateOf(dismissedIds) }
    
    val currentBanner = activeBanner?.takeIf { it.id !in localDismissedIds }

    if (isTablet) {
        Column(Modifier.fillMaxSize()) {
            currentBanner?.let { banner ->
                AlertBanner(
                    banner = banner,
                    onDismiss = {
                        localDismissedIds = localDismissedIds + banner.id
                        scope.launch {
                            settingsRepository.dismissAlertBanner(banner.id)
                        }
                    }
                )
            }
            Row(Modifier.weight(1f)) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor =
                        colorScheme.surfaceContainerLow,
                    contentColor =
                        colorScheme.onSurfaceVariant,
                    header = {
                        Column(Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Default.Shop,
                                contentDescription = null,
                                tint =
                                    colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.app_name),
                                color =
                                    colorScheme.onSurface
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
                                selectedIconColor =
                                    colorScheme.onPrimaryContainer,
                                selectedTextColor =
                                    colorScheme.onPrimaryContainer,
                                indicatorColor =
                                    colorScheme.primaryContainer,
                                unselectedIconColor =
                                    colorScheme.onSurfaceVariant,
                                unselectedTextColor =
                                    colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                VerticalDivider()
                Box(Modifier.weight(1f)) { content() }
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor =
                        colorScheme.surfaceContainerLow,
                    contentColor =
                        colorScheme.onSurfaceVariant
                ) {
                    navItems.forEach { item ->
                        val label = stringResource(item.labelResId)
                        NavigationBarItem(
                            selected = selectedIndex == item.index,
                            onClick = { onSelect(item.index) },
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor =
                                    colorScheme.onPrimaryContainer,
                                selectedTextColor =
                                    colorScheme.onPrimaryContainer,
                                indicatorColor =
                                    colorScheme.primaryContainer,
                                unselectedIconColor =
                                    colorScheme.onSurfaceVariant,
                                unselectedTextColor =
                                    colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                currentBanner?.let { banner ->
                    AlertBanner(
                        banner = banner,
                        onDismiss = {
                            localDismissedIds = localDismissedIds + banner.id
                            scope.launch {
                                settingsRepository.dismissAlertBanner(banner.id)
                            }
                        }
                    )
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}