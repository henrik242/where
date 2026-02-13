package no.synth.where.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ClientIdManager(private val dataStore: DataStore<Preferences>) {

    suspend fun getClientId(): String {
        return dataStore.data.map { preferences ->
            preferences[CLIENT_ID_KEY]
        }.first() ?: generateAndSaveClientId()
    }

    private suspend fun generateAndSaveClientId(): String {
        val clientId = generateClientId()
        dataStore.edit { preferences ->
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
        dataStore.edit { preferences ->
            preferences[CLIENT_ID_KEY] = newClientId
        }
        return newClientId
    }

    companion object {
        private val CLIENT_ID_KEY = stringPreferencesKey("client_id")
    }
}
