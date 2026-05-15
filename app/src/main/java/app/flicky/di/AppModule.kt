package app.flicky.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import app.flicky.data.local.AppDatabase
import app.flicky.data.local.DefaultRepositorySeeder
import app.flicky.data.remote.DbHttpClientProvider
import app.flicky.data.remote.DbMirrorPolicyProvider
import app.flicky.data.remote.FDroidApi
import app.flicky.data.remote.HttpClientProvider
import app.flicky.data.remote.IzzyStatsRepository
import app.flicky.data.remote.MirrorPolicyProvider
import app.flicky.data.remote.MirrorRegistry
import app.flicky.data.remote.MirrorStateStore
import app.flicky.data.remote.ReproducibleBuildRepository
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.RepoHeadersStore
import app.flicky.data.repository.RepositorySyncManager
import app.flicky.data.repository.SettingsRepository
import app.flicky.install.Installer
import app.flicky.ui.components.snackbar.SnackbarManager
import app.flicky.viewmodel.AppDetailViewModel
import app.flicky.viewmodel.AuthorListViewModel
import app.flicky.viewmodel.BrowseViewModel
import app.flicky.viewmodel.FavoritesViewModel
import app.flicky.viewmodel.SettingsViewModel
import app.flicky.viewmodel.UpdatesViewModel
import io.github.mlmgames.settings.core.datastore.createSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    single<DataStore<Preferences>> { createSettingsDataStore(androidContext(), name = "flicky.settings") }

    single {
        val context = androidContext()
        val scope: CoroutineScope = get()

        val db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "flicky.db"
        )
            .fallbackToDestructiveMigration(true)
            .build()

        scope.launch {
            DefaultRepositorySeeder.seedIfEmpty(db)
        }

        MirrorRegistry.setStateStore(MirrorStateStore(context.applicationContext))
        db
    }

    single { get<AppDatabase>().appDao() }
    single { get<AppDatabase>().repositoryDao() }
    single { get<AppDatabase>().repoConfigDao() }

    single<MirrorPolicyProvider> { DbMirrorPolicyProvider(get()) }
    single<HttpClientProvider> { DbHttpClientProvider(get(), get()) }
    single<FDroidApi> { FDroidApi(androidContext(), get(), get(), get(), get()) }
    single<IzzyStatsRepository> { IzzyStatsRepository(httpClientProvider = get()) }
    single<ReproducibleBuildRepository> { ReproducibleBuildRepository(get()) }

    single {
        val repo = SettingsRepository(
            dataStore = get(),
            repositoryDao = get(),
            repoConfigDao = get(),
            appDao = get()
        )
        get<CoroutineScope>().launch {
            repo.migrateLegacyProxySettingsIfNeeded()
        }
        repo
    }

    single<RepoHeadersStore> { RepoHeadersStore(get()) }
    single<AppRepository> { AppRepository(get()) }
    single<InstalledAppsRepository> { InstalledAppsRepository(androidContext()) }

    single<RepositorySyncManager> {
        RepositorySyncManager(
            api = get(),
            dao = get(),
            settings = get(),
            headersStore = get(),
            db = get()
        )
    }

    single<Installer> {
        Installer(
            context = androidContext(),
            settings = get(),
            mirrorPolicies = get(),
            httpClients = get(),
            db = get()
        )
    }

    single<SnackbarManager> { SnackbarManager() }

    viewModel {
        BrowseViewModel(
            repo = get(),
            sync = get(),
            settings = get(),
            appDao = get()
        )
    }

    viewModel {
        SettingsViewModel(
            repo = get(),
            db = get(),
            syncManager = get(),
            mirrorPolicyProvider = get(),
            httpClients = get(),
            headersStore = get(),
            installer = get(),
            context = androidContext()
        )
    }

    viewModel {
        UpdatesViewModel(
            repo = get(),
            installedRepo = get(),
            installer = get(),
            settings = get(),
            appDao = get()
        )
    }

    viewModel {
        FavoritesViewModel(
            repo = get(),
            installedRepo = get(),
            settings = get()
        )
    }

    viewModel { (pkg: String) ->
        AppDetailViewModel(
            dao = get(),
            installedRepo = get(),
            installer = get(),
            settings = get(),
            rbRepo = get(),
            packageName = pkg
        )
    }

    viewModel { (authorName: String) ->
        AuthorListViewModel(
            authorName = authorName,
            appDao = get()
        )
    }
}
