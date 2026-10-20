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
    follower.follow(userPreferences.followedClientIds.value, userPreferences.clientNicknames.value)
    return true
}

fun unfollow(userPreferences: UserPreferences, follower: LiveTrackingFollower, clientId: String) {
    userPreferences.removeFollowedClientId(clientId)
    // Keeps the id in the friends list, so it can be switched back on.
    userPreferences.addFollowHistoryEntry(clientId)
    follower.follow(userPreferences.followedClientIds.value, userPreferences.clientNicknames.value)
}

/** Sets (or clears, when [nickname] is blank) a client's local nickname; relabels the map if followed. */
fun setNickname(
    userPreferences: UserPreferences,
    follower: LiveTrackingFollower,
    clientId: String,
    nickname: String
) {
    userPreferences.setClientNickname(clientId, nickname)
    val followed = userPreferences.followedClientIds.value
    if (clientId in followed) follower.follow(followed, userPreferences.clientNicknames.value)
}

/** Drops a client from the friends list: unfollows it and clears its history entry and nickname. */
fun forgetClient(userPreferences: UserPreferences, follower: LiveTrackingFollower, clientId: String) {
    if (clientId in userPreferences.followedClientIds.value) {
        userPreferences.removeFollowedClientId(clientId)
        follower.follow(userPreferences.followedClientIds.value, userPreferences.clientNicknames.value)
    }
    userPreferences.removeFollowHistoryEntry(clientId)
    userPreferences.setClientNickname(clientId, "")
}

fun stopFollowingAll(userPreferences: UserPreferences, follower: LiveTrackingFollower) {
    userPreferences.clearFollowedClientIds()
    follower.stopFollowing()
}

/** A group link shows that group, so it replaces the followed set rather than appending to it. */
fun followGroupLink(
    userPreferences: UserPreferences,
    follower: LiveTrackingFollower,
    clientIds: List<String>,
    selfClientId: String
) {
    userPreferences.setFollowedClientIds(clientIds.filter { it != selfClientId })
    follower.follow(userPreferences.followedClientIds.value, userPreferences.clientNicknames.value)
}
