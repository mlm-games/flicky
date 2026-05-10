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
    val reverseSort by vm.reverseSort.collectAsState()

    FavoritesScreen(
        ui = ui,
        sort = sort,
        reverseSort = reverseSort,
        onSortChange = vm::setSort,
        onReverseSortChange = vm::setReverseSort,
        onAppClick = { app -> onOpenDetails(app.packageName) },
        onRemoveFavorite = vm::removeFavorite
    )
}