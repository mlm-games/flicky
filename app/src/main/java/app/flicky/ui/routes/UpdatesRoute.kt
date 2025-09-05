package app.flicky.ui.routes

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import app.flicky.AppGraph
import app.flicky.data.model.FDroidApp
import app.flicky.helper.viewModelFactory
import app.flicky.install.Installer
import app.flicky.ui.screens.UpdatesScreen
import app.flicky.viewmodel.UpdatesViewModel
import kotlinx.coroutines.launch

interface UpdatesActions {
    fun updateAll()
    fun updateOne(app: FDroidApp)
    fun openDetails(app: FDroidApp)
    fun ignoreThisVersion(app: FDroidApp)
    fun ignoreAll(app: FDroidApp)
    fun stopIgnoring(app: FDroidApp)
}

@Composable
fun UpdatesRoute(
    vm: UpdatesViewModel = viewModel(factory = viewModelFactory {
        UpdatesViewModel(
            AppGraph.appRepo,
            AppGraph.installedRepo,
            AppGraph.installer
        )
    }),
    installer: Installer = AppGraph.installer,
    onOpenDetails: (String) -> Unit
) {
    val ui by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()

    val actions = remember(vm, installer, ui) {
        object : UpdatesActions {
            override fun updateAll() {
                scope.launch {
                    for (app in ui.updates) {
                        installer.install(app)
                    }
                }
            }
            override fun updateOne(app: FDroidApp) { scope.launch { installer.install(app) } }
            override fun openDetails(app: FDroidApp) = onOpenDetails(app.packageName)
            override fun ignoreThisVersion(app: FDroidApp) = vm.ignoreThisVersion(app.packageName, app.versionCode.toLong())
            override fun ignoreAll(app: FDroidApp) = vm.ignoreAllUpdates(app.packageName)
            override fun stopIgnoring(app: FDroidApp) = vm.stopIgnoring(app.packageName)
        }
    }

    UpdatesScreen(ui = ui, actions = actions)
}