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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    fun chunksFor(sessionId: Long): Flow<List<AudioChunk>> = repository.observeChunks(sessionId)

    fun transcriptsFor(sessionId: Long): Flow<List<Transcript>> =
        transcriptionRepository.observeTranscriptsForSession(sessionId)

    fun summaryFor(sessionId: Long): Flow<Summary?> =
        summaryRepository.observeSummary(sessionId)

    fun generateSummary(sessionId: Long) {
        summaryRepository.generateSummary(sessionId)
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }
}


