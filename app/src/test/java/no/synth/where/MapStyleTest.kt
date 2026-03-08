package no.synth.where

import no.synth.where.data.MapStyle
import no.synth.where.data.Region
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.ui.map.MapLayer
import org.junit.Test
import org.junit.Assert.*

class MapStyleTest {

    private val regions = listOf(
        sampleRegion("Oslo", 60.0, 59.8, 11.0, 10.4),
        sampleRegion("Vestland", 61.0, 60.6, 5.5, 4.7),
        sampleRegion("Trøndelag", 64.3, 63.5, 11.0, 8.4)
    )

    private fun sampleRegion(
        name: String,
        north: Double,
        south: Double,
        east: Double,
        west: Double
    ): Region {
        val bounds = LatLngBounds.from(north, east, south, west)
        val polygon = listOf(
            listOf(
                LatLng(north, west),
                LatLng(south, west),
                LatLng(south, east),
                LatLng(north, east),
                LatLng(north, west)
            )
        )
        return Region(name, bounds, polygon)
    }

    @Test
    fun testStyleJsonIsValid() {
        val styleJson = MapStyle.getStyle(regions = regions)

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
        val styleJson = MapStyle.getStyle(regions = regions)

        assertTrue("Should contain Oslo", styleJson.contains("\"Oslo\""))
        assertTrue("Should contain Vestland", styleJson.contains("\"Vestland\""))
        assertTrue("Should contain Trøndelag", styleJson.contains("\"Trøndelag\""))

        assertTrue("Should contain coordinates array", styleJson.contains("\"coordinates\""))
    }

    @Test
    fun testOnlySelectedSourceIncluded() {
        val styleJson = MapStyle.getStyle(selectedLayer = MapLayer.OSM, regions = regions)

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
            showCountyBorders = true,
            regions = regions
        )
        assertTrue("Should contain waymarkedtrails source", style.contains("\"waymarkedtrails\""))
        assertTrue("Should contain avalanchezones source", style.contains("\"avalanchezones\""))
        assertTrue("Should contain regions source", style.contains("\"regions\""))
        assertTrue("Should contain waymarkedtrails layer", style.contains("\"waymarkedtrails-layer\""))
        assertTrue("Should contain avalanchezones layer", style.contains("\"avalanchezones-layer\""))
        assertTrue("Should contain regions outline", style.contains("\"regions-outline\""))
    }

    @Test
    fun testAvalancheZonesLayerOrdering() {
        val style = MapStyle.getStyle(
            showWaymarkedTrails = true,
            showAvalancheZones = true,
            showCountyBorders = true,
            regions = regions
        )
        // Avalanche zones (semi-transparent) should render below waymarked trails (opaque)
        val avalancheIdx = style.indexOf("avalanchezones-layer")
        val waymarkedIdx = style.indexOf("waymarkedtrails-layer")
        val regionsIdx = style.indexOf("regions-outline")
        assertTrue("Avalanche zones should appear before waymarked trails", avalancheIdx < waymarkedIdx)
        assertTrue("Waymarked trails should appear before regions", waymarkedIdx < regionsIdx)
    }

    @Test
    fun testAvalancheZonesOpacity() {
        val style = MapStyle.getStyle(showAvalancheZones = true)
        // Verify the opacity is set to 0.6 for readability
        assertTrue("Avalanche layer opacity should be 0.6", style.contains("0.6"))
    }
}
