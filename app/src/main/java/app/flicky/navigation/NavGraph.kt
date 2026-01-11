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
            entry<FlickyDestination.Browse> {
                val query = browseViewModel.query.collectAsStateWithLifecycle().value
                val sort = browseViewModel.sort.collectAsStateWithLifecycle().value
                val browseUi = browseViewModel.uiState.collectAsStateWithLifecycle().value

                BrowseScreen(
                    apps = browseViewModel.pagedApps.collectAsLazyPagingItems(),
                    query = query,
                    sort = sort,
                    onSortChange = browseViewModel::setSort,
                    onSearchChange = browseViewModel::setQuery,
                    onAppClick = { app -> backStack.add(FlickyDestination.Detail(app.packageName)) },
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

            entry<FlickyDestination.Categories> { args ->
                val browseUi = browseViewModel.uiState.collectAsStateWithLifecycle().value
                CategoriesScreen(
                    isSyncing = browseUi.isSyncing,
                    progress = browseUi.progress,
                    initialCategory = args.selected ?: "All",
                    onAppClick = { app -> backStack.add(FlickyDestination.Detail(app.packageName)) },
                )
            }

            entry<FlickyDestination.Updates> {
                UpdatesRoute(
                    onOpenDetails = { pkg -> backStack.add(FlickyDestination.Detail(pkg)) }
                )
            }

            entry<FlickyDestination.Settings> {
                SettingsScreen(vm = settingsViewModel)
            }

            entry<FlickyDestination.Detail> { args ->
                AppDetailRoute(
                    pkg = args.pkg,
                    onOpenCategory = { cat ->
                        backStack.add(FlickyDestination.Categories(selected = cat))
                    }
                )
            }

            entry<FlickyDestination.Favorites> {
                FavoritesRoute(
                    onOpenDetails = { pkg -> backStack.add(FlickyDestination.Detail(pkg)) }
                )
            }
        }
    )
}

@Serializable
sealed interface FlickyDestination : NavKey {

    @Serializable
    data object Browse : FlickyDestination

    @Serializable
    data class Categories(val selected: String? = "All") : FlickyDestination

    @Serializable
    data object Updates : FlickyDestination

    @Serializable
    data object Settings : FlickyDestination

    @Serializable
    data class Detail(val pkg: String) : FlickyDestination

    @Serializable
    data object Favorites : FlickyDestination
}