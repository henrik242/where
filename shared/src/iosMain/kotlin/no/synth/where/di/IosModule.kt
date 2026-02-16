package no.synth.where.di

import no.synth.where.data.ClientIdManager
import no.synth.where.data.PlatformFile
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.createDataStore
import no.synth.where.data.db.WhereDatabase
import no.synth.where.data.db.getDatabaseBuilder
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

val iosModule = module {
    single {
        getDatabaseBuilder().build()
    }
    single { get<WhereDatabase>().trackDao() }
    single { get<WhereDatabase>().savedPointDao() }
    single {
        val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val documentsDir = (paths.first() as NSURL).path!!
        TrackRepository(PlatformFile(documentsDir), get())
    }
    single {
        val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val documentsDir = (paths.first() as NSURL).path!!
        SavedPointsRepository(PlatformFile(documentsDir), get())
    }
    single { UserPreferences(createDataStore("user_prefs")) }
    single { ClientIdManager(createDataStore("client_prefs")) }
}
