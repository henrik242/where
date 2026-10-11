package no.synth.where.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.io.File

/** Per-user data directory for the desktop app: `~/.where`, created on first access. */
fun desktopDataDir(): File =
    File(System.getProperty("user.home"), ".where").apply { mkdirs() }

fun createDataStore(name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { File(desktopDataDir(), "$name.preferences_pb").absolutePath.toPath() }
    )
