package app.flicky

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.rememberNavBackStack
import app.flicky.data.repository.AppSettings
import app.flicky.di.AppDependencies
import app.flicky.navigation.NavScreen
import app.flicky.navigation.Nav3Host
import app.flicky.network.CoilCallFactory
import app.flicky.ui.components.snackbar.LauncherSnackbarHost
import app.flicky.ui.components.snackbar.SnackbarManager
import app.flicky.ui.screens.MobileMainScaffold
import app.flicky.ui.screens.TvMainScreen
import app.flicky.ui.theme.FlickyTheme
import app.flicky.viewmodel.BrowseViewModel
import app.flicky.viewmodel.SettingsViewModel
import app.flicky.work.SyncScheduler
import coil.Coil
import coil.ImageLoader
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            val callFactory = CoilCallFactory(AppDependencies.httpClients)
            val loader = ImageLoader.Builder(applicationContext)
                .callFactory(callFactory)
                .crossfade(true)
                .build()
            Coil.setImageLoader(loader)
        }

        setContent {
            val browseViewModel: BrowseViewModel = koinViewModel()
            val settingsViewModel: SettingsViewModel = koinViewModel()

            val settingsState by settingsViewModel.settings.collectAsState(AppSettings())

            LaunchedEffect(settingsState.failOnTrustErrors) {
                val callFactory = CoilCallFactory(AppDependencies.httpClients, settingsState.failOnTrustErrors)
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

            val isTV = app.flicky.helper.DeviceUtils.isTV(packageManager)

            val themeMode = settingsState.themeMode
            val dynamicColors = settingsState.dynamicTheme

            val backStack = rememberNavBackStack(NavScreen.Browse)

            fun selectedIndexForTop(): Int {
                return when (backStack.lastOrNull()) {
                    is NavScreen.Browse -> 0
                    is NavScreen.Detail -> 0
                    is NavScreen.Updates -> 1
                    is NavScreen.Favorites -> 2
                    is NavScreen.Settings -> 3
                    else -> 0
                }
            }

            fun switchTab(index: Int) {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)

                val dest = when (index) {
                    0 -> NavScreen.Browse
                    1 -> NavScreen.Updates
                    2 -> NavScreen.Favorites
                    else -> NavScreen.Settings
                }
                if (backStack.lastOrNull() != dest) {
                    backStack.add(dest)
                }
            }

            FlickyTheme(
                darkTheme = when (themeMode) { 0 -> isSystemInDarkTheme(); 1 -> false; else -> true },
                dynamicColor = dynamicColors
            ) {

                val snackbarHostState = remember { SnackbarHostState() }
                val snackbarManager: SnackbarManager = koinInject()

                val content = @Composable {
                    Nav3Host(
                        backStack = backStack,
                        browseViewModel = browseViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }

                Scaffold(
                    snackbarHost = {
                        LauncherSnackbarHost(
                            hostState = snackbarHostState,
                            manager = snackbarManager
                        )
                    }
                ) {
                    if (isTV) {
                        TvMainScreen(
                            selectedIndex = selectedIndexForTop(),
                            onSelect = ::switchTab,
                            content = content
                        )
                    } else {
                        MobileMainScaffold(
                            selectedIndex = selectedIndexForTop(),
                            onSelect = ::switchTab,
                            content = content
                        )
                    }
                }
            }
        }
    }
}