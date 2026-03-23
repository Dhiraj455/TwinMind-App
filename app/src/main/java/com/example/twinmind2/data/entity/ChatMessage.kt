package com.example.twinmind2.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatSessionId: Long,
    val role: String, // "user" or "assistant"
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
