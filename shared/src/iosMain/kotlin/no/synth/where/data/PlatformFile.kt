package no.synth.where.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.stringByDeletingLastPathComponent
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class PlatformFile(val path: String = "") {
    private val fileManager get() = NSFileManager.defaultManager

    actual fun exists(): Boolean = fileManager.fileExistsAtPath(path)

    actual fun readText(): String {
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: ""
    }

    actual fun renameTo(dest: PlatformFile): Boolean {
        return fileManager.moveItemAtPath(path, dest.path, null)
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    actual fun resolve(child: String): PlatformFile {
        val nsPath = path as NSString
        return PlatformFile(nsPath.stringByAppendingPathComponent(child))
    }

    actual fun lastModified(): Long {
        val attrs = fileManager.attributesOfItemAtPath(path, null) ?: return 0L
        val date = attrs["NSFileModificationDate"] as? NSDate ?: return 0L
        return (date.timeIntervalSince1970 * 1000).toLong()
    }

    actual fun writeBytes(bytes: ByteArray) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val parentDir = (path as NSString).stringByDeletingLastPathComponent
        if (!fileManager.fileExistsAtPath(parentDir)) {
            fileManager.createDirectoryAtPath(parentDir, true, null, null)
        }
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        nsData.writeToFile(path, true)
    }

    actual fun length(): Long {
        val attrs = fileManager.attributesOfItemAtPath(path, null) ?: return 0L
        return (attrs["NSFileSize"] as? Number)?.toLong() ?: 0L
    }
}
