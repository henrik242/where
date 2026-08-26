package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveTrackingFollowerTest {

    @Test
    fun sanitizeDropsInvalidAndDuplicateIdsAndCapsTheSet() {
        val ids = listOf(
            "aaa111", "aaa111", "BAD", "toolongid", "", "bbb222", "ccc333", "ddd444", "eee555", "fff666"
        )
        assertEquals(
            listOf("aaa111", "bbb222", "ccc333", "ddd444", "eee555"),
            LiveTrackingFollower.sanitize(ids)
        )
        assertEquals(LiveTrackingFollower.MAX_FOLLOWED, LiveTrackingFollower.sanitize(ids).size)
    }

    @Test
    fun sanitizeKeepsFollowOrder() {
        assertEquals(
            listOf("ccc333", "aaa111"),
            LiveTrackingFollower.sanitize(listOf("ccc333", "aaa111"))
        )
    }
}
