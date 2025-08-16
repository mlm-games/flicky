package app.flicky

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.flicky.data.external.UpdatesPreferences
import app.flicky.helper.DeviceUtils
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {

    private val browseViewModel: BrowseViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BrowseViewModel(
                    AppGraph.appRepo,
                    AppGraph.syncManager,
                    AppGraph.settings
                ) as T
            }
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(
                    AppGraph.settings
                ) as T
            }
        }
    }

    private val updatesViewModel: UpdatesViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return UpdatesViewModel(
                    AppGraph.appRepo,
                    AppGraph.installedRepo
                ) as T
            }
        }
    }

    private val appDetailViewModel: AppDetailViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AppDetailViewModel(
                    AppGraph.db.appDao(),
                    AppGraph.installedRepo,
                    AppGraph.installer,
                    ""
                ) as T
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        UpdatesPreferences.init(applicationContext)

        setContent {
            val settingsState by AppGraph.settings.settingsFlow.collectAsState(initial = null)
//            val themeMode = settingsState?.themeMode ?: 2
//            when (themeMode) { 0 -> false; 1 -> false; else -> true }

            // Schedule WorkManager based on settings
            LaunchedEffect(settingsState?.wifiOnly, settingsState?.syncIntervalIndex) {
                val wifiOnly = settingsState?.wifiOnly ?: true
                val hours = when (settingsState?.syncIntervalIndex ?: 1) {
                    0 -> 3; 1 -> 6; 2 -> 12; else -> 24
                }
                SyncScheduler.schedule(applicationContext, wifiOnly, hours)
            }

            val browseUi by browseViewModel.uiState.collectAsState()
            val updatesUi by updatesViewModel.ui.collectAsState()

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
            LaunchedEffect(Unit) {
                // Delay initial sync slightly to ensure everything is initialized
                kotlinx.coroutines.delay(500)
                lifecycleScope.launch {
                    try {
                        Log.d("MainActivity", "Starting initial sync")
                        val count = AppGraph.db.appDao().count()
                        if (count == 0) {
                            Log.d("MainActivity", "Database empty, forcing initial sync")
                            AppGraph.syncManager.syncAll(force = true)
                        } else {
                            Log.d("MainActivity", "Database has $count apps, normal sync")
                            AppGraph.syncManager.syncAll(force = false)
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Initial sync failed", e)
                    }
                }
            }

            var sort by remember { mutableStateOf(SortOption.Updated) }
            var query by remember { mutableStateOf("") }

            val isTV = DeviceUtils.isTV(packageManager)

            val settings = settingsViewModel.settings.collectAsState().value

            val themeDark = settings.themeMode == 2
            val dynamicColors = settings.dynamicTheme

            FlickyTheme(
                themeDark,
                dynamicColors
            ) {
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
                                    onSortChange = { s -> sort = s; browseViewModel.setSort(s) },
                                    onSearchChange = { q -> query = q; browseViewModel.setQuery(q) },
                                    onAppClick = { app -> navController.navigate(Routes.detail(app.packageName)) },
                                    onSyncClick = { browseViewModel.syncRepos() },
                                    onForceSyncClick = { browseViewModel.forceSyncRepos() },
                                    isSyncing = browseUi.isSyncing,
                                    progress = browseUi.progress,
                                    errorMessage = browseUi.errorMessage,
                                    onDismissError = { browseViewModel.clearError() }
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
                                UpdatesScreen(
                                    installed = updatesUi.installed,
                                    updates = updatesUi.updates,
                                    installingPackages = updatesUi.installingPackages,
                                    installProgress = updatesUi.installProgress,
                                    onUpdateAll = {
                                        lifecycleScope.launch {
                                            for (app in updatesUi.updates) {
                                                updatesViewModel.setInstalling(app.packageName, true)
                                                AppGraph.installer.install(app) { progress ->
                                                    updatesViewModel.updateInstallProgress(app.packageName, progress)
                                                }
                                                updatesViewModel.setInstalling(app.packageName, false)
                                            }
                                        }
                                    },
                                    onUpdateOne = { app ->
                                        lifecycleScope.launch {
                                            updatesViewModel.setInstalling(app.packageName, true)
                                            AppGraph.installer.install(app) { progress ->
                                                updatesViewModel.updateInstallProgress(app.packageName, progress)
                                            }
                                            updatesViewModel.setInstalling(app.packageName, false)
                                        }
                                    },
                                    onAppClick = { app ->
                                        navController.navigate(Routes.detail(app.packageName))
                                    }
                                )
                            },
                            settingsContent = { SettingsScreen(vm = settingsViewModel) },
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
                                    onSortChange = { s -> sort = s; browseViewModel.setSort(s) },
                                    onSearchChange = { q -> query = q; browseViewModel.setQuery(q) },
                                    onAppClick = { app -> navController.navigate(Routes.detail(app.packageName)) },
                                    onSyncClick = { browseViewModel.syncRepos() },
                                    onForceSyncClick = { browseViewModel.forceSyncRepos() },
                                    isSyncing = browseUi.isSyncing,
                                    progress = browseUi.progress,
                                    errorMessage = browseUi.errorMessage,
                                    onDismissError = { browseViewModel.clearError() }
                                )
                            },
                            categoriesContent = {
                                CategoriesScreen(
                                    onSyncClick = { browseViewModel.syncRepos() },
                                    isSyncing = browseUi.isSyncing,
                                    progress = browseUi.progress,
                                    onAppClick = { app -> navController.navigate(Routes.detail(app.packageName)) }
                                )
                            },
                            updatesContent = {
                                UpdatesScreen(
                                    installed = updatesUi.installed,
                                    updates = updatesUi.updates,
                                    installingPackages = updatesUi.installingPackages,
                                    installProgress = updatesUi.installProgress,
                                    onUpdateAll = {
                                        lifecycleScope.launch {
                                            for (app in updatesUi.updates) {
                                                updatesViewModel.setInstalling(app.packageName, true)
                                                AppGraph.installer.install(app) { progress ->
                                                    updatesViewModel.updateInstallProgress(app.packageName, progress)
                                                }
                                                updatesViewModel.setInstalling(app.packageName, false)
                                            }
                                        }
                                    },
                                    onUpdateOne = { app ->
                                        lifecycleScope.launch {
                                            updatesViewModel.setInstalling(app.packageName, true)
                                            AppGraph.installer.install(app) { progress ->
                                                updatesViewModel.updateInstallProgress(app.packageName, progress)
                                            }
                                            updatesViewModel.setInstalling(app.packageName, false)
                                        }
                                    },
                                    onAppClick = { app ->
                                        navController.navigate(Routes.detail(app.packageName))
                                    }
                                )
                            },
                            settingsContent = { SettingsScreen(vm = settingsViewModel) },
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