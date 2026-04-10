package no.synth.where.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.synth.where.data.ClientIdManager
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.PlatformFile
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.createDataStore
import no.synth.where.data.db.getDatabaseBuilder
import no.synth.where.util.CrashReporter
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

    val cachePaths = NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
    val cacheDir = requireNotNull((cachePaths.first() as NSURL).path) { "Caches directory not found" }
    OfflineTileReader.init(PlatformFile(cacheDir))
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        AppDependencies.userPreferences.offlineModeEnabled.collect { OfflineTileReader.offlineOnly = it }
    }

    CrashReporter.setEnabled(AppDependencies.userPreferences.crashReportingEnabled.value)
}
