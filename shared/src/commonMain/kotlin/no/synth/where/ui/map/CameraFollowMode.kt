package no.synth.where.ui.map

/**
 * How the camera tracks the user location, cycled by tapping the my-location FAB.
 *
 * - [OFF]: free pan, camera does not follow.
 * - [FOLLOW]: camera stays centered on the puck, north up.
 * - [FOLLOW_HEADING]: camera stays centered and rotates to the device compass heading,
 *   so the direction you are facing points up.
 *
 * Panning or rotating the map by hand drops back to [OFF] (handled per platform).
 */
enum class CameraFollowMode {
    OFF,
    FOLLOW,
    FOLLOW_HEADING;

    fun next(): CameraFollowMode = when (this) {
        OFF -> FOLLOW
        FOLLOW -> FOLLOW_HEADING
        FOLLOW_HEADING -> OFF
    }
}
