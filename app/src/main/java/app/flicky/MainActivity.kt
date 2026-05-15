package app.flicky

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.util.Consumer
import androidx.navigation3.runtime.rememberNavBackStack
import app.flicky.data.remote.HttpClientProvider
import app.flicky.data.repository.AppSettings
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
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    private val httpClients: HttpClientProvider by inject()

    private fun configureCoil(failOnTrustErrors: Boolean = false) {
        runCatching {
            val callFactory = CoilCallFactory(httpClients, failOnTrustErrors)
            val loader = ImageLoader.Builder(applicationContext)
                .callFactory(callFactory)
                .crossfade(true)
                .build()
            Coil.setImageLoader(loader)
        }
    }

    private fun Intent.deepLinkPackageName(): String? = data?.deepLinkPackageName()

    private fun Uri.deepLinkPackageName(): String? = when {
        scheme.equals("https", ignoreCase = true) &&
            host.equals("f-droid.org", ignoreCase = true) -> {
            val idx = pathSegments.indexOf("packages")
            if (idx != -1) pathSegments.getOrNull(idx + 1) else null
        }

        scheme.equals("fdroidrepos", ignoreCase = true) ->
            host?.takeIf { it.isNotBlank() }

        else -> null
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialPackage = intent.deepLinkPackageName()

        configureCoil()

        setContent {
            val browseViewModel: BrowseViewModel = koinViewModel()
            val settingsViewModel: SettingsViewModel = koinViewModel()

            val settingsState by settingsViewModel.settings.collectAsState(AppSettings())

            LaunchedEffect(settingsState.failOnTrustErrors) {
                configureCoil(settingsState.failOnTrustErrors)
            }

            val isTV = app.flicky.helper.DeviceUtils.isTV(packageManager)

            val themeMode = settingsState.themeMode
            val dynamicColors = settingsState.dynamicTheme

            val backStack = rememberNavBackStack(
                if (initialPackage != null) NavScreen.Detail(initialPackage) else NavScreen.Browse
            )

            DisposableEffect(Unit) {
                val listener = Consumer<Intent> { newIntent ->
                    val pkg = newIntent.deepLinkPackageName()
                    pkg?.let { backStack.add(NavScreen.Detail(it)) }
                }
                addOnNewIntentListener(listener)
                onDispose { removeOnNewIntentListener(listener) }
            }

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
