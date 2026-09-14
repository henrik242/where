package no.synth.where.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The description typed while creating a point has to reach [MapDialogs.SavePointDialog]'s caller. */
@RunWith(RobolectricTestRunner::class)
class SavePointDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun descriptionInputIsForwardedToTheCaller() {
        var name by mutableStateOf("Teltplass")
        var description by mutableStateOf("")

        compose.setContent {
            MaterialTheme {
                MapDialogs.SavePointDialog(
                    pointName = name,
                    onPointNameChange = { name = it },
                    pointDescription = description,
                    onPointDescriptionChange = { description = it },
                    coordinates = "59.9139, 10.7522",
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("Description (optional)").performTextInput("flat, sheltered")

        assertEquals("flat, sheltered", description)
        assertEquals("Teltplass", name)
    }

    /**
     * Regression for the "Jobb" -> "Jbbo" caret scramble: the name field is driven by a StateFlow
     * that echoes each keystroke back a frame later, like [no.synth.where.ui.MapScreenViewModel].
     * Typing letter by letter must keep them in order.
     */
    @Test
    fun nameSurvivesAsyncStateFlowRoundTrip() {
        val nameFlow = MutableStateFlow("")

        compose.setContent {
            val name by nameFlow.collectAsState()
            MaterialTheme {
                MapDialogs.SavePointDialog(
                    pointName = name,
                    onPointNameChange = { nameFlow.value = it },
                    pointDescription = "",
                    onPointDescriptionChange = {},
                    coordinates = "59.9139, 10.7522",
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        val field = compose.onNodeWithText("Location Name")
        "Jobb".forEach { ch ->
            field.performTextInput(ch.toString())
            compose.waitForIdle()
        }

        assertEquals("Jobb", nameFlow.value)
        compose.onNodeWithText("Jobb").assertIsDisplayed()
    }

    /** The reverse-geocoded name arriving after the dialog opens should populate the empty field. */
    @Test
    fun reverseGeocodedNameSeedsUntouchedField() {
        val nameFlow = MutableStateFlow("")

        compose.setContent {
            val name by nameFlow.collectAsState()
            MaterialTheme {
                MapDialogs.SavePointDialog(
                    pointName = name,
                    onPointNameChange = { nameFlow.value = it },
                    pointDescription = "",
                    onPointDescriptionChange = {},
                    coordinates = "59.9139, 10.7522",
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        nameFlow.value = "Blåbærmyra"
        compose.waitForIdle()

        compose.onNodeWithText("Blåbærmyra").assertIsDisplayed()
    }
}
