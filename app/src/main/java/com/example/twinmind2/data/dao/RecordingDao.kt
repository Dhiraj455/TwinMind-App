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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChunk(chunk: AudioChunk): Long

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId ORDER BY indexInSession ASC")
    fun observeChunks(sessionId: Long): Flow<List<AudioChunk>>
}


