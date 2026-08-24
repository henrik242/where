package no.synth.where.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
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
}
