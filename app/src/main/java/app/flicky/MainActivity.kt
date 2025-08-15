package app.flicky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.flicky.data.model.SortOption
import app.flicky.navigation.FlickyNavHost
import app.flicky.navigation.Routes
import app.flicky.ui.screens.*
import app.flicky.ui.theme.FlickyTheme
import app.flicky.viewmodel.*
import app.flicky.work.SyncScheduler
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.flicky.helper.DeviceUtils

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)

        setContent {
            // Observe settings for theme + scheduling
            val settingsState by AppGraph.settings.settingsFlow.collectAsState(initial = null)
            val themeMode = settingsState?.themeMode ?: 2
            when (themeMode) { 0 -> false; 1 -> false; else -> true }

            // Schedule WorkManager based on settings
            LaunchedEffect(settingsState?.wifiOnly, settingsState?.syncIntervalIndex) {
                val wifiOnly = settingsState?.wifiOnly ?: true
                val hours = when (settingsState?.syncIntervalIndex ?: 1) {
                    0 -> 3; 1 -> 6; 2 -> 12; else -> 24
                }
                SyncScheduler.schedule(applicationContext, wifiOnly, hours)
            }

            val browseVM = viewModelFactoryOvr { BrowseViewModel(AppGraph.appRepo, AppGraph.syncManager, AppGraph.settings) }
            val settingsVM = viewModelFactoryOvr { SettingsViewModel(AppGraph.settings) }
            val updatesVM = viewModelFactoryOvr { UpdatesViewModel(AppGraph.appRepo, AppGraph.installedRepo) }
            val browseUi by browseVM.uiState.collectAsState()
            val updatesUi by updatesVM.ui.collectAsState()

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

            // Initial sync (first frame)
            LaunchedEffect(Unit) { lifecycleScope.launch { AppGraph.syncManager.syncAll(true) } }

            var sort by remember { mutableStateOf(SortOption.Updated) }
            var query by remember { mutableStateOf("") }

            val isTV = DeviceUtils.isTV(packageManager)

            FlickyTheme {
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
                        }
                    ) {
                        FlickyNavHost(
                            navController = navController,
                            browseContent = {
                                BrowseScreen(
                                    apps = browseUi.apps.filter {
                                        query.isBlank() || it.name.contains(query, true) ||
                                                it.summary.contains(query, true) || it.packageName.contains(query, true)
                                    },
                                    query = query,
                                    sort = sort,
                                    onSortChange = { s -> sort = s; browseVM.setSort(s) },
                                    onSearchChange = { q -> query = q; browseVM.setQuery(q) },
                                    onAppClick = { app -> navController.navigate(Routes.detail(app.packageName)) },
                                    onSyncClick = { browseVM.syncRepos() },
                                    onForceSyncClick = { browseVM.forceSyncRepos() },
                                    isSyncing = browseUi.isSyncing,
                                    progress = browseUi.progress,
                                    errorMessage = browseUi.errorMessage,
                                    onDismissError = { browseVM.clearError() }
                                )
                            },
                            categoriesContent = {
                                CategoriesScreen(
                                    onSyncClick = { browseVM.syncRepos() },
                                    isSyncing = browseUi.isSyncing,
                                    progress = browseUi.progress
                                )
                            },
                            updatesContent = {
                                UpdatesScreen(
                                    installed = updatesUi.installed,
                                    updates = updatesUi.updates,
                                    onUpdateAll = {
                                        lifecycleScope.launch {
                                            // serialize installs to avoid overwhelming DM / user prompts
                                            for (app in updatesUi.updates) {
                                                AppGraph.installer.install(app) {}
                                            }
                                        }
                                    },
                                    onUpdateOne = { app ->
                                        lifecycleScope.launch { AppGraph.installer.install(app) {} }
                                    }
                                )
                            },
                            settingsContent = { SettingsScreen(vm = settingsVM) },
                            detailContent = { pkg ->
                                val detailVM = viewModelFactoryOvr {
                                    AppDetailViewModel(
                                        dao = AppGraph.db.appDao(),
                                        installedRepo = AppGraph.installedRepo,
                                        installer = AppGraph.installer,
                                        packageName = pkg
                                    )
                                }
                                val ui by detailVM.ui.collectAsState()
                                val app = ui.app ?: return@FlickyNavHost
                                AppDetailScreen(
                                    app = app,
                                    installedVersionCode = ui.installedVersionCode,
                                    isInstalling = ui.isInstalling,
                                    progress = ui.progress,
                                    onInstall = { detailVM.install() },
                                    onOpen = { detailVM.openApp() },
                                    onUninstall = { detailVM.uninstall() },
                                    error = ui.error
                                )
                            }
                        )
                    }
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
                        }
                    ) {
                        FlickyNavHost(
                            navController = navController,
                            browseContent = {
                                BrowseScreen(
                                    apps = browseUi.apps.filter {
                                        query.isBlank() || it.name.contains(query, true) ||
                                                it.summary.contains(query, true) || it.packageName.contains(query, true)
                                    },
                                    query = query,
                                    sort = sort,
                                    onSortChange = { s -> sort = s; browseVM.setSort(s) },
                                    onSearchChange = { q -> query = q; browseVM.setQuery(q) },
                                    onAppClick = { app -> navController.navigate(Routes.detail(app.packageName)) },
                                    onSyncClick = { browseVM.syncRepos() },
                                    onForceSyncClick = { browseVM.forceSyncRepos() },
                                    isSyncing = browseUi.isSyncing,
                                    progress = browseUi.progress,
                                    errorMessage = browseUi.errorMessage,
                                    onDismissError = { browseVM.clearError() }
                                )
                            },
                            categoriesContent = {
                                CategoriesScreen(
                                    onSyncClick = { browseVM.syncRepos() },
                                    isSyncing = browseUi.isSyncing,
                                    progress = browseUi.progress
                                )
                            },
                            updatesContent = {
                                UpdatesScreen(
                                    installed = updatesUi.installed,
                                    updates = updatesUi.updates,
                                    onUpdateAll = {
                                        lifecycleScope.launch {
                                            // serialize installs to avoid overwhelming DM / user prompts
                                            for (app in updatesUi.updates) {
                                                AppGraph.installer.install(app) {}
                                            }
                                        }
                                    },
                                    onUpdateOne = { app ->
                                        lifecycleScope.launch { AppGraph.installer.install(app) {} }
                                    }
                                )
                            },
                            settingsContent = { SettingsScreen(vm = settingsVM) },
                            detailContent = { pkg ->
                                val detailVM = viewModelFactoryOvr {
                                    AppDetailViewModel(
                                        dao = AppGraph.db.appDao(),
                                        installedRepo = AppGraph.installedRepo,
                                        installer = AppGraph.installer,
                                        packageName = pkg
                                    )
                                }
                                val ui by detailVM.ui.collectAsState()
                                val app = ui.app ?: return@FlickyNavHost
                                AppDetailScreen(
                                    app = app,
                                    installedVersionCode = ui.installedVersionCode,
                                    isInstalling = ui.isInstalling,
                                    progress = ui.progress,
                                    onInstall = { detailVM.install() },
                                    onOpen = { detailVM.openApp() },
                                    onUninstall = { detailVM.uninstall() },
                                    error = ui.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
inline fun <reified VM: ViewModel> viewModelFactoryOvr(crossinline create: () -> VM): VM {
    return viewModel(factory = viewModelFactory { initializer { create() } })
}