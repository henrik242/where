package no.synth.where.data.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Cosine of the tilt at which the screen counts as lying flat: within ~40 degrees of horizontal.
 * Anything more upright than that is read as "being pointed somewhere" instead.
 */
private const val FLAT_SCREEN_COS = 0.766f

/** Device-space axis pointing out through the back of the phone, where the rear camera looks. */
private val BACK_OF_DEVICE = floatArrayOf(0f, 0f, -1f)

/**
 * Compass heading of the direction that should sit at the top of the map while the camera follows
 * the device heading, in degrees clockwise from magnetic north.
 *
 * [rotationMatrix] is the 9-element row-major device-to-world matrix Android's SensorManager
 * produces (`getRotationMatrixFromVector`, or `getRotationMatrix` from gravity + magnetic field):
 * its three rows give the east, north and up components of a device-space vector.
 * [displayRotationDegrees] is how far the display is turned from its natural orientation --
 * `Surface.ROTATION_*` in degrees (0, 90, 180, 270).
 *
 * There are two postures, told apart by gravity alone:
 *
 * - Screen roughly horizontal (flat on a table, a lap, a cradle): the map reads like a paper map,
 *   so the heading is the direction the top edge of the *rendered* UI points. This is the only
 *   case that needs [displayRotationDegrees].
 * - Anything more upright, whatever its roll: the heading is the direction the back of the phone
 *   faces, which is where the holder is pointing it -- and, in a hand or a mount, the direction of
 *   travel.
 *
 * MapLibre's own LocationComponentCompassEngine picks between the same two references, but decides
 * from pitch and roll measured *after* remapping the matrix by the display rotation. A phone whose
 * physical orientation disagrees with its display rotation -- held on its side, or rotation-locked
 * -- then gets its sensor axes swapped and reports a heading 90 degrees out, while still tracking
 * every turn correctly. Reading the posture straight off the matrix's gravity row removes that
 * failure mode: the choice no longer depends on the display rotation at all.
 */
fun screenHeadingDegrees(rotationMatrix: FloatArray, displayRotationDegrees: Int): Double {
    require(rotationMatrix.size >= 9) { "rotation matrix must hold 9 elements" }
    // The bottom row holds the up components of the device axes, so its last element is how much
    // of "up" lies along the screen normal: +-1 with the screen level, 0 with the screen vertical.
    val screenNormalTowardsUp = rotationMatrix[8]
    val reference = if (abs(screenNormalTowardsUp) > FLAT_SCREEN_COS) {
        screenUpAxis(displayRotationDegrees)
    } else {
        BACK_OF_DEVICE
    }
    return bearingOf(rotationMatrix, reference)
}

/** Compass bearing of a device-space [reference] direction, in degrees clockwise from north. */
private fun bearingOf(rotationMatrix: FloatArray, reference: FloatArray): Double {
    // Rotate the reference into the world frame; only its horizontal part carries a heading.
    val east = rotationMatrix[0] * reference[0] +
        rotationMatrix[1] * reference[1] +
        rotationMatrix[2] * reference[2]
    val north = rotationMatrix[3] * reference[0] +
        rotationMatrix[4] * reference[1] +
        rotationMatrix[5] * reference[2]
    val degrees = atan2(east.toDouble(), north.toDouble()) * 180.0 / PI
    return (degrees + 360.0) % 360.0
}

/**
 * Device-space axis that points at the top of the rendered UI. Turning the device 90 degrees
 * brings the natural right edge to the top, matching the convention MapLibre's flat-device remap
 * uses.
 */
private fun screenUpAxis(displayRotationDegrees: Int): FloatArray =
    when (((displayRotationDegrees % 360) + 360) % 360) {
        90 -> floatArrayOf(1f, 0f, 0f)
        180 -> floatArrayOf(0f, -1f, 0f)
        270 -> floatArrayOf(-1f, 0f, 0f)
        else -> floatArrayOf(0f, 1f, 0f)
    }
