package no.synth.where.data

expect class PlatformFile {
    fun exists(): Boolean
    fun readText(): String
    fun renameTo(dest: PlatformFile): Boolean
    fun resolve(child: String): PlatformFile
    fun lastModified(): Long
    fun writeBytes(bytes: ByteArray)
    fun length(): Long
    fun delete(): Boolean
}
