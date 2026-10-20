package no.synth.where.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.synth.where.resources.Res
import no.synth.where.resources.cancel
import no.synth.where.resources.change_nickname
import no.synth.where.resources.nickname
import no.synth.where.resources.remove_nickname
import no.synth.where.resources.save
import no.synth.where.resources.set_nickname
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Long enough for a name, short enough to fit the map banner. */
const val MAX_NICKNAME_LENGTH = 20

/** "Edit nickname" when [nickname] is set, otherwise "Set nickname". */
fun nicknameActionLabel(nickname: String?): StringResource =
    if (nickname != null) Res.string.change_nickname else Res.string.set_nickname

/** Sets or clears the local nickname of [clientId]; an empty name clears it. */
@Composable
fun NicknameDialog(
    clientId: String,
    nickname: String?,
    onSetNickname: (clientId: String, nickname: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember(clientId) { mutableStateOf(nickname ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(nicknameActionLabel(nickname))) },
        text = {
            Column {
                Text(
                    text = clientId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(MAX_NICKNAME_LENGTH) },
                    label = { Text(stringResource(Res.string.nickname)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSetNickname(clientId, nameInput)
                onDismiss()
            }) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            Row {
                if (nickname != null) {
                    TextButton(onClick = {
                        onSetNickname(clientId, "")
                        onDismiss()
                    }) {
                        Text(stringResource(Res.string.remove_nickname))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        }
    )
}
