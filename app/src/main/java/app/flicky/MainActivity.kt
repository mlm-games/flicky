package app.flicky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import app.flicky.data.external.UpdatesPreferences
import app.flicky.data.model.SortOption
import app.flicky.helper.DeviceUtils
import app.flicky.helper.viewModelFactory
import app.flicky.navigation.FlickyNavHost
import app.flicky.navigation.Routes
import app.flicky.ui.routes.AppDetailRoute
import app.flicky.ui.routes.UpdatesRoute
import app.flicky.ui.screens.BrowseScreen
import app.flicky.ui.screens.CategoriesScreen
import app.flicky.ui.screens.MobileMainScaffold
import app.flicky.ui.screens.SettingsScreen
import app.flicky.ui.screens.TvMainScreen
import app.flicky.ui.theme.FlickyTheme
import app.flicky.viewmodel.BrowseViewModel
import app.flicky.viewmodel.SettingsViewModel
import app.flicky.work.SyncScheduler

class MainActivity : ComponentActivity() {

    private val browseViewModel: BrowseViewModel by viewModels {
        viewModelFactory {
            BrowseViewModel(
                AppGraph.appRepo,
                AppGraph.syncManager,
                AppGraph.settings
            )
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        viewModelFactory { SettingsViewModel(AppGraph.settings) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        UpdatesPreferences.init(applicationContext)

        setContent {
            val settingsState by AppGraph.settings.settingsFlow.collectAsState(initial = null)

            LaunchedEffect(settingsState?.wifiOnly, settingsState?.syncIntervalIndex) {
                val wifiOnly = settingsState?.wifiOnly ?: true
                val hours = when (settingsState?.syncIntervalIndex ?: 1) {
                    0 -> 3; 1 -> 6; 2 -> 12; 3 -> 24; 4 -> 24 * 7; else -> -1
                }
                SyncScheduler.schedule(applicationContext, wifiOnly, hours)
            }

            val browseUi by browseViewModel.uiState.collectAsState()

            val navController = rememberNavController()
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route ?: Routes.Browse

            val selectedIndex = when {
                currentRoute.startsWith("detail/") -> 0
                currentRoute == Routes.Browse -> 0
                currentRoute == Routes.Categories -> 1
                currentRoute == Routes.Updates -> 2
                currentRoute == Routes.Settings -> 3
                else -> 0
            }

            var sort by remember { mutableStateOf(SortOption.Updated) }
            var query by remember { mutableStateOf("") }

            val isTV = DeviceUtils.isTV(packageManager)

            val settings = settingsViewModel.settings.collectAsState().value
            val themeMode = settings.themeMode
            val dynamicColors = settings.dynamicTheme

            FlickyTheme(
                when (themeMode) { 0 -> isSystemInDarkTheme(); 1 -> false; else -> true },
                dynamicColors
            ) {
                val contentComposable: @Composable () -> Unit = {
                    FlickyNavHost(
                        navController = navController,
                        browseContent = {
                            BrowseScreen(
                                apps = browseViewModel.pagedApps.collectAsLazyPagingItems(),
                                query = query,
                                sort = sort,
                                onSortChange = { s -> sort = s; browseViewModel.setSort(s) },
                                onSearchChange = { q -> query = q; browseViewModel.setQuery(q) },
                                onAppClick = { app -> navController.navigate(Routes.detail(app.packageName)) },
                                onSyncClick = { browseViewModel.syncRepos() },
                                onForceSyncClick = { browseViewModel.forceSyncRepos() },
                                onClearAppsClick = { browseViewModel.forceSyncRepos()},
                                isSyncing = browseUi.isSyncing,
                                progress = browseUi.progress,
                                errorMessage = browseUi.errorMessage,
                                onDismissError = { browseViewModel.clearError() },
                                syncStatus = browseUi.statusText,
                            )
                        },
                        categoriesContent = {
                            CategoriesScreen(
                                onSyncClick = { browseViewModel.syncRepos() },
                                isSyncing = browseUi.isSyncing,
                                progress = browseUi.progress,
                                onAppClick = { app -> navController.navigate(Routes.detail(app.packageName)) },
                            )
                        },
                        updatesContent = {
                            UpdatesRoute(
                                onOpenDetails = { pkg -> navController.navigate(Routes.detail(pkg)) }
                            )
                        },
                        settingsContent = { SettingsScreen(vm = settingsViewModel) },
                        detailContent = { pkg ->
                            AppDetailRoute(pkg = pkg)
                        }
                    )
                }

                if (isTV) {
                    TvMainScreen(
                        selectedIndex = selectedIndex,
                        onSelect = { idx ->
                            val route = when (idx) {
                                0 -> Routes.Browse
                                1 -> Routes.Categories
                                2 -> Routes.Updates
                                else -> Routes.Settings
                            }
                            navController.navigate(route) {
                                popUpTo(Routes.Browse) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        content = contentComposable
                    )
                } else {
                    MobileMainScaffold(
                        selectedIndex = selectedIndex,
                        onSelect = { idx ->
                            val route = when (idx) {
                                0 -> Routes.Browse
                                1 -> Routes.Categories
                                2 -> Routes.Updates
                                else -> Routes.Settings
                            }
                            navController.navigate(route) {
                                popUpTo(Routes.Browse) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        content = contentComposable
                    )
                }
            }
        }
    }
}

