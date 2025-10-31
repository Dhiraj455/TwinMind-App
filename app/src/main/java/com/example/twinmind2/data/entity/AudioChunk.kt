package com.example.twinmind2.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_chunks",
    indices = [Index(value = ["sessionId", "indexInSession"], unique = true)]
)
data class AudioChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val indexInSession: Int,
    val filePath: String,
    val durationMs: Long,
    val createdAtMs: Long,
    val finalized: Boolean = true
)


