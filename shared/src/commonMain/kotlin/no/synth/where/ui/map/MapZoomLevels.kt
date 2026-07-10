package no.synth.where.ui.map

object MapZoomLevels {
    const val SINGLE_POINT = 15.0
    const val FRIEND_MAX = 15.0
    // Zoom the camera snaps to when follow mode engages from a further-out view.
    // Referenced by Android CameraUtils.applyFollowMode and mirrored in iOS MapViewFactory.setCameraFollowMode.
    const val FOLLOW_MIN = 15.0
    const val DEFAULT_PADDING_PX = 80
}
