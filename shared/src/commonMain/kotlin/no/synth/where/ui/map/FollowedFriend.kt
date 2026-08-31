package no.synth.where.ui.map

import no.synth.where.data.FriendTrack
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.data.geo.bounds

/** A followed client as the UI needs it: its map color and whether it is currently sending. */
data class FollowedFriend(
    val clientId: String,
    val color: String,
    val isActive: Boolean
)

/**
 * Colors each followed client by its position in the followed list, which is also how
 * [no.synth.where.data.FriendTrackStore] paints their track, so the two always agree.
 */
fun followedFriends(clientIds: List<String>, tracks: List<FriendTrack>): List<FollowedFriend> =
    clientIds.mapIndexed { index, clientId ->
        FollowedFriend(
            clientId = clientId,
            color = TrackColors.forIndex(index),
            isActive = tracks.any { it.clientId == clientId && it.isActive }
        )
    }

/** Bounds of everything [clientId] has sent, or of every followed friend when it is null. */
fun List<FriendTrack>.friendBounds(clientId: String? = null): LatLngBounds? =
    filter { clientId == null || it.clientId == clientId }.flatMap { it.points }.bounds()
