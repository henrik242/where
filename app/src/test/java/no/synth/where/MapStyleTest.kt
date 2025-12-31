package no.synth.where

import no.synth.where.data.MapStyle
import org.junit.Test
import org.junit.Assert.*

class MapStyleTest {
    @Test
    fun testStyleJsonIsValid() {
        val styleJson = MapStyle.getStyle()

        println("Generated Style JSON:")
        println(styleJson)
        println("\nJSON Length: ${styleJson.length}")

        // Basic JSON validation
        assertTrue("Style should start with {", styleJson.trim().startsWith("{"))
        assertTrue("Style should end with }", styleJson.trim().endsWith("}"))
        assertTrue("Style should contain version", styleJson.contains("\"version\""))
        assertTrue("Style should contain sources", styleJson.contains("\"sources\""))
        assertTrue("Style should contain layers", styleJson.contains("\"layers\""))
        assertTrue("Style should contain kartverket source", styleJson.contains("\"kartverket\""))
        assertTrue("Style should contain regions source", styleJson.contains("\"regions\""))
    }

    @Test
    fun testRegionsGeoJson() {
        val styleJson = MapStyle.getStyle()

        // Check for region names
        assertTrue("Should contain Oslo", styleJson.contains("\"Oslo\""))
        assertTrue("Should contain Vestland", styleJson.contains("\"Vestland\""))
        assertTrue("Should contain Trøndelag", styleJson.contains("\"Trøndelag"))

        // Check for coordinates
        assertTrue("Should contain coordinates array", styleJson.contains("\"coordinates\""))
    }
}

