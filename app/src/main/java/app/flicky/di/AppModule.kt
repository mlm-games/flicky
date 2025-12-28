package app.flicky.di

import app.flicky.AppGraph
import app.flicky.ui.components.snackbar.SnackbarManager
import app.flicky.viewmodel.AppDetailViewModel
import app.flicky.viewmodel.BrowseViewModel
import app.flicky.viewmodel.SettingsViewModel
import app.flicky.viewmodel.UpdatesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    single { AppGraph.settings }
    single { AppGraph.appRepo }
    single { AppGraph.syncManager }
    single { AppGraph.installer }
    single { AppGraph.installedRepo }
    single { AppGraph.db.appDao() }

    single { SnackbarManager() }

    viewModel {
        BrowseViewModel(
            repo = get(),
            sync = get(),
            settings = get(),
        )
    }

    viewModel {
        SettingsViewModel(repo = get())
    }

    viewModel {
        UpdatesViewModel(
            repo = get(),
            installedRepo = get(),
            installer = get(),
        )
    }

    viewModel { (pkg: String) ->
        AppDetailViewModel(
            dao = get(),
            installedRepo = get(),
            installer = get(),
            settings = get(),
            packageName = pkg
        )
    }
}