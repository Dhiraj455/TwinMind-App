package com.example.twinmind2.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.twinmind2.data.entity.ChatMessage
import com.example.twinmind2.data.entity.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageAt DESC")
    fun observeAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSession(id: Long): ChatSession?

    @Query("SELECT * FROM chat_messages WHERE chatSessionId = :sessionId ORDER BY createdAt ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE chatSessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getMessages(sessionId: Long): List<ChatMessage>

    @Query("UPDATE chat_sessions SET lastMessageAt = :timestamp WHERE id = :id")
    suspend fun updateLastMessageAt(id: Long, timestamp: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM chat_messages WHERE chatSessionId = :sessionId")
    suspend fun deleteMessages(sessionId: Long)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("SELECT * FROM chat_messages WHERE chatSessionId = :sessionId AND role = 'assistant' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastAssistantMessage(sessionId: Long): ChatMessage?
}
