package com.example.twinmind2.recording

import android.content.Context
import com.example.twinmind2.data.dao.RecordingDao
import com.example.twinmind2.data.dao.SummaryDao
import com.example.twinmind2.data.dao.TranscriptDao
import com.example.twinmind2.data.entity.AudioChunk
import com.example.twinmind2.data.entity.RecordingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class RecordingRepository(
    private val appContext: Context,
    private val dao: RecordingDao,
    private val transcriptDao: TranscriptDao,
    private val summaryDao: SummaryDao
) {
    data class RecordingUiState(
        val activeSessionId: Long? = null,
        val status: String = "Stopped",
        val elapsedSec: Int = 0,
        val isPaused: Boolean = false
    )

    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state

    suspend fun startSession(): Long {
        val session = RecordingSession(startTimeMs = System.currentTimeMillis(), status = "active")
        val id = dao.insertSession(session)
        ensureSessionDir(id)
        _state.value = _state.value.copy(activeSessionId = id, status = "Recording", elapsedSec = 0, isPaused = false)
        return id
    }

    suspend fun updateSessionCompleteAudio(sessionId: Long, completeAudioPath: String) {
        val session = dao.getSession(sessionId) ?: return
        val endTime = session.endTimeMs ?: System.currentTimeMillis()
        dao.updateSession(
            session.copy(completeAudioPath = completeAudioPath, status = "stopped", endTimeMs = endTime)
        )
    }

    /**
     * Persists when recording is stopping (e.g. app removed from recents) so [endTimeMs] is not lost
     * if the process dies before [updateSessionCompleteAudio] runs. Keeps status active so recovery can still finalize audio.
     */
    suspend fun markSessionRecordingEndTime(sessionId: Long, endTimeMs: Long = System.currentTimeMillis()) {
        val session = dao.getSession(sessionId) ?: return
        if (session.endTimeMs != null) return
        dao.updateSession(session.copy(endTimeMs = endTimeMs))
    }

    fun observeSessions(): Flow<List<RecordingSession>> = dao.observeSessions()

    fun searchSessions(query: String): Flow<List<RecordingSession>> = dao.searchSessions(query)

    suspend fun getSession(sessionId: Long): RecordingSession? = dao.getSession(sessionId)

    suspend fun renameSessionTitle(sessionId: Long, newTitle: String) {
        val current = dao.getSession(sessionId) ?: return
        dao.updateSession(current.copy(title = newTitle))
        val existingSummary = summaryDao.getSummary(sessionId)
        if (existingSummary != null) {
            summaryDao.insert(
                existingSummary.copy(
                    title = newTitle,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun createCombinedSession(title: String = "Combined Recording"): Long {
        val now = System.currentTimeMillis()
        return dao.insertSession(
            RecordingSession(
                startTimeMs = now,
                endTimeMs = now,
                status = "combined",
                title = title,
                tags = "combined"
            )
        )
    }

    fun observeChunks(sessionId: Long): Flow<List<AudioChunk>> = dao.observeChunks(sessionId)

//    suspend fun deleteChunkById(chunkId: Long) {
//        dao.deleteChunkById(chunkId)
//    }

    suspend fun addChunk(
        sessionId: Long,
        index: Int,
        file: File,
        durationMs: Long,
        finalized: Boolean = true
    ): Long {
        val chunk = AudioChunk(
            sessionId = sessionId,
            indexInSession = index,
            filePath = file.absolutePath,
            durationMs = durationMs,
            createdAtMs = System.currentTimeMillis(),
            finalized = finalized
        )
        return dao.insertChunk(chunk)
    }

    fun getSessionDir(sessionId: Long): File = File(appContext.filesDir, "recordings/$sessionId").apply { mkdirs() }

    private fun ensureSessionDir(sessionId: Long) { getSessionDir(sessionId) }

    fun updateElapsed(elapsedSec: Int) {
        _state.value = _state.value.copy(elapsedSec = elapsedSec)
    }

    fun setPaused(paused: Boolean, status: String) {
        _state.value = _state.value.copy(isPaused = paused, status = status)
    }

    fun setStatus(status: String) {
        _state.value = _state.value.copy(status = status)
    }

    fun clearActive() {
        _state.value = RecordingUiState()
    }

    suspend fun deleteSession(sessionId: Long) {
        // Delete all related data from database
        dao.deleteChunksForSession(sessionId)
        transcriptDao.deleteTranscriptsForSession(sessionId)
        summaryDao.deleteSummaryForSession(sessionId)
        dao.deleteSession(sessionId)
        
        // Delete all audio files from storage
        val sessionDir = getSessionDir(sessionId)
        if (sessionDir.exists()) {
            sessionDir.deleteRecursively()
        }
    }

    suspend fun deleteSessions(sessionIds: Collection<Long>) {
        sessionIds.forEach { deleteSession(it) }
    }
}


