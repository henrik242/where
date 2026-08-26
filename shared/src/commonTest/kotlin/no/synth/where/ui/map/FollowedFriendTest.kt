package no.synth.where.ui.map

import no.synth.where.data.FriendTrack
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
