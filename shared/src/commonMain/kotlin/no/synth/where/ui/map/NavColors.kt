package no.synth.where.ui.map

/**
 * Navigation route-line colors, shared so Android (Kotlin) and iOS (Swift, via `import Shared` ->
 * `NavColors.shared`) can't drift apart. Remaining is a dark olive that reads over Kartverket's
 * green forest fill; the traversed leg reuses [remaining] but is drawn dotted, off-course is red.
 */
object NavColors {
    val remaining = "#556B1B"
    val offCourse = "#E53935"
}

/**
 * Navigation route-line widths, opacities, and dash gaps, shared with iOS (Swift via
 * `NavStyle.shared`) so the two renderers can't drift. Dash *patterns* are assembled per platform
 * from these gaps because MapLibre's Android and iOS dash APIs take different types.
 */
object NavStyle {
    val completedWidth = 6.0   // matches remainingWidth so the dotted traversed leg is easy to see
    val remainingWidth = 6.0
    val offCourseWidth = 3.0
    val remainingOpacity = 0.9
    val traversedOpacity = 0.7
    val traversedDashGap = 2.0   // gap between the round-cap dots of the dotted traversed leg
    val offCourseDash = 2.0      // equal on/off dash of the off-course connector
    val arrowSpacing = 48.0      // spacing of the direction arrows along the remaining line
}
