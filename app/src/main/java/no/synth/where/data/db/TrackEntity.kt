package no.synth.where.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val isRecording: Boolean = false
)
