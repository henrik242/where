package no.synth.where.di

import no.synth.where.data.ClientIdManager
import no.synth.where.data.PlatformFile
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.createDataStore
import no.synth.where.data.db.getDatabaseBuilder
import no.synth.where.util.CrashReporter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

object AppDependencies {
    lateinit var trackRepository: TrackRepository
    lateinit var savedPointsRepository: SavedPointsRepository
    lateinit var userPreferences: UserPreferences
    lateinit var clientIdManager: ClientIdManager
}

fun startApp() {
    val database = getDatabaseBuilder().build()

    val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val documentsDir = (paths.first() as NSURL).path!!

    AppDependencies.trackRepository = TrackRepository(PlatformFile(documentsDir), database.trackDao())
    AppDependencies.savedPointsRepository = SavedPointsRepository(PlatformFile(documentsDir), database.savedPointDao())
    AppDependencies.userPreferences = UserPreferences(createDataStore("user_prefs"))
    AppDependencies.clientIdManager = ClientIdManager(createDataStore("client_prefs"))

    CrashReporter.setEnabled(AppDependencies.userPreferences.crashReportingEnabled.value)
}
