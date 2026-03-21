package com.example.twinmind2.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.twinmind2.data.entity.AudioChunk
import com.example.twinmind2.data.entity.RecordingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert
    suspend fun insertSession(session: RecordingSession): Long

    @Update
    suspend fun updateSession(session: RecordingSession)

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getSession(id: Long): RecordingSession?

    @Query("SELECT * FROM recording_sessions ORDER BY startTimeMs DESC")
    fun observeSessions(): Flow<List<RecordingSession>>

    @Query("SELECT * FROM recording_sessions WHERE status = 'active' ORDER BY startTimeMs ASC")
    suspend fun getActiveSessions(): List<RecordingSession>

    @Query(
        """
        SELECT DISTINCT rs.* FROM recording_sessions rs
        LEFT JOIN summaries s ON s.sessionId = rs.id
        LEFT JOIN transcripts t ON t.sessionId = rs.id AND t.status = 'completed'
        WHERE rs.title LIKE '%' || :query || '%'
           OR rs.tags LIKE '%' || :query || '%'
           OR s.title LIKE '%' || :query || '%'
           OR s.summary LIKE '%' || :query || '%'
           OR s.actionItems LIKE '%' || :query || '%'
           OR s.keyPoints LIKE '%' || :query || '%'
           OR t.text LIKE '%' || :query || '%'
        ORDER BY rs.startTimeMs DESC
        """
    )
    fun searchSessions(query: String): Flow<List<RecordingSession>>

    @Query("UPDATE recording_sessions SET title = :title, tags = :tags WHERE id = :sessionId")
    suspend fun updateSessionTitleAndTags(sessionId: Long, title: String?, tags: String?)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChunk(chunk: AudioChunk): Long

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId ORDER BY indexInSession ASC")
    fun observeChunks(sessionId: Long): Flow<List<AudioChunk>>

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId ORDER BY indexInSession ASC")
    suspend fun getChunksForSession(sessionId: Long): List<AudioChunk>

    @Query("DELETE FROM audio_chunks WHERE id = :chunkId")
    suspend fun deleteChunkById(chunkId: Long)

    @Query("DELETE FROM recording_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM audio_chunks WHERE sessionId = :sessionId")
    suspend fun deleteChunksForSession(sessionId: Long)
}


