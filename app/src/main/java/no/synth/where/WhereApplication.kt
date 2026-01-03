package no.synth.where

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.storage.FileSource
import java.io.File

class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()

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

