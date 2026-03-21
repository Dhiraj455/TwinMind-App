package com.example.twinmind2.summary

import android.content.Context
import android.util.Log
import com.example.twinmind2.data.dao.RecordingDao
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
    private val recordingDao: RecordingDao,
    private val summaryDao: SummaryDao,
    private val transcriptDao: TranscriptDao,
    private val transcriptionApi: TranscriptionApi
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apiKey: String = NetworkModule.getGeminiApiKey()
    private val jobs = ConcurrentHashMap<Long, Job>()

    private data class CombinedSummaryResult(
        val title: String,
        val summary: String,
        val actionItems: String,
        val keyPoints: String,
        val tags: String
    )

    fun observeSummary(sessionId: Long): Flow<Summary?> =
        summaryDao.observeSummary(sessionId)

    suspend fun getSummary(sessionId: Long): Summary? =
        summaryDao.getSummary(sessionId)

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
        // 1. Enqueue WorkManager worker FIRST as a durable backup that survives process death.
        //    Uses KEEP so if in-process finishes first the worker exits via idempotency check.
        SummaryWorker.enqueue(context, sessionId, replaceExisting = true)

        // 2. Also run immediately in-process for a fast, responsive UI experience.
        val existing = jobs[sessionId]
        if (existing?.isActive == true) return

        val job = scope.launch {
            runSummaryGeneration(sessionId, force = true)
        }
        jobs[sessionId] = job
        job.invokeOnCompletion { jobs.remove(sessionId) }
    }

    suspend fun runSummaryGeneration(sessionId: Long, force: Boolean = false) {
        try {
            // Skip if already completed and not a forced re-generation.
            if (!force) {
                val existing = summaryDao.getSummary(sessionId)
                if (existing?.status == "completed") return
            }
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
            val combined = generateCombinedSummary(safeTranscriptText)
            updateSection(sessionId, SummarySection.TITLE, combined.title, 1)
            updateSection(sessionId, SummarySection.SUMMARY, combined.summary, 2)
            updateSection(sessionId, SummarySection.ACTION_ITEMS, combined.actionItems, 3)
            updateSection(sessionId, SummarySection.KEY_POINTS, combined.keyPoints, 4)
            val generatedTags = normalizeTagList(combined.tags)

            markCompleted(sessionId)
            val finalSummary = summaryDao.getSummary(sessionId)
            recordingDao.updateSessionTitleAndTags(
                sessionId = sessionId,
                title = finalSummary?.title,
                tags = generatedTags
            )
        } catch (e: Exception) {
            Log.e("SummaryRepository", "Error generating summary", e)
            markFailed(sessionId, e.message ?: "Unknown error")
        }
    }

    private suspend fun generateCombinedSummary(transcript: String): CombinedSummaryResult {
        val request = TranscriptionApi.GenerateContentRequest(
            contents = listOf(
                TranscriptionApi.Content(
                    parts = listOf(
                        TranscriptionApi.Part(
                            text = """
You are an assistant creating meeting summaries.
Return ALL sections in ONE response using EXACT headers below:

TITLE:
<Generate a concise meeting title based on the transcript. Respond with the title only.>

SUMMARY:
<Provide a concise meeting summary in 3-4 sentences.> 

ACTION_ITEMS:
<List clear action items with owners when possible. Use bullet points. If none, respond with 'None'.>

KEY_POINTS:
<List key decisions or important points as bullet points.>

TAGS:
<comma-separated tags from: work, meeting, personal, ideas, study, call, planning, brainstorm, interview, lecture, other>

Rules:
- Use only these headers exactly once each.
- Keep ACTION_ITEMS and KEY_POINTS as bullet lines.
- If no action items, write "- None".
- Use 1-3 tags only.

Transcript:
$transcript
                            """.trimIndent()
                        )
                    )
                )
            ),
            generationConfig = TranscriptionApi.GenerationConfig(
                response_mime_type = "text/plain"
            )
        )

        var attempt = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            val response = transcriptionApi.generateContent(apiKey, request)
            
            when {
                response.isSuccessful && response.body() != null -> {
                    val candidates = response.body()?.candidates
                    val content = candidates?.firstOrNull()?.content
                    val textPart = content?.parts?.firstOrNull { !it.text.isNullOrBlank() }
                    val responseText = textPart?.text?.trim() ?: throw Exception("Empty response from summary API")
                    return parseCombinedSummary(responseText)
                }
                
                response.code() == 429 -> {
                    attempt++
                    if (attempt >= maxRetries) {
                        val errorBody = response.errorBody()?.string()
                        Log.e("SummaryRepository", "Rate limit exceeded after $maxRetries attempts: body=$errorBody")
                        throw Exception("Rate limit exceeded. Please try again in a few minutes.")
                    }
                    val delaySeconds = 2L * (1 shl (attempt - 1))
                    Log.w("SummaryRepository", "Rate limited (429), retrying in ${delaySeconds}s (attempt $attempt/$maxRetries)")
                    kotlinx.coroutines.delay(delaySeconds * 1000)
                    lastException = Exception("Rate limit exceeded")
                }
                
                else -> {
                    val errorBody = response.errorBody()?.string()
                    Log.e("SummaryRepository", "Gemini summary API error: code=${response.code()} body=$errorBody")
                    throw Exception("Failed to generate summary: ${response.message()}")
                }
            }
        }
        throw lastException ?: Exception("Failed to generate summary after $maxRetries attempts")
    }

    private fun parseCombinedSummary(text: String): CombinedSummaryResult {
        val title = extractSection(text, "TITLE", listOf("SUMMARY"))
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .ifBlank { "Meeting Summary" }

        val summary = extractSection(text, "SUMMARY", listOf("ACTION_ITEMS"))
            .trim()
            .ifBlank { "Summary not available." }

        val actionItems = normalizeBullets(
            extractSection(text, "ACTION_ITEMS", listOf("KEY_POINTS"))
        ).ifBlank { "None" }

        val keyPoints = normalizeBullets(
            extractSection(text, "KEY_POINTS", listOf("TAGS"))
        ).ifBlank { "None" }

        val tags = extractSection(text, "TAGS", emptyList())
            .split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(",")

        return CombinedSummaryResult(
            title = title,
            summary = summary,
            actionItems = actionItems,
            keyPoints = keyPoints,
            tags = tags
        )
    }

    private fun extractSection(text: String, header: String, nextHeaders: List<String>): String {
        // Match the header at the start of a line, with optional trailing space/content on same line.
        val headerRegex = Regex("(?im)^$header:\\s*")
        val startMatch = headerRegex.find(text) ?: return ""
        val start = startMatch.range.last + 1
        val remainder = text.substring(start)

        if (nextHeaders.isEmpty()) return remainder.trim()

        // Also match next headers that may have content on the same line (e.g. "TAGS: work")
        val nextRegex = Regex(
            "(?im)^(${nextHeaders.joinToString("|") { Regex.escape(it) }}):\\s*"
        )
        val nextMatch = nextRegex.find(remainder)
        val end = nextMatch?.range?.first ?: remainder.length
        return remainder.substring(0, end).trim()
    }

    private fun normalizeBullets(raw: String): String {
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                when {
                    line.startsWith("- ") -> line.removePrefix("- ").trim()
                    line.startsWith("* ") -> line.removePrefix("* ").trim()
                    line.startsWith("• ") -> line.removePrefix("• ").trim()
                    else -> line
                }
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun normalizeTagList(rawTags: String): String? {
        val allowedTags = setOf(
            "work", "meeting", "personal", "ideas", "study",
            "call", "planning", "brainstorm", "interview", "lecture", "other"
        )
        val normalized = rawTags
            .split(",", "\n")
            .map { it.trim().lowercase() }
            .map { it.removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }
            .filter { it in allowedTags }
            .distinct()
            .take(3)
        return normalized.takeIf { it.isNotEmpty() }?.joinToString(",")
    }
}

