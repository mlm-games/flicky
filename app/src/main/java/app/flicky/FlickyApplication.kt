package app.flicky

import android.app.Application
import app.flicky.data.repository.SettingsRepository
import app.flicky.di.appModule
import app.flicky.migration.PreferencesMigration
import app.flicky.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FlickyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val koin = startKoin {
            androidContext(this@FlickyApplication)
            modules(appModule)
        }.koin

        CoroutineScope(Dispatchers.IO).launch {
            delay(500)
            PreferencesMigration.migrateIfNeeded(
                this@FlickyApplication,
                koin.get<SettingsRepository>()
            )
            val settings = koin.get<SettingsRepository>()
            val settingsState = settings.settingsFlow.first()

            val wifiOnly = settingsState.wifiOnly
            val hours = when (settingsState.syncIntervalIndex) {
                0 -> 3; 1 -> 6; 2 -> 12; 3 -> 24; 4 -> 24 * 7; else -> -1
            }
            SyncScheduler.schedule(this@FlickyApplication, wifiOnly, hours)
        }
    }
}
