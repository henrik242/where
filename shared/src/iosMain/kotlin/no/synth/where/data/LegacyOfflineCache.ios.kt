package no.synth.where.data

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object LegacyOfflineCache {
    // MLNOfflineStorage kept its database under Application Support/.mapbox/.
    private fun mapboxDir(): String? {
        val support = NSFileManager.defaultManager
            .URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL
        val base = support?.path ?: return null
        return "$base/.mapbox"
    }

    actual fun exists(): Boolean {
        val dir = mapboxDir() ?: return false
        return NSFileManager.defaultManager.fileExistsAtPath(dir)
    }

    actual fun deleteAll() {
        val dir = mapboxDir() ?: return
        NSFileManager.defaultManager.removeItemAtPath(dir, null)
    }
}
