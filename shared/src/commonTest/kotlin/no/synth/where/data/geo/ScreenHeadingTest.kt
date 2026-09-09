package no.synth.where.data.geo

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Postures are described by where each device axis points in the world, which is exactly what the
 * columns of a device-to-world rotation matrix hold. World axes are east, north and up.
 */
class ScreenHeadingTest {

    private val east = floatArrayOf(1f, 0f, 0f)
    private val west = floatArrayOf(-1f, 0f, 0f)
    private val north = floatArrayOf(0f, 1f, 0f)
    private val south = floatArrayOf(0f, -1f, 0f)
    private val up = floatArrayOf(0f, 0f, 1f)
    private val down = floatArrayOf(0f, 0f, -1f)
    private val halfRoot2 = (1.0 / sqrt(2.0)).toFloat()

    /** Row-major device-to-world matrix from the world directions the device axes point at. */
    private fun posture(deviceX: FloatArray, deviceY: FloatArray, deviceZ: FloatArray) = floatArrayOf(
        deviceX[0], deviceY[0], deviceZ[0],
        deviceX[1], deviceY[1], deviceZ[1],
        deviceX[2], deviceY[2], deviceZ[2],
    )

    private fun heading(matrix: FloatArray, displayRotationDegrees: Int = 0) =
        screenHeadingDegrees(matrix, displayRotationDegrees)

    @Test
    fun uprightPhoneFollowsTheDirectionItsBackFaces() {
        // Portrait, screen vertical facing the holder, back towards north: heading north.
        assertEquals(0.0, heading(posture(deviceX = east, deviceY = up, deviceZ = south)), 0.001)
        // Same grip turned to face east.
        assertEquals(90.0, heading(posture(deviceX = south, deviceY = up, deviceZ = west)), 0.001)
        // ...and west, to prove the whole circle is covered rather than just the north axis.
        assertEquals(270.0, heading(posture(deviceX = north, deviceY = up, deviceZ = east)), 0.001)
    }

    @Test
    fun phoneOnItsSideStillReadsTheDirectionItPointsAt() {
        // The regression this function exists for: screen vertical but rolled 90 degrees, so the
        // top edge lies horizontal (pointing west) while the back still faces north. MapLibre's
        // stock engine mistakes this for a flat phone and returns the top edge -- west, a heading
        // 90 degrees out that still tracks every turn.
        val onItsSide = posture(deviceX = up, deviceY = west, deviceZ = south)
        assertEquals(0.0, heading(onItsSide), 0.001)
        // Rolled the other way, the top edge points east; the answer must not follow it.
        assertEquals(0.0, heading(posture(deviceX = down, deviceY = east, deviceZ = south)), 0.001)
    }

    @Test
    fun flatPhoneFollowsTheTopOfTheScreen() {
        assertEquals(0.0, heading(posture(deviceX = east, deviceY = north, deviceZ = up)), 0.001)
        assertEquals(90.0, heading(posture(deviceX = south, deviceY = east, deviceZ = up)), 0.001)
    }

    @Test
    fun flatPhoneAccountsForDisplayRotation() {
        // Lying flat with the top edge north: a landscape display puts the natural right edge --
        // east -- at the top of what the user reads.
        val flatFacingNorth = posture(deviceX = east, deviceY = north, deviceZ = up)
        assertEquals(90.0, heading(flatFacingNorth, displayRotationDegrees = 90), 0.001)
        assertEquals(180.0, heading(flatFacingNorth, displayRotationDegrees = 180), 0.001)
        assertEquals(270.0, heading(flatFacingNorth, displayRotationDegrees = 270), 0.001)
        // Rotations outside 0..359 normalize instead of falling through to portrait.
        assertEquals(270.0, heading(flatFacingNorth, displayRotationDegrees = -90), 0.001)
    }

    @Test
    fun tiltedReadingPostureAgreesWithBothReferences() {
        // Held in a lap at 45 degrees, top edge tilted up and away towards north. The back of the
        // phone points down and north, the top edge up and north: both references read north, so
        // crossing the flat/upright threshold cannot make the map jump.
        val tilted = posture(
            deviceX = east,
            deviceY = floatArrayOf(0f, halfRoot2, halfRoot2),
            deviceZ = floatArrayOf(0f, -halfRoot2, halfRoot2),
        )
        assertEquals(0.0, heading(tilted), 0.001)
    }

    @Test
    fun theBreakdownShowsBothReferencesAndTheChoice() {
        // Upright, back towards north, top edge towards the sky: the reading in use is the back's,
        // and the screen-up reference is the degenerate one a vertical screen cannot use.
        val upright = posture(deviceX = east, deviceY = up, deviceZ = south)
        val breakdown = headingBreakdown(upright, 0)
        assertEquals(false, breakdown.screenIsFlat)
        assertEquals(0.0, breakdown.backOfDeviceDegrees, 0.001)
        assertEquals(breakdown.backOfDeviceDegrees, breakdown.headingDegrees, 0.001)

        // Flat, top edge north: now the screen-up reference is the one in use.
        val flat = headingBreakdown(posture(deviceX = east, deviceY = north, deviceZ = up), 0)
        assertEquals(true, flat.screenIsFlat)
        assertEquals(0.0, flat.screenUpDegrees, 0.001)
        assertEquals(flat.screenUpDegrees, flat.headingDegrees, 0.001)
    }

    @Test
    fun theFlatReferenceMatchesTheStandardAzimuth() {
        // With the screen-up axis, this function reduces to atan2(R[1], R[4]) -- exactly what
        // SensorManager.getOrientation reports as azimuth. Anything else means a transposed
        // convention, which these postures alone could not catch.
        val m = posture(deviceX = south, deviceY = east, deviceZ = up)
        val expected = ((kotlin.math.atan2(m[1].toDouble(), m[4].toDouble()) * 180.0 / kotlin.math.PI) + 360.0) % 360.0
        assertEquals(expected, headingBreakdown(m, 0).screenUpDegrees, 0.001)
    }

    @Test
    fun headingsAreNormalizedToOneTurn() {
        // Back facing just west of north: expressed as 359-ish rather than a negative angle.
        val nearlyNorth = posture(
            deviceX = east,
            deviceY = up,
            deviceZ = floatArrayOf(0.1f, -0.995f, 0f),
        )
        val degrees = heading(nearlyNorth)
        assertEquals(354.3, degrees, 0.1)
    }
}
