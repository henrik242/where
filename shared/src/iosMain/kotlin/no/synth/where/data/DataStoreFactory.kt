package no.synth.where.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

fun createDataStore(name: String): DataStore<Preferences> {
    val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val documentsDir = (paths.first() as NSURL).path!!
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { "$documentsDir/$name.preferences_pb".toPath() }
    )
}
