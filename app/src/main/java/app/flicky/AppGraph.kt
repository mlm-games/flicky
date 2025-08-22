package app.flicky

import android.content.Context
import androidx.room.Room
import app.flicky.data.local.AppDatabase
import app.flicky.data.remote.FDroidApi
import app.flicky.data.repository.*
import app.flicky.install.Installer

object AppGraph {
    @Volatile
    private var INSTANCE: AppGraphInstance? = null
    private val LOCK = Any()

    private class AppGraphInstance(context: Context) {
        val db: AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "flicky.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration(false)
            .build()

        val settings = SettingsRepository(context.applicationContext)
        val api = FDroidApi(context.applicationContext)
        val headersStore = RepoHeadersStore(settings)
        val syncManager = RepositorySyncManager(api, db.appDao(), settings, headersStore)
        val appRepo = AppRepository(db.appDao())
        val installer = Installer(context.applicationContext)
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
    val installer: Installer get() = getInstance(appContext).installer
    val installedRepo: InstalledAppsRepository get() = getInstance(appContext).installedRepo

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
    }
}