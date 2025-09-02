package app.flicky.ui.routes

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import app.flicky.AppGraph
import app.flicky.helper.viewModelFactory
import app.flicky.viewmodel.AppDetailViewModel
import app.flicky.ui.screens.AppDetailScreen

@Composable
fun AppDetailRoute(
    pkg: String,
    onOpenCategory: (String) -> Unit,
    vm: AppDetailViewModel = viewModel(factory = viewModelFactory {
        AppDetailViewModel(
            dao = AppGraph.db.appDao(),
            installedRepo = AppGraph.installedRepo,
            installer = AppGraph.installer,
            packageName = pkg,
            settings = AppGraph.settings
        )
    })
) {
    val ui by vm.ui.collectAsState()
    val app = ui.app ?: return
    AppDetailScreen(
        app = app,
        installedVersionCode = ui.installedVersionCode,
        isInstalling = ui.isInstalling,
        stage = ui.stage,
        progress = ui.progress,
        onInstall = { vm.install() },
        onOpen = { vm.openApp() },
        onUninstall = { vm.uninstall() },
        error = ui.error,
        onOpenCategory = onOpenCategory
    )
}