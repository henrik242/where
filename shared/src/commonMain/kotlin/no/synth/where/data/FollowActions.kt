package no.synth.where.data

/**
 * The follow/unfollow operations both platforms need: update the stored set, then point the
 * follower at it. Returns false when [clientId] was rejected (invalid, yourself, already
 * followed, or the set is full).
 */
fun startFollowing(
    userPreferences: UserPreferences,
    follower: LiveTrackingFollower,
    clientId: String,
    selfClientId: String
): Boolean {
    if (!LiveTrackingFollower.CLIENT_ID_REGEX.matches(clientId)) return false
    if (clientId == selfClientId) return false
    if (!userPreferences.addFollowedClientId(clientId)) return false
    userPreferences.addFollowHistoryEntry(clientId)
    follower.follow(userPreferences.followedClientIds.value)
    return true
}

fun unfollow(userPreferences: UserPreferences, follower: LiveTrackingFollower, clientId: String) {
    userPreferences.removeFollowedClientId(clientId)
    follower.follow(userPreferences.followedClientIds.value)
}

fun stopFollowingAll(userPreferences: UserPreferences, follower: LiveTrackingFollower) {
    userPreferences.clearFollowedClientIds()
    follower.stopFollowing()
}
