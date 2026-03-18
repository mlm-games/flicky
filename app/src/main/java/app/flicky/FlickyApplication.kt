package app.flicky

import android.app.Application
import app.flicky.data.repository.SettingsRepository
import app.flicky.di.appModule
import app.flicky.migration.PreferencesMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        }
    }
}
