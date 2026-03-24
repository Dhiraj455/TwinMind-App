package com.example.twinmind2.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmind2.data.entity.AudioChunk
import com.example.twinmind2.data.entity.RecordingSession
import com.example.twinmind2.data.entity.Summary
import com.example.twinmind2.data.entity.Transcript
import com.example.twinmind2.summary.SummaryRepository
import com.example.twinmind2.transcription.TranscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val repository: RecordingRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val summaryRepository: SummaryRepository
) : ViewModel() {

    val recordingState: StateFlow<RecordingRepository.RecordingUiState> =
        repository.state.stateIn(viewModelScope, SharingStarted.Eagerly, repository.state.value)
    val sessions: StateFlow<List<RecordingSession>> =
        repository.observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<RecordingSession>> =
        _searchQuery
            .debounce(300)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) repository.observeSessions()
                else repository.searchSessions(query.trim())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun chunksFor(sessionId: Long): Flow<List<AudioChunk>> = repository.observeChunks(sessionId)

    fun transcriptsFor(sessionId: Long): Flow<List<Transcript>> =
        transcriptionRepository.observeTranscriptsForSession(sessionId)

    fun summaryFor(sessionId: Long): Flow<Summary?> =
        summaryRepository.observeSummary(sessionId)

    suspend fun getSummaryOnce(sessionId: Long): Summary? =
        summaryRepository.getSummary(sessionId)

    fun generateSummary(sessionId: Long) {
        summaryRepository.generateSummary(sessionId)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    fun deleteSessions(sessionIds: Collection<Long>) {
        viewModelScope.launch {
            repository.deleteSessions(sessionIds)
        }
    }

    fun renameSessionTitle(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameSessionTitle(sessionId, newTitle)
        }
    }

    fun retryTranscriptChunk(sessionId: Long, chunkId: Long) {
        viewModelScope.launch {
            val chunk = repository.observeChunks(sessionId).first().find { it.id == chunkId } ?: return@launch
            transcriptionRepository.createPendingTranscript(chunk)
            transcriptionRepository.transcribeChunk(chunk).fold(
                onSuccess = { raw ->
                    val cleaned = runCatching {
                        transcriptionRepository.contextCorrectTranscript(sessionId, raw).getOrDefault(raw)
                    }.getOrDefault(raw)
                    transcriptionRepository.updateTranscriptSuccess(sessionId, chunkId, raw, cleaned)
                },
                onFailure = { e ->
                    transcriptionRepository.updateTranscriptFailure(sessionId, chunkId, e.message ?: "Transcription failed")
                }
            )
        }
    }

    suspend fun combineSessionsToNewNote(sessionIds: Collection<Long>): Long? {
        val orderedIds = sessionIds
            .toList()
            .sortedBy { id -> sessions.value.firstOrNull { it.id == id }?.startTimeMs ?: Long.MAX_VALUE }

        val combinedText = orderedIds.mapIndexedNotNull { index, sessionId ->
            val text = transcriptionRepository.getTranscriptsForSession(sessionId)
                .filter { it.status == "completed" }
                .sortedBy { it.chunkIndex }
                .joinToString("\n") { it.text }
                .trim()
            if (text.isBlank()) null else "Recording ${index + 1}\n$text"
        }.joinToString("\n\n")

        if (combinedText.isBlank()) return null

        val newSessionId = repository.createCombinedSession("Combined Notes")
        transcriptionRepository.insertCombinedTranscript(newSessionId, combinedText)
        summaryRepository.generateSummary(newSessionId)
        return newSessionId
    }
}


