package no.synth.where.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences private constructor(context: Context) {
    private val dataStore = context.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var showCountyBorders by mutableStateOf(
        runBlocking {
            dataStore.data.map { it[SHOW_COUNTY_BORDERS] ?: true }.first()
        }
    )
        private set

    fun updateShowCountyBorders(value: Boolean) {
        showCountyBorders = value
        scope.launch {
            dataStore.edit { it[SHOW_COUNTY_BORDERS] = value }
        }
    }

    companion object {
        private val SHOW_COUNTY_BORDERS = booleanPreferencesKey("show_county_borders")

        @Volatile
        private var instance: UserPreferences? = null

        fun getInstance(context: Context): UserPreferences {
            return instance ?: synchronized(this) {
                instance ?: UserPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}

