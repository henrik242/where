package no.synth.where.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.clientDataStore: DataStore<Preferences> by preferencesDataStore(name = "client_prefs")

class ClientIdManager(private val context: Context) {

    suspend fun getClientId(): String {
        return context.clientDataStore.data.map { preferences ->
            preferences[CLIENT_ID_KEY]
        }.first() ?: generateAndSaveClientId()
    }

    private suspend fun generateAndSaveClientId(): String {
        val clientId = generateClientId()
        context.clientDataStore.edit { preferences ->
            preferences[CLIENT_ID_KEY] = clientId
        }
        return clientId
    }

    private fun generateClientId(): String {
        val chars = ('a'..'z') + ('0'..'9')
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }

    suspend fun regenerateClientId(): String {
        val newClientId = generateClientId()
        context.clientDataStore.edit { preferences ->
            preferences[CLIENT_ID_KEY] = newClientId
        }
        return newClientId
    }

    companion object {
        private val CLIENT_ID_KEY = stringPreferencesKey("client_id")
    }
}

