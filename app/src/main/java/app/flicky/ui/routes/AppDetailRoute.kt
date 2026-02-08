package app.flicky.ui.routes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flicky.viewmodel.AppDetailViewModel
import app.flicky.ui.screens.AppDetailScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AppDetailRoute(
    pkg: String,
    onOpenCategory: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    vm: AppDetailViewModel = koinViewModel(parameters = { parametersOf(pkg) })
) {
    val ui = vm.ui.collectAsStateWithLifecycle().value
    val app = ui.app

    if (app == null) {
        if (ui.appNotFound) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "App not found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        return
    }

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
        onOpenAuthor = onOpenAuthor,
        variants = ui.variants,
        isFavorite = ui.isFavorite,
        onToggleFavorite = { vm.toggleFavorite() }
    )
}