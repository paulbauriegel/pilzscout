package de.pilzscout.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.pilzscout.app.settings.SettingsRepository
import javax.inject.Inject

@HiltAndroidApp
class MushroomApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settings.applyStoredThemeMode()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
