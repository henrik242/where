package no.synth.where

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import no.synth.where.data.ClientIdManager
import no.synth.where.data.PlatformFile
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.db.WhereDatabase
import no.synth.where.util.CrashReporter
import org.maplibre.android.MapLibre
import org.maplibre.android.storage.FileSource
import timber.log.Timber
import java.io.File

private val Application.userPrefsDataStore by preferencesDataStore(name = "user_prefs")
private val Application.clientPrefsDataStore by preferencesDataStore(name = "client_prefs")

class WhereApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, WhereDatabase::class.java, "where_database").build()
    }
    val trackRepository by lazy { TrackRepository(PlatformFile(filesDir), database.trackDao()) }
    val savedPointsRepository by lazy { SavedPointsRepository(PlatformFile(filesDir), database.savedPointDao()) }
    val userPreferences by lazy { UserPreferences(userPrefsDataStore) }
    val clientIdManager by lazy { ClientIdManager(clientPrefsDataStore) }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        CrashReporter.setEnabled(userPreferences.crashReportingEnabled.value)

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
