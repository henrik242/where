package no.synth.where

import android.app.Application
import android.util.Log
import org.maplibre.android.MapLibre
import org.maplibre.android.log.Logger

class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize MapLibre
        MapLibre.getInstance(this)

        // Enable MapLibre debug logging
        Logger.setVerbosity(Logger.VERBOSE)
        Log.d("WhereApplication", "MapLibre initialized with verbose logging")
    }
}

