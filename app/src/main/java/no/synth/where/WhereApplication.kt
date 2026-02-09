package no.synth.where

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
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
