package app.flicky

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.rememberNavBackStack
import app.flicky.data.remote.HttpClientProvider
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.repository.applyAppLocale
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

class MainActivity : AppCompatActivity() {

    private val httpClients: HttpClientProvider by inject()

    private val settingsRepository: SettingsRepository by inject()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

    private fun Intent.repoDeepLinkUrl(): String? = data?.repoDeepLinkUrl()

    private fun Intent.isUpdatesDeepLink(): Boolean {
        val uri = data ?: return false
        return uri.scheme.equals("flicky", ignoreCase = true) &&
            uri.host.equals("updates", ignoreCase = true)
    }

    private fun Uri.deepLinkPackageName(): String? = when {
        scheme.equals("https", ignoreCase = true) &&
            host.equals("f-droid.org", ignoreCase = true) -> {
            val idx = pathSegments.indexOf("packages")
            if (idx != -1) pathSegments.getOrNull(idx + 1) else null
        }

        scheme.equals("market", ignoreCase = true) ->
            getQueryParameter("id")?.takeIf { it.isNotBlank() }

        scheme.equals("https", ignoreCase = true) &&
            host.equals("play.google.com", ignoreCase = true) ->
            getQueryParameter("id")?.takeIf { it.isNotBlank() }

        else -> null
    }

    /**
     * fdroidrepos://example.org/fdroid/repo?fingerprint=... -> https://example.org/fdroid/repo
     * fdroidrepo://example.org/repo -> http://example.org/repo
     */
    private fun Uri.repoDeepLinkUrl(): String? {
        val scheme = scheme?.lowercase() ?: return null
        if (scheme != "fdroidrepos" && scheme != "fdroidrepo") return null

        runCatching { getQueryParameter("url") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return normalizeRepoUrl(it, scheme == "fdroidrepo") }

        val host = host?.lowercase() ?: return null
        val httpScheme = if (scheme == "fdroidrepo") "http" else "https"
        val segments = pathSegments.map { it.lowercase() }
        val path = when {
            segments.size >= 2 &&
                segments[segments.lastIndex - 1].equals("fdroid", true) &&
                (segments.last().equals("repo", true) || segments.last().equals("archive", true)) ->
                "/" + segments.joinToString("/")

            segments.lastOrNull()?.equals("repo", true) == true ||
                segments.lastOrNull()?.equals("archive", true) == true ->
                "/" + segments.joinToString("/")

            segments.lastOrNull()?.equals("fdroid", true) == true ->
                "/" + segments.joinToString("/") + "/repo"

            else -> "/" + (segments.filter { it.isNotBlank() } + listOf("fdroid", "repo")).joinToString("/")
        }
        return "$httpScheme://$host$path"
    }

    private fun normalizeRepoUrl(raw: String, preferHttp: Boolean): String {
        val trimmed = raw.trim()
        val withScheme = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            preferHttp -> "http://$trimmed"
            else -> "https://$trimmed"
        }
        val uri = Uri.parse(withScheme)
        val host = uri.host?.lowercase() ?: return withScheme.trimEnd('/')
        val scheme = uri.scheme?.lowercase() ?: "https"
        val path = uri.path.orEmpty().trimEnd('/')
        return "$scheme://$host$path"
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialPackage = intent.deepLinkPackageName()
        val initialUpdates = intent.isUpdatesDeepLink()
        val initialRepoUrl = intent.repoDeepLinkUrl()

        configureCoil()

        lifecycleScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                applyAppLocale(settings.language.languageTag)
            }
        }

        lifecycleScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.autoUpdate) maybeRequestNotificationPermission()
        }

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
                when {
                    initialRepoUrl != null -> NavScreen.Settings
                    initialUpdates -> NavScreen.Updates
                    initialPackage != null -> NavScreen.Detail(initialPackage)
                    else -> NavScreen.Browse
                }
            )

            var pendingRepoUrl by remember { mutableStateOf(initialRepoUrl) }

            DisposableEffect(Unit) {
                val listener = Consumer<Intent> { newIntent ->
                    val repoUrl = newIntent.repoDeepLinkUrl()
                    when {
                        repoUrl != null -> {
                            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                            if (backStack.lastOrNull() != NavScreen.Settings) {
                                backStack.add(NavScreen.Settings)
                            }
                            pendingRepoUrl = repoUrl
                        }

                        newIntent.isUpdatesDeepLink() -> backStack.add(NavScreen.Updates)
                        else -> newIntent.deepLinkPackageName()
                            ?.let { backStack.add(NavScreen.Detail(it)) }
                    }
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

            fun contentKeyForFocus(): Any {
                return when (val top = backStack.lastOrNull()) {
                    is NavScreen.Detail -> "detail:${top.pkg}"
                    is NavScreen.Categories -> "categories:${top.selected}"
                    is NavScreen.AuthorList -> "author:${top.authorName}"
                    is NavScreen.Browse -> "browse"
                    is NavScreen.Updates -> "updates"
                    is NavScreen.Favorites -> "favorites"
                    is NavScreen.Settings -> "settings"
                    else -> top?.toString() ?: "root"
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
                        settingsViewModel = settingsViewModel,
                        pendingRepoUrl = pendingRepoUrl,
                        onPendingRepoUrlConsumed = { pendingRepoUrl = null }
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
                            contentKey = contentKeyForFocus(),
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
