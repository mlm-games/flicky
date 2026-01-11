package app.flicky.ui.routes

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flicky.viewmodel.AppDetailViewModel
import app.flicky.ui.screens.AppDetailScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AppDetailRoute(
    pkg: String,
    onOpenCategory: (String) -> Unit,
    vm: AppDetailViewModel = koinViewModel(parameters = { parametersOf(pkg) })
) {
    val ui = vm.ui.collectAsStateWithLifecycle().value
    val app = ui.app ?: return

    AppDetailScreen(
        app = app,
        installedVersionCode = ui.installedVersionCode,
        stage = ui.stage,
        onInstall = { vm.install() },
        onInstallVariant = { v -> vm.installVariant(v) },
        onOpen = { vm.openApp() },
        onCancel = { vm.cancel() },
        onUninstall = { vm.uninstall() },
        error = ui.error,
        onOpenCategory = onOpenCategory,
        variants = ui.variants,
        isFavorite = ui.isFavorite,
        onToggleFavorite = { vm.toggleFavorite() }
    )
}