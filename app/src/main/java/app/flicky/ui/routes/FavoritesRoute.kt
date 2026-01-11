package app.flicky.ui.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.flicky.ui.screens.FavoritesScreen
import app.flicky.viewmodel.FavoritesViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun FavoritesRoute(
    vm: FavoritesViewModel = koinViewModel(),
    onOpenDetails: (String) -> Unit
) {
    val ui by vm.ui.collectAsState()
    val sort by vm.sort.collectAsState()

    FavoritesScreen(
        ui = ui,
        sort = sort,
        onSortChange = vm::setSort,
        onAppClick = { app -> onOpenDetails(app.packageName) },
        onRemoveFavorite = vm::removeFavorite
    )
}