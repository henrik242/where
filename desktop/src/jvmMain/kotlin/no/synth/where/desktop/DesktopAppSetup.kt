package no.synth.where.desktop

import no.synth.where.data.routing.HikingRouteSession
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.synth.where.BuildInfo
import no.synth.where.data.ClientIdManager
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.MapCacheConfig
import no.synth.where.data.MapCacheLogWatcher
import no.synth.where.data.OfflineCoverage
import no.synth.where.data.OfflineMapGate
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.OnlineTrackingCoordinator
import no.synth.where.data.PlatformFile
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.StravaApiClient
import no.synth.where.data.StravaRouteImporter
import no.synth.where.data.StravaTokenManager
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.createDataStore
import no.synth.where.data.createDefaultHttpClient
import no.synth.where.data.db.SqlDelightSavedPointDao
import no.synth.where.data.db.SqlDelightTrackDao
import no.synth.where.data.db.WhereDatabase
import no.synth.where.data.desktopDataDir
import no.synth.where.util.CrashReporter
import java.io.File

/**
 * Desktop dependency holder, the JVM twin of the iOS `AppDependencies`/`startApp`. There is no
 * location source on the desktop, so there is no location tracker: the app is a viewer that can
 * also follow others' live tracks (online tracking), not record or share its own position.
 */
object AppDependencies {
    lateinit var trackRepository: TrackRepository
    lateinit var savedPointsRepository: SavedPointsRepository
    lateinit var userPreferences: UserPreferences
    lateinit var clientIdManager: ClientIdManager
    lateinit var liveTrackingFollower: LiveTrackingFollower
    lateinit var onlineTrackingCoordinator: OnlineTrackingCoordinator
    lateinit var stravaTokenManager: StravaTokenManager
    lateinit var stravaRouteImporter: StravaRouteImporter
    lateinit var hikingRouteSession: HikingRouteSession
}

fun startApp() {
    val dataDir = desktopDataDir()

    // Adopt the same database file name the mobile apps use, under ~/.where. Passing the schema lets
    // the JDBC driver create it on a fresh desktop install and migrate an older file forward (it
    // tracks the version in SQLite's user_version). Foreign keys on, for Room/mobile parity.
    val dbFile = File(dataDir, "where_database")
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", java.util.Properties(), WhereDatabase.Schema)
    driver.execute(null, "PRAGMA foreign_keys = ON", 0)
    val database = WhereDatabase(driver)

    AppDependencies.trackRepository = TrackRepository(PlatformFile(dataDir.absolutePath), SqlDelightTrackDao(database))
    AppDependencies.savedPointsRepository = SavedPointsRepository(PlatformFile(dataDir.absolutePath), SqlDelightSavedPointDao(database))
    AppDependencies.userPreferences = UserPreferences(createDataStore("user_prefs"))
    AppDependencies.clientIdManager = ClientIdManager(createDataStore("client_prefs"))
    AppDependencies.liveTrackingFollower = LiveTrackingFollower(AppDependencies.userPreferences.trackingServerUrl.value)
    val stravaHttpClient = createDefaultHttpClient()
    AppDependencies.stravaTokenManager = StravaTokenManager(AppDependencies.userPreferences, stravaHttpClient)
    AppDependencies.stravaRouteImporter = StravaRouteImporter(
        StravaApiClient(stravaHttpClient), AppDependencies.stravaTokenManager, AppDependencies.trackRepository
    )

    // The map cache (ambient tiles + downloaded packs) lives in ~/.where, which is durable.
    OfflineTileReader.init(PlatformFile(dataDir.absolutePath))
    OfflineCoverage.cacheDir = PlatformFile(dataDir.absolutePath)
    MapCacheLogWatcher.install()
    OfflineMapGate.configure(File(dataDir, "maplibre-cache.db").absolutePath)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    AppDependencies.hikingRouteSession = HikingRouteSession(AppDependencies.trackRepository, appScope)
    appScope.launch {
        AppDependencies.userPreferences.offlineModeEnabled.collect {
            OfflineTileReader.offlineOnly = it
            OfflineMapGate.enabled = it
        }
    }
    appScope.launch {
        runCatching { OfflineCoverage.setMaxAmbientCacheSize(MapCacheConfig.ambientCacheSizeBytes) }
    }

    CrashReporter.setEnabled(AppDependencies.userPreferences.crashReportingEnabled.value)

    AppDependencies.onlineTrackingCoordinator = OnlineTrackingCoordinator(
        sources = OnlineTrackingCoordinator.Sources(
            isRecording = AppDependencies.trackRepository.isRecording,
            liveShareUntilMillis = AppDependencies.userPreferences.liveShareUntilMillis,
            onlineTrackingEnabled = AppDependencies.userPreferences.onlineTrackingEnabled,
            offlineModeEnabled = AppDependencies.userPreferences.offlineModeEnabled,
            trackingServerUrl = AppDependencies.userPreferences.trackingServerUrl,
            currentTrack = AppDependencies.trackRepository.currentTrack,
            onViewerCountChanged = { AppDependencies.userPreferences.updateViewerCount(it) },
        ),
        getClientId = { AppDependencies.clientIdManager.getClientId() },
        trackingHint = BuildInfo.TRACKING_HINT,
        parentScope = appScope,
    )
    appScope.launch { AppDependencies.onlineTrackingCoordinator.start() }
}
