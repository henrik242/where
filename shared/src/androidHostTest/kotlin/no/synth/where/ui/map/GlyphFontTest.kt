package no.synth.where.ui.map

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the two map renderers as text, because a symbol layer cannot be built off-device on either
 * platform: MapLibre's Android layers are JNI-bound and the iOS ones are Swift. A text layer that
 * leaves the font unset asks for MapLibre's default "Open Sans Regular" stack, which is not bundled,
 * and then draws nothing - on iOS it takes every other layer on the same source down with it.
 */
class GlyphFontTest {

    private val repoRoot = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(repoRoot, path).readText()

    private fun count(text: String, needle: String) = text.split(needle).size - 1

    @Test
    fun everyAndroidTextLayerPinsTheBundledFont() {
        val kotlin = source("shared/src/androidMain/kotlin/no/synth/where/ui/map/MapRenderUtils.kt")
        val textLayers = count(kotlin, "PropertyFactory.textField(")
        assertTrue(textLayers > 0, "no text layers found - has the renderer moved?")
        assertEquals(
            textLayers,
            count(kotlin, "PropertyFactory.textFont(GLYPH_FONTS)"),
            "every layer with a text field must pin GLYPH_FONTS"
        )
    }

    @Test
    fun everyIosTextLayerPinsTheBundledFont() {
        val swift = source("iosApp/Where/MapViewFactory.swift")
        val textLayers = count(swift, ".text = NSExpression")
        assertTrue(textLayers > 0, "no text layers found - has the renderer moved?")
        assertEquals(
            textLayers,
            count(swift, ".textFontNames = glyphFontNames"),
            "every layer with a text field must pin glyphFontNames"
        )
    }
}
