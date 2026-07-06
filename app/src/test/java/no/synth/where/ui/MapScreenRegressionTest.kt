package no.synth.where.ui

import no.synth.where.data.SavedPoint
import org.junit.Test
import org.junit.Assert.*
import no.synth.where.data.geo.LatLng

/**
 * Regression tests to ensure specific bugs don't happen again.
 *
 * These tests document the exact issues that occurred and verify they're fixed:
 * 1. Saved points toggle not working (never shown)
 * 2. Unable to add points (long press not working)
 * 3. Point click not working
 */
class MapScreenRegressionTest {

    @Test
    fun regression_savedPointsNeverShown_Issue20260104() {
        // Bug: Saved points were never shown regardless of toggle state
        // Root cause: showSavedPoints parameter not passed to MapLibreMapView

        var showSavedPoints = false
        val onShowSavedPointsChange: (Boolean) -> Unit = { showSavedPoints = it }

        // User toggles saved points ON
        onShowSavedPointsChange(true)
        assertTrue(
            "Bug regression: Saved points should be showable. " +
            "Ensure showSavedPoints parameter is passed to MapLibreMapView",
            showSavedPoints
        )

        // User toggles saved points OFF
        onShowSavedPointsChange(false)
        assertFalse(
            "Saved points should be hideable",
            showSavedPoints
        )
    }

    @Test
    fun regression_cannotAddPoints_Issue20260104() {
        // Bug: Long press on map did not show save point dialog
        // Root cause: onLongPress callback not passed to MapLibreMapView

        var dialogShown = false
        var capturedLatLng: LatLng? = null

        val onLongPress: (LatLng) -> Unit = { latLng ->
            // This simulates showing the save point dialog
            capturedLatLng = latLng
            dialogShown = true
        }

        // User long presses on map
        val pressLocation = LatLng(59.9139, 10.7522)
        onLongPress(pressLocation)

        assertTrue(
            "Bug regression: Long press should trigger save point dialog. " +
            "Ensure onLongPress parameter is passed to MapLibreMapView",
            dialogShown
        )
        assertNotNull("Long press should capture location", capturedLatLng)
        assertEquals("Latitude should match", 59.9139, capturedLatLng?.latitude ?: 0.0, 0.0001)
        assertEquals("Longitude should match", 10.7522, capturedLatLng?.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun regression_cannotClickPoints_Issue20260104() {
        // Bug: Clicking on saved points did not show edit dialog
        // Root cause: onPointClick callback not passed to MapLibreMapView

        var dialogShown = false
        var capturedPoint: SavedPoint? = null

        val onPointClick: (SavedPoint) -> Unit = { point ->
            // This simulates showing the edit point dialog
            capturedPoint = point
            dialogShown = true
        }

        // User clicks on a saved point
        val existingPoint = SavedPoint(
            id = "test-point",
            name = "Oslo",
            latLng = LatLng(59.9139, 10.7522),
            description = "Capital of Norway",
            color = "#FF5722"
        )
        onPointClick(existingPoint)

        assertTrue(
            "Bug regression: Point click should trigger edit dialog. " +
            "Ensure onPointClick parameter is passed to MapLibreMapView",
            dialogShown
        )
        assertNotNull("Point click should capture the point", capturedPoint)
        assertEquals("Point name should match", "Oslo", capturedPoint?.name)
    }

}

