package no.synth.where.data

actual class PlatformFile(val path: String = "") {
    actual fun exists(): Boolean = TODO("iOS implementation")
    actual fun readText(): String = TODO("iOS implementation")
    actual fun renameTo(dest: PlatformFile): Boolean = TODO("iOS implementation")
    actual fun resolve(child: String): PlatformFile = TODO("iOS implementation")
}
