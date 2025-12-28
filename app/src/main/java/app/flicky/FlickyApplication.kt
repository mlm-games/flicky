package app.flicky

import android.app.Application
import app.flicky.data.external.UpdatesPreferences
import app.flicky.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FlickyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // TODO: Migrate to koin
        AppGraph.init(this)

        UpdatesPreferences.init(this)

        startKoin {
            androidContext(this@FlickyApplication)
            modules(appModule)
        }
    }
}