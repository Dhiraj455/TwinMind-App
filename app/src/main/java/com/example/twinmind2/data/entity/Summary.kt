package com.example.twinmind2.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class Summary(
    @PrimaryKey val sessionId: Long,
    val status: String = "idle",
    val title: String? = null,
    val summary: String? = null,
    val actionItems: String? = null,
    val keyPoints: String? = null,
    val sectionsCompleted: Int = 0,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

