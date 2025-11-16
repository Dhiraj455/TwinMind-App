package com.example.twinmind2.recording

import android.content.Context
import com.example.twinmind2.data.dao.RecordingDao
import com.example.twinmind2.data.entity.AudioChunk
import com.example.twinmind2.data.entity.RecordingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class RecordingRepository(
    private val appContext: Context,
    private val dao: RecordingDao
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
        dao.updateSession(session.copy(completeAudioPath = completeAudioPath, status = "stopped", endTimeMs = System.currentTimeMillis()))
    }

    fun observeSessions(): Flow<List<RecordingSession>> = dao.observeSessions()

    fun observeChunks(sessionId: Long): Flow<List<AudioChunk>> = dao.observeChunks(sessionId)

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
        dao.deleteChunksForSession(sessionId)
        dao.deleteSession(sessionId)
        val sessionDir = getSessionDir(sessionId)
        if (sessionDir.exists()) {
            sessionDir.deleteRecursively()
        }
    }
}


