package no.synth.where.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import no.synth.where.data.StravaTokenManager
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Guides the user through creating their own Strava API app and entering its credentials.
 * BYO credentials sidestep Strava's one-athlete-per-app cap: each user owns their own app.
 */
@Composable
fun StravaSetupDialog(
    initialClientId: String?,
    onSave: (clientId: String, clientSecret: String) -> Unit,
    onRemoveCredentials: () -> Unit,
    onOpenApiSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var clientId by remember { mutableStateOf(initialClientId ?: "") }
    var clientSecret by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }
    val canSave = clientId.isNotBlank() && clientSecret.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.strava_setup_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(Res.string.strava_setup_instructions, StravaTokenManager.CALLBACK_DOMAIN),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onOpenApiSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.strava_open_api_settings))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text(stringResource(Res.string.strava_client_id_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text(stringResource(Res.string.strava_client_secret_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { secretVisible = !secretVisible }) {
                            Text(stringResource(if (secretVisible) Res.string.strava_hide else Res.string.strava_show))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!initialClientId.isNullOrBlank()) {
                    TextButton(
                        onClick = { onRemoveCredentials(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(Res.string.strava_remove_credentials)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(clientId.trim(), clientSecret.trim()) },
                enabled = canSave
            ) { Text(stringResource(Res.string.strava_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
