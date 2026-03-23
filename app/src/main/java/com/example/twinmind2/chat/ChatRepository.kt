package com.example.twinmind2.chat

import android.util.Log
import com.example.twinmind2.data.dao.ChatDao
import com.example.twinmind2.data.dao.SummaryDao
import com.example.twinmind2.data.dao.TranscriptDao
import com.example.twinmind2.data.entity.ChatMessage
import com.example.twinmind2.data.entity.ChatSession
import com.example.twinmind2.di.NetworkModule
import com.example.twinmind2.transcription.TranscriptionApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val summaryDao: SummaryDao,
    private val transcriptDao: TranscriptDao,
    private val transcriptionApi: TranscriptionApi
) {
    private val apiKey: String = NetworkModule.getGeminiApiKey()

    fun observeAllSessions(): Flow<List<ChatSession>> = chatDao.observeAllSessions()

    fun observeMessages(chatSessionId: Long): Flow<List<ChatMessage>> =
        chatDao.observeMessages(chatSessionId)

    suspend fun getSession(chatSessionId: Long): ChatSession? = chatDao.getSession(chatSessionId)

    suspend fun createSession(title: String, type: String, recordingSessionId: Long?): Long {
        val session = ChatSession(
            title = title,
            type = type,
            recordingSessionId = recordingSessionId,
            createdAt = System.currentTimeMillis(),
            lastMessageAt = System.currentTimeMillis()
        )
        return chatDao.insertSession(session)
    }

    suspend fun saveMessage(chatSessionId: Long, role: String, content: String): Long {
        val now = System.currentTimeMillis()
        val message = ChatMessage(
            chatSessionId = chatSessionId,
            role = role,
            content = content,
            createdAt = now
        )
        val id = chatDao.insertMessage(message)
        chatDao.updateLastMessageAt(chatSessionId, now)
        return id
    }

    suspend fun getMessages(chatSessionId: Long): List<ChatMessage> =
        chatDao.getMessages(chatSessionId)

    suspend fun deleteSession(chatSessionId: Long) {
        chatDao.deleteMessages(chatSessionId)
        chatDao.deleteSession(chatSessionId)
    }

    suspend fun deleteLastAssistantMessage(chatSessionId: Long) {
        chatDao.getLastAssistantMessage(chatSessionId)?.let { msg ->
            chatDao.deleteMessage(msg.id)
        }
    }

    suspend fun getAiResponse(
        chatSessionId: Long,
        type: String,
        recordingSessionId: Long?
    ): String {
        val messages = chatDao.getMessages(chatSessionId)
        val latestUserQuery = messages.lastOrNull { it.role == "user" }?.content ?: ""
        val context = buildContext(type, recordingSessionId, latestUserQuery)
        return callGemini(messages, context)
    }

    private suspend fun buildContext(
        type: String,
        recordingSessionId: Long?,
        userQuery: String
    ): String = when (type) {
        "recording" -> if (recordingSessionId != null) buildRecordingContext(recordingSessionId) else ""
        "all" -> buildAllMemoriesContext(userQuery)
        else -> ""
    }

    private suspend fun buildRecordingContext(sessionId: Long): String {
        val summary = summaryDao.getSummary(sessionId)
        val transcripts = transcriptDao.getTranscriptsForSession(sessionId)
            .filter { it.status == "completed" }
            .sortedBy { it.chunkIndex }

        if (summary == null && transcripts.isEmpty()) return ""

        return buildString {
            appendLine("=== Recording Notes ===")
            summary?.title?.takeIf { it.isNotBlank() }?.let { appendLine("Title: $it") }
            summary?.summary?.takeIf { it.isNotBlank() }?.let { appendLine("Summary: $it") }
            summary?.actionItems?.takeIf { it.isNotBlank() }?.let { appendLine("Action Items:\n$it") }
            summary?.keyPoints?.takeIf { it.isNotBlank() }?.let { appendLine("Key Points:\n$it") }
            if (transcripts.isNotEmpty()) {
                appendLine()
                appendLine("Full Transcript:")
                val fullTranscript = transcripts.joinToString(" ") { it.text }
                appendLine(fullTranscript.take(8000))
            }
        }
    }

    private suspend fun buildAllMemoriesContext(userQuery: String): String {
        val queryWords = userQuery.lowercase()
            .split(" ", ",", ".", "?", "!", "\n")
            .map { it.trim() }
            .filter { it.length > 2 }

        if (queryWords.isEmpty()) return ""

        val allSummaries = summaryDao.getAllCompletedSummaries()
        val allTranscripts = transcriptDao.getAllCompletedTranscripts()

        val sessionScores = mutableMapOf<Long, Int>()

        allSummaries.forEach { summary ->
            val content = listOfNotNull(
                summary.title, summary.summary, summary.keyPoints, summary.actionItems
            ).joinToString(" ").lowercase()
            val score = queryWords.count { word -> content.contains(word) }
            if (score > 0) {
                sessionScores[summary.sessionId] = (sessionScores[summary.sessionId] ?: 0) + score * 2
            }
        }

        allTranscripts.groupBy { it.sessionId }.forEach { (sessionId, transcripts) ->
            val content = transcripts.joinToString(" ") { it.text }.lowercase()
            val score = queryWords.count { word -> content.contains(word) }
            if (score > 0) {
                sessionScores[sessionId] = (sessionScores[sessionId] ?: 0) + score
            }
        }

        val topSessionIds = sessionScores.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        if (topSessionIds.isEmpty()) return ""

        return buildString {
            appendLine("=== Relevant Memories Found ===")
            topSessionIds.forEachIndexed { i, sessionId ->
                appendLine()
                appendLine("--- Memory ${i + 1} ---")
                val summary = allSummaries.find { it.sessionId == sessionId }
                val transcripts = allTranscripts
                    .filter { it.sessionId == sessionId }
                    .sortedBy { it.chunkIndex }
                summary?.title?.takeIf { it.isNotBlank() }?.let { appendLine("Title: $it") }
                summary?.summary?.takeIf { it.isNotBlank() }?.let { appendLine("Summary: $it") }
                summary?.actionItems?.takeIf { it.isNotBlank() }?.let { appendLine("Action Items:\n$it") }
                if (transcripts.isNotEmpty()) {
                    val excerpt = transcripts.joinToString(" ") { it.text }.take(2000)
                    appendLine("Transcript excerpt: $excerpt")
                }
            }
        }
    }

    private suspend fun callGemini(messages: List<ChatMessage>, context: String): String {
        val promptText = buildString {
            appendLine("You are TwinMind AI, a helpful assistant that helps users recall and work with their recorded memories.")
            if (context.isNotBlank()) {
                appendLine()
                appendLine("Here is relevant context from the user's memories:")
                appendLine(context)
                appendLine()
                appendLine("Use the above context to answer the user's questions accurately. If the context is not relevant to the question, still answer helpfully using your general knowledge.")
            } else {
                appendLine("No specific memory context is available for this query. Answer the user's question as best you can.")
            }
            appendLine()
            appendLine("Conversation:")
            messages.forEach { msg ->
                val label = if (msg.role == "user") "User" else "Assistant"
                appendLine("$label: ${msg.content}")
            }
            appendLine()
            appendLine("Respond to the user's last message. Be helpful, accurate, and concise.")
        }

        val request = TranscriptionApi.GenerateContentRequest(
            contents = listOf(
                TranscriptionApi.Content(
                    parts = listOf(TranscriptionApi.Part(text = promptText))
                )
            ),
            generationConfig = TranscriptionApi.GenerationConfig(response_mime_type = "text/plain")
        )

        var attempt = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            val response = transcriptionApi.generateContent(apiKey, request)
            when {
                response.isSuccessful && response.body() != null -> {
                    val text = response.body()?.candidates
                        ?.firstOrNull()?.content?.parts
                        ?.firstOrNull { !it.text.isNullOrBlank() }?.text?.trim()
                        ?: throw Exception("Empty response from AI")
                    return text
                }
                response.code() == 429 -> {
                    attempt++
                    if (attempt >= maxRetries) {
                        throw Exception("Rate limit exceeded. Please try again in a moment.")
                    }
                    val delaySec = 2L * (1 shl (attempt - 1))
                    Log.w("ChatRepository", "Rate limited, retrying in ${delaySec}s")
                    kotlinx.coroutines.delay(delaySec * 1000)
                    lastException = Exception("Rate limit exceeded")
                }
                else -> {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ChatRepository", "API error: code=${response.code()} body=$errorBody")
                    throw Exception("Failed to get AI response: ${response.message()}")
                }
            }
        }
        throw lastException ?: Exception("Failed after $maxRetries attempts")
    }
}
