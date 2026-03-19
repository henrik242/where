package no.synth.where.data

expect class PlatformFile {
    fun exists(): Boolean
    fun readText(): String
    fun readBytes(): ByteArray
    fun renameTo(dest: PlatformFile): Boolean
    fun resolve(child: String): PlatformFile
    fun lastModified(): Long
    fun writeBytes(bytes: ByteArray)
    fun length(): Long
    fun delete(): Boolean
    fun mkdirs(): Boolean
    fun listFiles(): List<PlatformFile>
    fun isDirectory(): Boolean
}

fun PlatformFile.totalSize(): Long {
    if (!exists()) return 0L
    if (!isDirectory()) return length()
    return listFiles().sumOf { it.totalSize() }
}

fun PlatformFile.deleteRecursively() {
    if (isDirectory()) listFiles().forEach { it.deleteRecursively() }
    delete()
}
