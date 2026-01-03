package no.synth.where.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

class UserPreferences private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    var showCountyBorders by mutableStateOf(prefs.getBoolean("show_county_borders", true))
        private set

    fun updateShowCountyBorders(value: Boolean) {
        showCountyBorders = value
        prefs.edit { putBoolean("show_county_borders", value) }
    }

    companion object {
        @Volatile
        private var instance: UserPreferences? = null

        fun getInstance(context: Context): UserPreferences {
            return instance ?: synchronized(this) {
                instance ?: UserPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}

