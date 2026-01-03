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

class ClientIdManager private constructor(private val context: Context) {

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

    companion object {
        private val CLIENT_ID_KEY = stringPreferencesKey("client_id")

        @Volatile
        private var instance: ClientIdManager? = null

        fun getInstance(context: Context): ClientIdManager {
            return instance ?: synchronized(this) {
                instance ?: ClientIdManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

