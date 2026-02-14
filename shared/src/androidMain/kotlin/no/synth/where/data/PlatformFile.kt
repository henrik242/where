package no.synth.where.data

import java.io.File

actual class PlatformFile(internal val file: File) {
    actual fun exists(): Boolean = file.exists()
    actual fun readText(): String = file.readText()
    actual fun renameTo(dest: PlatformFile): Boolean = file.renameTo(dest.file)
    actual fun resolve(child: String): PlatformFile = PlatformFile(File(file, child))
    actual fun lastModified(): Long = file.lastModified()
    actual fun writeBytes(bytes: ByteArray) = file.writeBytes(bytes)
    actual fun length(): Long = file.length()
}
