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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import app.flicky.data.external.UpdatesPreferences
import app.flicky.data.repository.AppSettings
import app.flicky.helper.DeviceUtils
import app.flicky.helper.viewModelFactory
import app.flicky.navigation.FlickyNavHost
import app.flicky.navigation.Routes
import app.flicky.network.CoilCallFactory
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
import coil.Coil
import coil.ImageLoader

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


        runCatching {
            val callFactory = CoilCallFactory(AppGraph.httpClients)
            val loader = ImageLoader.Builder(applicationContext)
                .callFactory(callFactory)
                .crossfade(true)
                .build()
            Coil.setImageLoader(loader)
        }

        setContent {
            val settingsState by AppGraph.settings.settingsFlow.collectAsState(AppSettings())

            LaunchedEffect(settingsState.failOnTrustErrors) {
                val callFactory = CoilCallFactory(AppGraph.httpClients, settingsState.failOnTrustErrors)
                val loader = ImageLoader.Builder(applicationContext)
                    .callFactory(callFactory)
                    .crossfade(true)
                    .build()
                Coil.setImageLoader(loader)
            }

            LaunchedEffect(settingsState.wifiOnly, settingsState.syncIntervalIndex) {
                val wifiOnly = settingsState.wifiOnly
                val hours = when (settingsState.syncIntervalIndex) {
                    0 -> 3; 1 -> 6; 2 -> 12; 3 -> 24; 4 -> 24 * 7; else -> -1
                }
                SyncScheduler.schedule(applicationContext, wifiOnly, hours)
            }

            val navController = rememberNavController()
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route ?: Routes.Browse

            val selectedIndex = when {
                currentRoute.startsWith("detail/") -> 0
                currentRoute == Routes.Browse -> 0
                currentRoute.startsWith("categories") -> 1
                currentRoute == Routes.Updates -> 2
                currentRoute == Routes.Settings -> 3
                else -> 0
            }

            val query by browseViewModel.query.collectAsStateWithLifecycle()
            val sort by browseViewModel.sort.collectAsStateWithLifecycle()
            val browseUi by browseViewModel.uiState.collectAsStateWithLifecycle()

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
                                onSortChange = browseViewModel::setSort,
                                onSearchChange = browseViewModel::setQuery,
                                onAppClick = { app -> navController.navigate(Routes.detail(app.packageName)) },
                                onSyncClick = browseViewModel::syncRepos,
                                onForceSyncClick = browseViewModel::forceSyncRepos,
                                onClearAppsClick = browseViewModel::clearAllApps,
                                isSyncing = browseUi.isSyncing,
                                progress = browseUi.progress,
                                errorMessage = browseUi.errorMessage,
                                onDismissError = browseViewModel::clearError,
                                syncStatusRes = browseUi.statusTextRes,
                                isTv = isTV
                            )
                        },
                        categoriesContent = { selected ->
                        CategoriesScreen(
                                isSyncing = browseUi.isSyncing,
                                progress = browseUi.progress,
                                initialCategory = selected ?: "All",
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
                            AppDetailRoute(
                                pkg = pkg,
                                onOpenCategory = { cat ->
                                    navController.navigate(
                                        Routes.categories(cat),
                                    )
                                },
                            )
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