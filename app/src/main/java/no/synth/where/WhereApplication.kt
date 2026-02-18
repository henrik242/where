package no.synth.where

import android.app.Application
import no.synth.where.data.UserPreferences
import no.synth.where.util.CrashReporter
import no.synth.where.di.appModule
import no.synth.where.di.userPrefsDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.maplibre.android.MapLibre
import org.maplibre.android.storage.FileSource
import timber.log.Timber
import java.io.File

class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidContext(this@WhereApplication)
            modules(appModule)
        }

        val prefs = UserPreferences(userPrefsDataStore)
        CrashReporter.setEnabled(prefs.crashReportingEnabled.value)

        MapLibre.getInstance(this)

        val tilesDir = File(getExternalFilesDir(null), "maplibre-tiles")
        if (!tilesDir.exists()) {
            tilesDir.mkdirs()
        }

        FileSource.setResourcesCachePath(tilesDir.absolutePath, object : FileSource.ResourcesCachePathChangeCallback {
            override fun onSuccess(path: String) {}
            override fun onError(message: String) {}
        })
    }
}
