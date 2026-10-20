package no.synth.where.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NicknameDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val saved = mutableListOf<Pair<String, String>>()
    private var dismissed = false

    private fun setContent(nickname: String?) {
        compose.setContent {
            MaterialTheme {
                NicknameDialog(
                    clientId = "abc123",
                    nickname = nickname,
                    onSetNickname = { id, name -> saved += id to name },
                    onDismiss = { dismissed = true }
                )
            }
        }
    }

    @Test
    fun saveForwardsTypedNameAndDismisses() {
        setContent(nickname = null)
        compose.onNodeWithText("Set nickname").assertExists()
        compose.onNodeWithText("Nickname").performTextInput("Per")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("abc123" to "Per"), saved)
        assertTrue(dismissed)
    }

    @Test
    fun inputIsCappedAtMaxLength() {
        setContent(nickname = null)
        compose.onNodeWithText("Nickname").performTextInput("x".repeat(MAX_NICKNAME_LENGTH + 5))
        compose.onNodeWithText("Save").performClick()
        assertEquals(MAX_NICKNAME_LENGTH, saved.single().second.length)
    }

    @Test
    fun withoutNicknameThereIsNothingToRemove() {
        setContent(nickname = null)
        compose.onNodeWithText("Remove nickname").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(saved.isEmpty())
        assertTrue(dismissed)
    }

    @Test
    fun existingNicknameCanBeRemovedOrKept() {
        setContent(nickname = "Per")
        compose.onNodeWithText("Edit nickname").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
        compose.onNodeWithText("Remove nickname").performClick()
        assertEquals(listOf("abc123" to ""), saved)
    }

    @Test
    fun clearingTheFieldSavesAnEmptyName() {
        setContent(nickname = "Per")
        compose.onNodeWithText("Per").performTextClearance()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("abc123" to ""), saved)
    }
}
