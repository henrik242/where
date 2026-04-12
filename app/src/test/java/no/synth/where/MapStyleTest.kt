package no.synth.where

import no.synth.where.data.MapStyle
import no.synth.where.ui.map.MapLayer
import org.junit.Test
import org.junit.Assert.*

class MapStyleTest {

    @Test
    fun testStyleJsonIsValid() {
        val styleJson = MapStyle.getStyle()

        assertTrue("Style should start with {", styleJson.trim().startsWith("{"))
        assertTrue("Style should end with }", styleJson.trim().endsWith("}"))
        assertTrue("Style should contain version", styleJson.contains("\"version\""))
        assertTrue("Style should contain sources", styleJson.contains("\"sources\""))
        assertTrue("Style should contain layers", styleJson.contains("\"layers\""))
        assertTrue("Style should contain kartverket source", styleJson.contains("\"kartverket\""))
    }

    @Test
    fun testOnlySelectedSourceIncluded() {
        val styleJson = MapStyle.getStyle(selectedLayer = MapLayer.OSM)

        assertTrue("Should contain osm source", styleJson.contains("\"osm\""))
        assertFalse("Should not contain kartverket source", styleJson.contains("\"kartverket\""))
        assertFalse("Should not contain toporaster source", styleJson.contains("\"toporaster\""))
        assertFalse("Should not contain opentopomap source", styleJson.contains("\"opentopomap\""))
        assertFalse("Should not contain sjokartraster source", styleJson.contains("\"sjokartraster\""))
    }

    @Test
    fun testWaymarkedTrailsIncludedWhenEnabled() {
        val withTrails = MapStyle.getStyle(showWaymarkedTrails = true)
        assertTrue("Should contain waymarkedtrails", withTrails.contains("\"waymarkedtrails\""))

        val withoutTrails = MapStyle.getStyle(showWaymarkedTrails = false)
        assertFalse("Should not contain waymarkedtrails", withoutTrails.contains("\"waymarkedtrails\""))
    }

    @Test
    fun testAvalancheZonesIncludedWhenEnabled() {
        val withZones = MapStyle.getStyle(showAvalancheZones = true)
        assertTrue("Should contain avalanchezones", withZones.contains("\"avalanchezones\""))
        assertTrue("Should contain NVE tile URL", withZones.contains("gis3.nve.no"))
        assertTrue("Should contain avalanche layer", withZones.contains("\"avalanchezones-layer\""))
        assertTrue("Should set raster-opacity", withZones.contains("\"raster-opacity\""))

        val withoutZones = MapStyle.getStyle(showAvalancheZones = false)
        assertFalse("Should not contain avalanchezones", withoutZones.contains("\"avalanchezones\""))
    }

    @Test
    fun testAllOverlaysCanBeEnabledTogether() {
        val style = MapStyle.getStyle(
            showWaymarkedTrails = true,
            showAvalancheZones = true,
        )
        assertTrue("Should contain waymarkedtrails source", style.contains("\"waymarkedtrails\""))
        assertTrue("Should contain avalanchezones source", style.contains("\"avalanchezones\""))
        assertTrue("Should contain waymarkedtrails layer", style.contains("\"waymarkedtrails-layer\""))
        assertTrue("Should contain avalanchezones layer", style.contains("\"avalanchezones-layer\""))
    }

    @Test
    fun testAvalancheZonesLayerOrdering() {
        val style = MapStyle.getStyle(
            showWaymarkedTrails = true,
            showAvalancheZones = true,
        )
        val avalancheIdx = style.indexOf("avalanchezones-layer")
        val waymarkedIdx = style.indexOf("waymarkedtrails-layer")
        assertTrue("Avalanche zones should appear before waymarked trails", avalancheIdx < waymarkedIdx)
    }

    @Test
    fun testAvalancheZonesOpacity() {
        val style = MapStyle.getStyle(showAvalancheZones = true)
        // Verify the opacity is set to 0.6 for readability
        assertTrue("Avalanche layer opacity should be 0.6", style.contains("0.6"))
    }

    @Test
    fun testHillshadeIncludedWhenEnabled() {
        val withHillshade = MapStyle.getStyle(showHillshade = true)
        assertTrue("Should contain hillshade source", withHillshade.contains("\"hillshade\""))
        assertTrue("Should contain hillshade layer", withHillshade.contains("\"hillshade-layer\""))
        assertTrue("Should contain elevation-tiles-prod URL", withHillshade.contains("elevation-tiles-prod"))

        val withoutHillshade = MapStyle.getStyle(showHillshade = false)
        assertFalse("Should not contain hillshade source", withoutHillshade.contains("\"hillshade\""))
        assertFalse("Should not contain hillshade layer", withoutHillshade.contains("\"hillshade-layer\""))
    }

    @Test
    fun testHillshadeLayerOrdering() {
        val style = MapStyle.getStyle(
            showHillshade = true,
            showAvalancheZones = true,
            showWaymarkedTrails = true,
        )
        val baseIdx = style.indexOf("base-layer")
        val hillshadeIdx = style.indexOf("hillshade-layer")
        val avalancheIdx = style.indexOf("avalanchezones-layer")
        assertTrue("Hillshade should appear after base layer", hillshadeIdx > baseIdx)
        assertTrue("Hillshade should appear before avalanche zones", hillshadeIdx < avalancheIdx)
    }
}
