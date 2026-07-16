package no.synth.where.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val isRecording: Boolean = false,
    // null = unfiled; the exact (case-sensitive) name is the folder's identity; one folder per track.
    val folder: String? = null
)
