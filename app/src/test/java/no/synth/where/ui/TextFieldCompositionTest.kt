package no.synth.where.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * material3 draws text field containers through a foundation style modifier, so an androidx
 * material3 built against a different compose train than the compose BOM throws while attaching
 * that node and every screen with a text field dies. Composing text fields for real catches it.
 */
@RunWith(RobolectricTestRunner::class)
class TextFieldCompositionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun textFieldsComposeAndLayOut() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.padding(4.dp)) { Text("row child") }
                    OutlinedTextField(value = "oslo", onValueChange = {}, label = { Text("place") })
                    TextField(value = "bergen", onValueChange = {})
                }
            }
        }

        compose.onNodeWithText("oslo").assertIsDisplayed()
        compose.onNodeWithText("bergen").assertIsDisplayed()
    }
}
