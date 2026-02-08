package app.flicky.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import app.flicky.data.local.AppDatabase
import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepositoryEntity
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.remote.DbHttpClientProvider
import app.flicky.data.remote.DbMirrorPolicyProvider
import app.flicky.data.remote.FDroidApi
import app.flicky.data.remote.HttpClientProvider
import app.flicky.data.remote.IzzyStatsRepository
import app.flicky.data.remote.MirrorPolicyProvider
import app.flicky.data.remote.MirrorRegistry
import app.flicky.data.remote.MirrorStateStore
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    single<DataStore<Preferences>> {
        createSettingsDataStore(androidContext(), name = "flicky.settings")
    }

    single {
        val context = androidContext()
        val scope: CoroutineScope = get()
        val seedMutex = Mutex()
        var hasSeeded = false

        suspend fun seedDefaultRepositoriesOnce(db: AppDatabase) {
            seedMutex.withLock {
                if (hasSeeded) return

                db.withTransaction {
                    val repoDao = db.repositoryDao()
                    val cfgDao = db.repoConfigDao()

                    if (repoDao.getAll().isEmpty()) {
                        RepositoryInfo.defaults().forEach { def ->
                            val base = def.url.trim().removeSuffix("/")
                            repoDao.upsert(
                                RepositoryEntity(
                                    baseUrl = base,
                                    name = def.name
                                )
                            )
                            cfgDao.insertIgnore(
                                RepoConfig(
                                    baseUrl = base,
                                    enabled = def.enabled,
                                    rotateMirrors = base.equals("https://f-droid.org/repo", ignoreCase = true),
                                    strategy = if (base.equals("https://f-droid.org/repo", ignoreCase = true))
                                        "RoundRobin" else "StickyLastGood"
                                )
                            )
                        }
                    }
                }
                hasSeeded = true
            }
        }

        val db = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "flicky.db"
        )
            .fallbackToDestructiveMigration(true)
            .addCallback(object : RoomDatabase.Callback() {
            })
            .build()

        scope.launch {
            seedDefaultRepositoriesOnce(db)
        }

        MirrorRegistry.setStateStore(MirrorStateStore(context))

        db
    }

    single { get<AppDatabase>().appDao() }
    single { get<AppDatabase>().repositoryDao() }
    single { get<AppDatabase>().repoConfigDao() }

    single<MirrorPolicyProvider> { DbMirrorPolicyProvider(get()) }
    single<HttpClientProvider> { DbHttpClientProvider(get()) }
    single { FDroidApi(androidContext(), get()) }
    single { IzzyStatsRepository(httpClientProvider = get()) }

    single {
        SettingsRepository(
            dataStore = get(),
            repositoryDao = get(),
            repoConfigDao = get(),
            appDao = get()
        )
    }

    single { RepoHeadersStore(get()) }
    single { AppRepository(get()) }
    single { InstalledAppsRepository(androidContext()) }

    single {
        RepositorySyncManager(
            api = get(),
            dao = get(),
            settings = get(),
            headersStore = get()
        )
    }

    single {
        Installer(
            context = androidContext(),
            settings = get(),
            mirrorPolicies = get(),
            httpClients = get(),
            snackbarManager = get()
        )
    }

    single { SnackbarManager() }

    viewModel {
        BrowseViewModel(
            repo = get(),
            sync = get(),
            settings = get()
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
            settings = get()
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

/**
 * Helper object for accessing Koin dependencies outside of Compose. Delete later when unused (not recommended / migrate to koin)
 */
object AppDependencies {
    private var _koin: org.koin.core.Koin? = null
    private var _context: Context? = null

    fun init(koin: org.koin.core.Koin, context: Context) {
        _koin = koin
        _context = context.applicationContext
    }

    val context: Context get() = _context!!
    val db: AppDatabase get() = _koin!!.get()
    val settings: SettingsRepository get() = _koin!!.get()
    val installer: Installer get() = _koin!!.get()
    val syncManager: RepositorySyncManager get() = _koin!!.get()
    val installedRepo: InstalledAppsRepository get() = _koin!!.get()
    val appRepo: AppRepository get() = _koin!!.get()
    val mirrorPolicyProvider: MirrorPolicyProvider get() = _koin!!.get()
    val httpClients: HttpClientProvider get() = _koin!!.get()
    val headersStore: RepoHeadersStore get() = _koin!!.get()
}