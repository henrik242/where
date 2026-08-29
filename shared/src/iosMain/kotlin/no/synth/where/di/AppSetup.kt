package no.synth.where.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.synth.where.BuildInfo
import no.synth.where.data.ClientIdManager
import no.synth.where.data.LiveTrackingFollower
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
import no.synth.where.data.db.getDatabaseBuilder
import no.synth.where.location.IosLocationTracker
import no.synth.where.util.CrashReporter
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

object AppDependencies {
    lateinit var trackRepository: TrackRepository
    lateinit var savedPointsRepository: SavedPointsRepository
    lateinit var userPreferences: UserPreferences
    lateinit var clientIdManager: ClientIdManager
    lateinit var liveTrackingFollower: LiveTrackingFollower
    lateinit var onlineTrackingCoordinator: OnlineTrackingCoordinator
    lateinit var stravaTokenManager: StravaTokenManager
    lateinit var stravaRouteImporter: StravaRouteImporter
    lateinit var locationTracker: IosLocationTracker
}

fun startApp() {
    val database = getDatabaseBuilder().build()

    val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val documentsDir = requireNotNull((paths.first() as NSURL).path) { "Documents directory not found" }

    AppDependencies.trackRepository = TrackRepository(PlatformFile(documentsDir), database.trackDao())
    AppDependencies.savedPointsRepository = SavedPointsRepository(PlatformFile(documentsDir), database.savedPointDao())
    AppDependencies.userPreferences = UserPreferences(createDataStore("user_prefs"))
    AppDependencies.clientIdManager = ClientIdManager(createDataStore("client_prefs"))
    AppDependencies.liveTrackingFollower = LiveTrackingFollower(AppDependencies.userPreferences.trackingServerUrl.value)
    val stravaHttpClient = createDefaultHttpClient()
    AppDependencies.stravaTokenManager = StravaTokenManager(AppDependencies.userPreferences, stravaHttpClient)
    AppDependencies.stravaRouteImporter = StravaRouteImporter(
        StravaApiClient(stravaHttpClient), AppDependencies.stravaTokenManager, AppDependencies.trackRepository
    )

    val cachePaths = NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
    val cacheDir = requireNotNull((cachePaths.first() as NSURL).path) { "Caches directory not found" }
    OfflineTileReader.init(PlatformFile(cacheDir))
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    appScope.launch {
        AppDependencies.userPreferences.offlineModeEnabled.collect { OfflineTileReader.offlineOnly = it }
    }

    CrashReporter.setEnabled(AppDependencies.userPreferences.crashReportingEnabled.value)

    val prefs = AppDependencies.userPreferences
    // Read the authorization status here, on the main thread, for the cold-start check below.
    val hasAlwaysPermission =
        CLLocationManager.authorizationStatus() == kCLAuthorizationStatusAuthorizedAlways

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

    // App-scoped, not map-screen-scoped: a live share has to keep sending while the user sits on
    // the tracking or settings screen, and a CLLocationManager owned by a screen stops feeding the
    // coordinator the moment that screen is disposed. IosApp drives start/stop from
    // OnlineTrackingCoordinator.shouldTrackLocation.
    AppDependencies.locationTracker = IosLocationTracker(
        AppDependencies.trackRepository,
        AppDependencies.onlineTrackingCoordinator,
    )

    // Don't silently auto-resume live sharing on cold start without Always permission — it would
    // trigger an unsolicited Always prompt. Drop the timer; the user can re-enable explicitly.
    // The deadline has to be read straight from disk: the prefs flow is still 0 until its
    // DataStore collector has run. Start the coordinator only after that check, so it never sees
    // a restored deadline we are about to drop.
    appScope.launch {
        if (!hasAlwaysPermission && prefs.readLiveShareUntil() > 0L) prefs.stopLiveShare()
        AppDependencies.onlineTrackingCoordinator.start()
    }
}
