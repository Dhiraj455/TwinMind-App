package com.example.twinmind2.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmind2.data.entity.ChatMessage
import com.example.twinmind2.data.entity.ChatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    val allChatSessions: StateFlow<List<ChatSession>> = chatRepository.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)

    val messages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { id ->
            if (id != null) chatRepository.observeMessages(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession

    fun loadSession(chatSessionId: Long) {
        _currentSessionId.value = chatSessionId
        viewModelScope.launch {
            val session = chatRepository.getSession(chatSessionId)
            _currentSession.value = session

            // Auto-fetch AI response if only the initial user message exists
            val existingMessages = chatRepository.getMessages(chatSessionId)
            if (existingMessages.size == 1 && existingMessages[0].role == "user") {
                fetchAiResponse(
                    chatSessionId = chatSessionId,
                    type = session?.type ?: "all",
                    recordingSessionId = session?.recordingSessionId
                )
            }
        }
    }

    suspend fun createSessionAndSendFirstMessage(
        query: String,
        type: String,
        recordingSessionId: Long?
    ): Long {
        val title = query.take(60).trim()
        val sessionId = chatRepository.createSession(
            title = title,
            type = type,
            recordingSessionId = recordingSessionId
        )
        chatRepository.saveMessage(sessionId, "user", query)
        return sessionId
    }

    fun fetchAiResponse(chatSessionId: Long, type: String, recordingSessionId: Long?) {
        if (_isLoading.value) return
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val response = chatRepository.getAiResponse(chatSessionId, type, recordingSessionId)
                chatRepository.saveMessage(chatSessionId, "assistant", response)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to get response"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendMessage(content: String) {
        val sessionId = _currentSessionId.value ?: return
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            chatRepository.saveMessage(sessionId, "user", content)
            fetchAiResponse(sessionId, session.type, session.recordingSessionId)
        }
    }

    fun retryLastMessage() {
        val sessionId = _currentSessionId.value ?: return
        val session = _currentSession.value ?: return
        if (_isLoading.value) return
        _error.value = null
        _isLoading.value = true
        viewModelScope.launch {
            // Remove the previous AI response so the regenerated one replaces it cleanly
            chatRepository.deleteLastAssistantMessage(sessionId)
            try {
                val response = chatRepository.getAiResponse(sessionId, session.type, session.recordingSessionId)
                chatRepository.saveMessage(sessionId, "assistant", response)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to get response"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun observeSessionMessages(chatSessionId: Long): Flow<List<ChatMessage>> =
        chatRepository.observeMessages(chatSessionId)

    fun deleteCurrentSession() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
        }
    }

    fun deleteChatSession(chatSessionId: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(chatSessionId)
        }
    }
}
