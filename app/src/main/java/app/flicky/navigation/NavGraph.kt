package app.flicky.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.paging.compose.collectAsLazyPagingItems
import app.flicky.ui.routes.AppDetailRoute
import app.flicky.ui.routes.FavoritesRoute
import app.flicky.ui.routes.UpdatesRoute
import app.flicky.ui.screens.BrowseScreen
import app.flicky.ui.screens.CategoriesScreen
import app.flicky.ui.screens.SettingsScreen
import app.flicky.viewmodel.BrowseViewModel
import app.flicky.viewmodel.SettingsViewModel

@Composable
fun Nav3Host(
    backStack: NavBackStack<NavKey>,
    browseViewModel: BrowseViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            } else {
                activity?.finish()
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<NavScreen.Browse> {
                val query = browseViewModel.query.collectAsStateWithLifecycle().value
                val sort = browseViewModel.sort.collectAsStateWithLifecycle().value
                val browseUi = browseViewModel.uiState.collectAsStateWithLifecycle().value

                BrowseScreen(
                    apps = browseViewModel.pagedApps.collectAsLazyPagingItems(),
                    query = query,
                    sort = sort,
                    onSortChange = browseViewModel::setSort,
                    onSearchChange = browseViewModel::setQuery,
                    onAppClick = { app -> backStack.add(NavScreen.Detail(app.packageName)) },
                    onCategoriesClick = { backStack.add(NavScreen.Categories(it)) },
                    onSyncClick = browseViewModel::syncRepos,
                    onForceSyncClick = browseViewModel::forceSyncRepos,
                    onClearAppsClick = browseViewModel::clearAllApps,
                    isSyncing = browseUi.isSyncing,
                    progress = browseUi.progress,
                    errorMessage = browseUi.errorMessage,
                    onDismissError = browseViewModel::clearError,
                    syncStatusRes = browseUi.statusTextRes,
                    isTv = app.flicky.helper.DeviceUtils.isTV((context).packageManager),
                )
            }

            entry<NavScreen.Categories> { args ->
                val browseUi = browseViewModel.uiState.collectAsStateWithLifecycle().value
                CategoriesScreen(
                    isSyncing = browseUi.isSyncing,
                    progress = browseUi.progress,
                    initialCategory = args.selected ?: "All",
                    onAppClick = { app -> backStack.add(NavScreen.Detail(app.packageName)) },
                )
            }

            entry<NavScreen.Updates> {
                UpdatesRoute(
                    onOpenDetails = { pkg -> backStack.add(NavScreen.Detail(pkg)) }
                )
            }

            entry<NavScreen.Settings> {
                SettingsScreen(vm = settingsViewModel)
            }

            entry<NavScreen.Detail> { args ->
                AppDetailRoute(
                    pkg = args.pkg,
                    onOpenCategory = { cat ->
                        backStack.add(NavScreen.Categories(selected = cat))
                    }
                )
            }

            entry<NavScreen.Favorites> {
                FavoritesRoute(
                    onOpenDetails = { pkg -> backStack.add(NavScreen.Detail(pkg)) }
                )
            }
        }
    )
}

@Serializable
sealed interface NavScreen : NavKey {

    @Serializable
    data object Browse : NavScreen

    @Serializable
    data class Categories(val selected: String? = "All") : NavScreen

    @Serializable
    data object Updates : NavScreen

    @Serializable
    data object Settings : NavScreen

    @Serializable
    data class Detail(val pkg: String) : NavScreen

    @Serializable
    data object Favorites : NavScreen
}