package com.example.twinmind2.summary

import android.content.Context
import android.util.Log
import com.example.twinmind2.data.dao.SummaryDao
import com.example.twinmind2.data.dao.TranscriptDao
import com.example.twinmind2.data.entity.Summary
import com.example.twinmind2.di.NetworkModule
import com.example.twinmind2.transcription.TranscriptionApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class SummarySection {
    TITLE,
    SUMMARY,
    ACTION_ITEMS,
    KEY_POINTS
}

@Singleton
class SummaryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val summaryDao: SummaryDao,
    private val transcriptDao: TranscriptDao,
    private val transcriptionApi: TranscriptionApi
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val apiKey: String = NetworkModule.getGeminiApiKey(context)

    fun observeSummary(sessionId: Long): Flow<Summary?> =
        summaryDao.observeSummary(sessionId)

    suspend fun ensureSummary(sessionId: Long): Summary {
        val existing = summaryDao.getSummary(sessionId)
        if (existing != null) return existing

        val summary = Summary(sessionId = sessionId)
        summaryDao.insert(summary)
        return summary
    }

    suspend fun markGenerating(sessionId: Long) {
        val current = ensureSummary(sessionId)
        val updated = current.copy(
            status = "generating",
            title = null,
            summary = null,
            actionItems = null,
            keyPoints = null,
            sectionsCompleted = 0,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )
        summaryDao.insert(updated)
    }

    suspend fun updateSection(sessionId: Long, section: SummarySection, value: String, sectionsCompleted: Int) {
        val current = ensureSummary(sessionId)
        val updated = when (section) {
            SummarySection.TITLE -> current.copy(title = value)
            SummarySection.SUMMARY -> current.copy(summary = value)
            SummarySection.ACTION_ITEMS -> current.copy(actionItems = value)
            SummarySection.KEY_POINTS -> current.copy(keyPoints = value)
        }
        summaryDao.insert(
            updated.copy(
                sectionsCompleted = sectionsCompleted,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markCompleted(sessionId: Long) {
        val current = ensureSummary(sessionId)
        summaryDao.insert(
            current.copy(
                status = "completed",
                sectionsCompleted = 4,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markFailed(sessionId: Long, message: String) {
        val current = ensureSummary(sessionId)
        summaryDao.insert(
            current.copy(
                status = "failed",
                errorMessage = message,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun generateSummary(sessionId: Long) {
        val existingJob = jobs[sessionId]
        if (existingJob?.isActive == true) return

        val job = scope.launch {
            runSummaryGeneration(sessionId)
        }
        jobs[sessionId] = job
    }

    private suspend fun runSummaryGeneration(sessionId: Long) {
        try {
            markGenerating(sessionId)

            val transcripts = transcriptDao.getTranscriptsForSession(sessionId)
                .filter { it.status == "completed" }
                .sortedBy { it.chunkIndex }

            if (transcripts.isEmpty()) {
                markFailed(sessionId, "No transcripts available for summarization")
                return
            }

            val transcriptText = transcripts.joinToString("\n") { it.text }
            val maxCharacters = 15_000
            val safeTranscriptText = if (transcriptText.length > maxCharacters) {
                transcriptText.takeLast(maxCharacters)
            } else {
                transcriptText
            }

            val sections = listOf(
                SummarySection.TITLE to "Generate a concise meeting title based on the transcript. Respond with the title only.",
                SummarySection.SUMMARY to "Provide a concise meeting summary in 3-4 sentences.",
                SummarySection.ACTION_ITEMS to "List clear action items with owners when possible. Use bullet points. If none, respond with 'None'.",
                SummarySection.KEY_POINTS to "List key decisions or important points as bullet points."
            )

            sections.forEachIndexed { index, (section, instruction) ->
                val text = generateSection(safeTranscriptText, instruction)
                updateSection(sessionId, section, text, index + 1)
            }

            markCompleted(sessionId)
        } catch (e: Exception) {
            Log.e("SummaryRepository", "Error generating summary", e)
            markFailed(sessionId, e.message ?: "Unknown error")
        } finally {
            jobs.remove(sessionId)
        }
    }

    private suspend fun generateSection(transcript: String, instruction: String): String {
        val request = TranscriptionApi.GenerateContentRequest(
            contents = listOf(
                TranscriptionApi.Content(
                    parts = listOf(
                        TranscriptionApi.Part(
                            text = "You are an assistant creating meeting summaries.\n$instruction\n\nTranscript:\n$transcript"
                        )
                    )
                )
            ),
            generationConfig = TranscriptionApi.GenerationConfig(
                response_mime_type = "text/plain"
            )
        )

        // Retry logic with exponential backoff for rate limiting
        var attempt = 0
        val maxRetries = 5
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            val response = transcriptionApi.generateContent(apiKey, request)
            
            when {
                response.isSuccessful && response.body() != null -> {
                    // Success
                    val candidates = response.body()?.candidates
                    val content = candidates?.firstOrNull()?.content
                    val textPart = content?.parts?.firstOrNull { !it.text.isNullOrBlank() }
                    return textPart?.text?.trim() ?: throw Exception("Empty response from summary API")
                }
                
                response.code() == 429 -> {
                    // Rate limit - retry with exponential backoff
                    attempt++
                    if (attempt >= maxRetries) {
                        val errorBody = response.errorBody()?.string()
                        Log.e("SummaryRepository", "Rate limit exceeded after $maxRetries attempts: body=$errorBody")
                        throw Exception("Rate limit exceeded. Please try again in a few minutes.")
                    }
                    
                    // Exponential backoff: 2, 4, 8, 16, 32 seconds
                    val delaySeconds = 2L * (1 shl (attempt - 1))
                    Log.w("SummaryRepository", "Rate limited (429), retrying in ${delaySeconds}s (attempt $attempt/$maxRetries)")
                    kotlinx.coroutines.delay(delaySeconds * 1000)
                    lastException = Exception("Rate limit exceeded")
                }
                
                else -> {
                    // Other error - don't retry
                    val errorBody = response.errorBody()?.string()
                    Log.e("SummaryRepository", "Gemini summary API error: code=${response.code()} body=$errorBody")
                    throw Exception("Failed to generate summary: ${response.message()}")
                }
            }
        }
        
        // Shouldn't reach here, but handle it anyway
        throw lastException ?: Exception("Failed to generate summary after $maxRetries attempts")
    }
}

