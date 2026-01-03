package no.synth.where

import android.app.Application
import android.util.Log
import org.maplibre.android.MapLibre
import org.maplibre.android.log.Logger
import org.maplibre.android.storage.FileSource
import java.io.File

class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize MapLibre
        MapLibre.getInstance(this)

        // Configure permanent tile storage in external files (not cache)
        // This prevents Android from clearing tiles automatically
        val tilesDir = File(getExternalFilesDir(null), "maplibre-tiles")

        // Create the directory if it doesn't exist
        if (!tilesDir.exists()) {
            val created = tilesDir.mkdirs()
            Log.d("WhereApplication", "Created maplibre-tiles directory: $created")
        }

        FileSource.setResourcesCachePath(tilesDir.absolutePath, object : FileSource.ResourcesCachePathChangeCallback {
            override fun onSuccess(path: String) {
                Log.d("WhereApplication", "✓ MapLibre tile storage successfully set to: $path")
            }

            override fun onError(message: String) {
                Log.e("WhereApplication", "✗ Failed to set tile storage: $message")
            }
        })

        Log.d("WhereApplication", "MapLibre configured with permanent tile storage at: ${tilesDir.absolutePath}")

        // Enable MapLibre debug logging
        Logger.setVerbosity(Logger.VERBOSE)
        Log.d("WhereApplication", "MapLibre initialized - tiles will be kept perpetually in external storage")
    }
}

