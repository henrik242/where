package no.synth.where.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_points")
data class SavedPointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String? = "",
    val timestamp: Long = System.currentTimeMillis(),
    val color: String? = "#FF5722"
)
