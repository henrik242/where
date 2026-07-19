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
