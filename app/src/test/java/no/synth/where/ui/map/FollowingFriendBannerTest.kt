package no.synth.where.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tapping a friend's name opens their actions, each reaching the caller with that friend's id. */
@RunWith(RobolectricTestRunner::class)
class FollowingFriendBannerTest {

    @get:Rule
    val compose = createComposeRule()

    private val per = FollowedFriend("aaa111", "#FF0000", isActive = true, nickname = "Per")
    private val kari = FollowedFriend("bbb222", "#00FF00", isActive = false)

    private val shown = mutableListOf<String>()
    private val edited = mutableListOf<String>()
    private val unfollowed = mutableListOf<String>()
    private var closed = false
    private var bannerClicked = false

    private fun setContent(friends: List<FollowedFriend>) {
        compose.setContent {
            MaterialTheme {
                FollowingFriendBanner(
                    friends = friends,
                    isConnecting = false,
                    onClick = { bannerClicked = true },
                    onFriendClick = { shown += it },
                    onEditNickname = { edited += it },
                    onUnfollow = { unfollowed += it },
                    onClose = { closed = true }
                )
            }
        }
    }

    @Test
    fun showOnMapZoomsToTheTappedFriend() {
        setContent(listOf(per, kari))
        compose.onNodeWithText("bbb222").performClick()
        compose.onNodeWithText("Show on Map").performClick()
        assertEquals(listOf("bbb222"), shown)
    }

    @Test
    fun nicknameItemSaysEditWhenSetAndSetWhenNot() {
        setContent(listOf(per, kari))
        compose.onNodeWithText("Per").performClick()
        compose.onNodeWithText("Edit nickname").performClick()
        compose.onNodeWithText("bbb222").performClick()
        compose.onNodeWithText("Set nickname").performClick()
        assertEquals(listOf("aaa111", "bbb222"), edited)
    }

    @Test
    fun unfollowDropsOnlyTheTappedFriend() {
        setContent(listOf(per, kari))
        compose.onNodeWithText("Per").performClick()
        compose.onNodeWithText("Stop following Per").performClick()
        assertEquals(listOf("aaa111"), unfollowed)
    }

    @Test
    fun singleFriendIsATappableChipWithStatus() {
        setContent(listOf(per))
        compose.onNodeWithText("Live").assertExists()
        compose.onNodeWithText("Per").performClick()
        compose.onNodeWithText("Show on Map").performClick()
        assertEquals(listOf("aaa111"), shown)
    }

    @Test
    fun closeButtonStopsFollowing() {
        setContent(listOf(per, kari))
        compose.onNodeWithContentDescription("Stop Following All").performClick()
        assertTrue(closed)
    }

    @Test
    fun bannerTapIsSeparateFromNameTap() {
        setContent(listOf(per))
        compose.onNodeWithText("Following").performClick()
        assertTrue(bannerClicked)
        assertTrue(shown.isEmpty())
    }
}
