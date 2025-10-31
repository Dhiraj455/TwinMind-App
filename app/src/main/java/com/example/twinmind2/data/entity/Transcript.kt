package com.example.twinmind2.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcripts",
    indices = [Index(value = ["sessionId", "chunkId"], unique = true)]
)
data class Transcript(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val chunkId: Long, // Reference to AudioChunk.id
    val chunkIndex: Int, // indexInSession for ordering
    val text: String,
    val status: String, // pending, completed, failed
    val errorMessage: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val completedAtMs: Long? = null
)

