package no.synth.where.compat

import android.view.WindowInsets

/** Target of `SystemOverlaysGuard` in app/build.gradle.kts. */
object WindowInsetsTypeCompat {
    @JvmStatic
    fun systemOverlays(): Int = try {
        WindowInsets.Type.systemOverlays()
    } catch (_: NoSuchMethodError) {
        0
    }
}
