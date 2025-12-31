package no.synth.where

import android.app.Application
import org.maplibre.android.MapLibre

class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize MapLibre
        MapLibre.getInstance(this)
    }
}

