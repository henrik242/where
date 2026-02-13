package no.synth.where

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import no.synth.where.data.UserPreferences
import no.synth.where.di.userPrefsDataStore
import org.maplibre.android.MapLibre
import org.maplibre.android.storage.FileSource
import timber.log.Timber
import java.io.File

@HiltAndroidApp
class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val prefs = UserPreferences(userPrefsDataStore)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = prefs.crashReportingEnabled.value

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
