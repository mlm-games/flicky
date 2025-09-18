package app.flicky

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import app.flicky.data.local.AppDatabase
import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepositoryEntity
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.remote.DbHttpClientProvider
import app.flicky.data.remote.DbMirrorPolicyProvider
import app.flicky.data.remote.FDroidApi
import app.flicky.data.remote.HttpClientProvider
import app.flicky.data.remote.MirrorPolicyProvider
import app.flicky.data.remote.MirrorRegistry
import app.flicky.data.remote.MirrorStateStore
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.RepoHeadersStore
import app.flicky.data.repository.RepositorySyncManager
import app.flicky.data.repository.SettingsRepository
import app.flicky.install.Installer
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppGraph {
    @Volatile
    private var INSTANCE: AppGraphInstance? = null
    private val LOCK = Any()

    private class AppGraphInstance(context: Context) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val seedMutex = Mutex()
        private var hasSeeded = false

        val db: AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "flicky.db"
        )
            .fallbackToDestructiveMigration(true)
            // Only use onCreate callback for initial seeding
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    scope.launch { seedDefaultRepositoriesOnce() }
                }
            })
            .build()

        init {
            // Check if we need to seed after a destructive migration
            scope.launch {
                seedDefaultRepositoriesOnce()
            }
        }

        // Thread-safe seeding with proper transaction handling
        private suspend fun seedDefaultRepositoriesOnce() {
            seedMutex.withLock {
                if (hasSeeded) return

                db.withTransaction {
                    val repoDao = db.repositoryDao()
                    val cfgDao = db.repoConfigDao()

                    // Check if already seeded
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

        val settings = SettingsRepository(context.applicationContext, db.repositoryDao(), db.repoConfigDao(), db.appDao())
        val mirrorPolicyProvider: MirrorPolicyProvider = DbMirrorPolicyProvider(db.repoConfigDao())
        val httpClients: HttpClientProvider = DbHttpClientProvider(db.repoConfigDao())
        val api = FDroidApi(context.applicationContext, httpClients)
        val headersStore = RepoHeadersStore(settings)
        val syncManager = RepositorySyncManager(api, db.appDao(), settings, headersStore)
        val appRepo = AppRepository(db.appDao())
        val installer = Installer(context.applicationContext, settings, mirrorPolicyProvider, httpClients)
        val installedRepo = InstalledAppsRepository(context.applicationContext)
    }

    private fun getInstance(context: Context): AppGraphInstance {
        return INSTANCE ?: synchronized(LOCK) {
            INSTANCE ?: AppGraphInstance(context).also { INSTANCE = it }
        }
    }

    val db: AppDatabase get() = getInstance(appContext).db
    val settings: SettingsRepository get() = getInstance(appContext).settings
    val api: FDroidApi get() = getInstance(appContext).api
    val headersStore: RepoHeadersStore get() = getInstance(appContext).headersStore
    val syncManager: RepositorySyncManager get() = getInstance(appContext).syncManager
    val appRepo: AppRepository get() = getInstance(appContext).appRepo
    val mirrorPolicyProvider: MirrorPolicyProvider get() = getInstance(appContext).mirrorPolicyProvider
    val httpClients: HttpClientProvider get() = getInstance(appContext).httpClients
    val installer: Installer get() = getInstance(appContext).installer
    val installedRepo: InstalledAppsRepository get() = getInstance(appContext).installedRepo

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            MirrorRegistry.setStateStore(MirrorStateStore(appContext))
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearAllCaches(context: Context = appContext) {
        syncManager.cancelCurrentSync()

        db.withTransaction {
            db.appDao().clear()
            db.appDao().clearVariants()
            db.repositoryDao().clearAll()
        }
        headersStore.clear()

        runCatching {
            settings.repositoriesFlow.first().forEach { MirrorRegistry.clear(it.url) }
        }

        runCatching {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }

        // APK download cache
        runCatching { installer.clearDownloadCache() }
    }
}