package no.synth.where

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration

class WhereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize osmdroid configuration
        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = "Mozilla/5.0 (Linux; Android 10; Mobile) WhereApp/1.0"
        config.additionalHttpRequestProperties["Referer"] = "https://www.norgeskart.no/"
    }
}

