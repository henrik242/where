package no.synth.where.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import no.synth.where.data.LiveTrackingFollower.Companion.MAX_FOLLOWED
import no.synth.where.ui.map.FollowedFriend
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Followed and recent friends share one list, each with a follow button and a menu to rename or forget. */
@RunWith(RobolectricTestRunner::class)
class OnlineTrackingFriendsTest {

    @get:Rule
    val compose = createComposeRule()

    private val followed = mutableListOf<String>()
    private val unfollowed = mutableListOf<String>()
    private val forgotten = mutableListOf<String>()

    private fun setContent(
        friends: List<FollowedFriend> = emptyList(),
        history: List<String> = emptyList(),
        nicknames: Map<String, String> = emptyMap(),
        idInput: String = "",
    ) {
        compose.setContent {
            MaterialTheme {
                OnlineTrackingScreenContent(
                    isTrackingEnabled = false,
                    clientId = "abc123",
                    showRegenerateDialog = false,
                    showTrackingInfoDialog = false,
                    onBackClick = {},
                    onToggleTracking = {},
                    onViewOnWeb = {},
                    onShare = {},
                    onRegenerateClick = {},
                    onConfirmRegenerate = {},
                    onDismissRegenerate = {},
                    onConfirmTrackingInfo = {},
                    onDismissTrackingInfo = {},
                    followedFriends = friends,
                    followClientIdInput = idInput,
                    followHistory = history,
                    clientNicknames = nicknames,
                    onFollow = { followed += it },
                    onUnfollow = { unfollowed += it },
                    onForget = { forgotten += it },
                )
            }
        }
    }

    private fun friend(id: String, nickname: String? = null) =
        FollowedFriend(id, "#FF0000", isActive = true, nickname = nickname)

    // The add row's Follow button is disabled while the id field is empty, so the enabled one is the row's.
    @Test
    fun buttonsFollowAndUnfollow() {
        setContent(friends = listOf(friend("aaa111", "Per")), history = listOf("xyz789"))
        compose.onNodeWithText("Unfollow").performScrollTo().performClick()
        compose.onNode(hasText("Follow") and isEnabled()).performScrollTo().performClick()
        assertEquals(listOf("aaa111"), unfollowed)
        assertEquals(listOf("xyz789"), followed)
    }

    @Test
    fun recentCannotBeFollowedAtTheLimit() {
        val friends = (1..MAX_FOLLOWED).map { friend("fol00$it") }
        setContent(friends = friends, history = listOf("xyz789"))
        compose.onNodeWithText("Follow").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun menuRenamesAndForgets() {
        setContent(nicknames = mapOf("xyz789" to "Kari"))
        compose.onNodeWithContentDescription("Actions for Kari").performScrollTo().performClick()
        compose.onNodeWithText("Edit nickname").performClick()
        compose.onNodeWithText("Remove nickname").assertExists()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithContentDescription("Actions for Kari").performClick()
        compose.onNodeWithText("Forget").performClick()
        assertEquals(listOf("xyz789"), forgotten)
    }

    @Test
    fun nicknameFieldAppearsOnceTheIdIsComplete() {
        setContent(idInput = "def45")
        compose.onNodeWithText("Nickname (optional)").assertDoesNotExist()
        compose.onNodeWithText("def45").assertExists()
    }

    @Test
    fun nicknameFieldShownForCompleteId() {
        setContent(idInput = "def456")
        compose.onNodeWithText("Nickname (optional)").assertExists()
    }
}
