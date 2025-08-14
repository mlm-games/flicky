package app.flicky.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

object Routes {
    const val Browse = "browse"
    const val Categories = "categories"
    const val Updates = "updates"
    const val Settings = "settings"
    const val Detail = "detail/{pkg}"
    fun detail(pkg: String) = "detail/$pkg"
}

@Composable
fun FlickyNavHost(
    navController: NavHostController,
    browseContent: @Composable () -> Unit,
    categoriesContent: @Composable () -> Unit,
    updatesContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    detailContent: @Composable (String) -> Unit
) {
    NavHost(navController, startDestination = Routes.Browse) {
        composable(Routes.Browse) { browseContent() }
        composable(Routes.Categories) { categoriesContent() }
        composable(Routes.Updates) { updatesContent() }
        composable(Routes.Settings) { settingsContent() }
        composable(
            Routes.Detail,
            arguments = listOf(navArgument("pkg") { type = NavType.StringType })
        ) { backStack ->
            val pkg = backStack.arguments?.getString("pkg") ?: ""
            detailContent(pkg)
        }
    }
}