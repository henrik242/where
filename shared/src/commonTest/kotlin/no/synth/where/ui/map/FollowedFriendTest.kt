package no.synth.where.ui.map

import no.synth.where.data.FriendTrack
import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FollowedFriendTest {

    @Test
    fun everyFollowedClientGetsADistinctColorInFollowOrder() {
        val ids = listOf("aaa111", "bbb222", "ccc333", "ddd444", "eee555")
        val friends = followedFriends(ids, emptyList())
        assertEquals(ids, friends.map { it.clientId })
        assertEquals(ids.size, friends.map { it.color }.toSet().size)
        assertEquals(TrackColors.forIndex(0), friends.first().color)
    }

    @Test
    fun marksWhoIsSending() {
        val ids = listOf("aaa111", "bbb222")
        val tracks = listOf(
            FriendTrack("aaa111", "t1", emptyList(), isActive = true),
            FriendTrack("bbb222", "t2", emptyList(), isActive = false),
        )
        assertEquals(listOf(true, false), followedFriends(ids, tracks).map { it.isActive })
    }

    @Test
    fun boundsCoverOneFriendOrEveryone() {
        val tracks = listOf(
            FriendTrack("aaa111", "t1", listOf(LatLng(59.0, 10.0), LatLng(59.5, 10.5)), isActive = true),
            FriendTrack("bbb222", "t2", listOf(LatLng(63.0, 11.0)), isActive = true),
        )
        val one = assertNotNull(tracks.friendBounds("aaa111"))
        assertEquals(59.5, one.north)
        val all = assertNotNull(tracks.friendBounds())
        assertEquals(63.0, all.north)
        assertNull(tracks.friendBounds("ccc333"))
    }
}
