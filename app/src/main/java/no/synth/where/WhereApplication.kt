package no.synth.where

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.synth.where.data.AndroidDownloadEngine
import no.synth.where.data.ClientIdManager
import no.synth.where.data.DownloadQueueManager
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.MapCacheConfig
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.OnlineTrackingCoordinator
import no.synth.where.data.PlatformFile
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.db.MIGRATION_1_2
import no.synth.where.data.db.WhereDatabase
import no.synth.where.util.CrashReporter
import org.maplibre.android.MapLibre
import org.maplibre.android.log.Logger
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.storage.FileSource
import timber.log.Timber
import java.io.File

private val Application.userPrefsDataStore by preferencesDataStore(name = "user_prefs")
private val Application.clientPrefsDataStore by preferencesDataStore(name = "client_prefs")

class WhereApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, WhereDatabase::class.java, "where_database")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val trackRepository by lazy { TrackRepository(PlatformFile(filesDir), database.trackDao()) }
    val savedPointsRepository by lazy { SavedPointsRepository(PlatformFile(filesDir), database.savedPointDao()) }
    val userPreferences by lazy { UserPreferences(userPrefsDataStore) }
    val clientIdManager by lazy { ClientIdManager(clientPrefsDataStore) }
    val downloadQueueManager by lazy {
        DownloadQueueManager(
            engine = AndroidDownloadEngine(this),
            // Main.immediate so onActive() (startForegroundService) runs synchronously inside the
            // foreground enqueue click, satisfying the FGS start-from-foreground requirement.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            // Start the foreground service on the first enqueue (always from the foreground UI).
            // It stops itself when the queue drains (see MapDownloadService's queue collector), so
            // we never call startService from the background (which draining while backgrounded
            // would otherwise do). onIdle is intentionally unused.
            onActive = { no.synth.where.service.MapDownloadService.start(this) },
        )
    }
    val liveTrackingFollower by lazy { LiveTrackingFollower(userPreferences.trackingServerUrl.value) }
    val onlineTrackingCoordinator by lazy {
        OnlineTrackingCoordinator(
            sources = OnlineTrackingCoordinator.Sources(
                isRecording = trackRepository.isRecording,
                liveShareUntilMillis = userPreferences.liveShareUntilMillis,
                onlineTrackingEnabled = userPreferences.onlineTrackingEnabled,
                offlineModeEnabled = userPreferences.offlineModeEnabled,
                trackingServerUrl = userPreferences.trackingServerUrl,
                currentTrack = trackRepository.currentTrack,
                onViewerCountChanged = { userPreferences.updateViewerCount(it) },
            ),
            getClientId = { clientIdManager.getClientId() },
            trackingHint = BuildInfo.TRACKING_HINT,
            parentScope = appScope,
        )
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        CrashReporter.setEnabled(userPreferences.crashReportingEnabled.value)

        Logger.setLoggerDefinition(MapLibreLogWatcher)
        MapLibre.getInstance(this)
        OfflineTileReader.init(PlatformFile(cacheDir))
        appScope.launch {
            userPreferences.offlineModeEnabled.collect { OfflineTileReader.offlineOnly = it }
        }
        onlineTrackingCoordinator.start()

        val tilesDir = File(getExternalFilesDir(null), "maplibre-tiles")
        if (!tilesDir.exists()) {
            tilesDir.mkdirs()
        }

        FileSource.setResourcesCachePath(tilesDir.absolutePath, object : FileSource.ResourcesCachePathChangeCallback {
            override fun onSuccess(path: String) {
                // Give the ambient cache (tiles auto-saved while browsing) far more room than
                // MapLibre's 50 MB default, so areas the user has panned over stay available
                // offline instead of being evicted after ~50 MB of browsing.
                OfflineManager.getInstance(this@WhereApplication)
                    .setMaximumAmbientCacheSize(MapCacheConfig.ambientCacheSizeBytes, object : OfflineManager.FileSourceCallback {
                        override fun onSuccess() {}
                        override fun onError(message: String) {}
                    })
            }
            override fun onError(message: String) {}
        })
    }
}
