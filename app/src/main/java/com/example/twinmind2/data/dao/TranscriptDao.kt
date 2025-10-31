package com.example.twinmind2.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.twinmind2.data.entity.Transcript
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: Transcript): Long

    @Update
    suspend fun updateTranscript(transcript: Transcript)

    @Query("SELECT * FROM transcripts WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    fun observeTranscriptsForSession(sessionId: Long): Flow<List<Transcript>>

    @Query("SELECT * FROM transcripts WHERE sessionId = :sessionId AND chunkId = :chunkId")
    suspend fun getTranscriptForChunk(sessionId: Long, chunkId: Long): Transcript?

    @Query("SELECT * FROM transcripts WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    suspend fun getTranscriptsForSession(sessionId: Long): List<Transcript>

    @Query("SELECT * FROM transcripts WHERE status = 'pending' OR status = 'failed'")
    suspend fun getPendingTranscripts(): List<Transcript>

    @Query("SELECT * FROM transcripts WHERE sessionId = :sessionId AND status = 'pending' ORDER BY chunkIndex ASC")
    suspend fun getPendingTranscriptsForSession(sessionId: Long): List<Transcript>
}

