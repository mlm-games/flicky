package app.flicky

import android.content.Context
import androidx.room.Room
import app.flicky.data.local.AppDatabase
import app.flicky.data.remote.FDroidApi
import app.flicky.data.repository.*
import app.flicky.install.Installer

object AppGraph {
    lateinit var db: AppDatabase
    lateinit var settings: SettingsRepository
    lateinit var api: FDroidApi
    lateinit var headersStore: RepoHeadersStore
    lateinit var syncManager: RepositorySyncManager
    lateinit var appRepo: AppRepository
    lateinit var installer: Installer
    lateinit var installedRepo: InstalledAppsRepository

    fun init(context: Context) {
        if (::db.isInitialized) return
        db = Room.databaseBuilder(context, AppDatabase::class.java, "flicky.db")
            .fallbackToDestructiveMigration(false)
            .build()
        settings = SettingsRepository(context)
        api = FDroidApi(context)
        headersStore = RepoHeadersStore(settings)
        syncManager = RepositorySyncManager(api, db.appDao(), settings, headersStore)
        appRepo = AppRepository(db.appDao())
        installer = Installer(context)
        installedRepo = InstalledAppsRepository(context)
    }
}