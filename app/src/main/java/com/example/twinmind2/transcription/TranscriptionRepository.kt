package com.example.twinmind2.transcription

import android.content.Context
import android.content.SharedPreferences
import com.example.twinmind2.data.dao.TranscriptDao
import com.example.twinmind2.data.entity.AudioChunk
import com.example.twinmind2.data.entity.Transcript
import com.example.twinmind2.di.NetworkModule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val transcriptDao: TranscriptDao,
    private val transcriptionApi: TranscriptionApi,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("twinmind_prefs", Context.MODE_PRIVATE)
    private val apiKey = NetworkModule.getGeminiApiKey(context)
    fun observeTranscriptsForSession(sessionId: Long): Flow<List<Transcript>> =
        transcriptDao.observeTranscriptsForSession(sessionId)

    suspend fun createPendingTranscript(chunk: AudioChunk): Long {
        val transcript = Transcript(
            sessionId = chunk.sessionId,
            chunkId = chunk.id,
            chunkIndex = chunk.indexInSession,
            text = "",
            status = "pending"
        )
        return transcriptDao.insertTranscript(transcript)
    }

    suspend fun transcribeChunk(chunk: AudioChunk): Result<String> {
        android.util.Log.d("TranscriptionRepository", "transcribeChunk called for chunk: ${chunk.id}, path: ${chunk.filePath}")
        return try {
            val file = File(chunk.filePath)
            if (!file.exists()) {
                android.util.Log.e("TranscriptionRepository", "Audio file not found: ${chunk.filePath}")
                return Result.failure(Exception("Audio file not found: ${chunk.filePath}"))
            }
            
            android.util.Log.d("TranscriptionRepository", "File exists: ${file.absolutePath}, size: ${file.length()} bytes")

            android.util.Log.d("TranscriptionRepository", "Calling Google Gemini 2.5 Flash transcription API")
            println("==========================================")
            println("📞 CALLING GOOGLE GEMINI API 📞")
            println("==========================================")
            println("File: ${file.name}, Size: ${file.length()} bytes")
            println("Model: gemini-2.0-flash-exp")
            println("==========================================")

            // Step 1: Upload file to Gemini
            val fileUri = uploadFileToGemini(file)
            
            if (fileUri == null) {
                return Result.failure(Exception("Failed to upload file to Gemini"))
            }

            // Step 2: Generate transcription using the file URI
            val transcriptionResult = generateTranscription(fileUri)
            
            transcriptionResult
        } catch (e: Exception) {
            android.util.Log.e("TranscriptionRepository", "Error in transcribeChunk", e)
            Result.failure(e)
        }
    }

    private suspend fun uploadFileToGemini(file: File): String? {
        var attempt = 0
        val maxRetries = 3
        
        while (attempt < maxRetries) {
            try {
                // Use OkHttp to upload file via multipart
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", """{"file":{"display_name":"${file.name}"}}""")
                    .addFormDataPart(
                        "file",
                        file.name,
                        file.asRequestBody("audio/wav".toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                
                when {
                    response.isSuccessful -> {
                        val responseBody = response.body?.string()
                        android.util.Log.d("TranscriptionRepository", "File upload response: $responseBody")
                        
                        // Parse response to get file URI
                        val gson = com.google.gson.Gson()
                        val uploadResponse = gson.fromJson(responseBody, Map::class.java)
                        val fileData = uploadResponse["file"] as? Map<*, *>
                        val uri = fileData?.get("uri") as? String
                        
                        android.util.Log.d("TranscriptionRepository", "File uploaded successfully, URI: $uri")
                        return uri
                    }
                    
                    response.code == 429 -> {
                        attempt++
                        if (attempt >= maxRetries) {
                            val errorBody = response.body?.string()
                            android.util.Log.e("TranscriptionRepository", "Rate limit exceeded after $maxRetries attempts")
                            return null
                        }
                        
                        val delaySeconds = 2L * (1 shl (attempt - 1))
                        android.util.Log.w("TranscriptionRepository", "Rate limited (429), retrying upload in ${delaySeconds}s (attempt $attempt/$maxRetries)")
                        kotlinx.coroutines.delay(delaySeconds * 1000)
                    }
                    
                    response.code == 403 -> {
                        val errorBody = response.body?.string()
                        android.util.Log.e("TranscriptionRepository", "File upload failed (403): $errorBody")
                        // Check if it's a quota error
                        if (errorBody?.contains("quota", ignoreCase = true) == true) {
                            android.util.Log.e("TranscriptionRepository", "Quota exceeded - user needs to enable billing or wait for quota allocation")
                        }
                        return null
                    }
                    
                    else -> {
                        val errorBody = response.body?.string()
                        android.util.Log.e("TranscriptionRepository", "File upload failed: ${response.code}, $errorBody")
                        return null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TranscriptionRepository", "Error uploading file", e)
                return null
            }
        }
        
        return null
    }

    private suspend fun generateTranscription(fileUri: String): Result<String> {
        return try {
            val request = TranscriptionApi.GenerateContentRequest(
                contents = listOf(
                    TranscriptionApi.Content(
                        parts = listOf(
                            // First part: the audio file
                            TranscriptionApi.Part(
                                file_data = TranscriptionApi.FileData(
                                    file_uri = fileUri,
                                    mime_type = "audio/wav"
                                ),
                                text = null
                            ),
                            // Second part: the transcription instruction
                            TranscriptionApi.Part(
                                file_data = null,
                                text = "Transcribe this audio file into text. Return only the transcription without any additional commentary."
                            )
                        )
                    )
                ),
                generationConfig = TranscriptionApi.GenerationConfig(
                    response_mime_type = "text/plain"
                )
            )

            val response = transcriptionApi.generateContent(apiKey, request)
            
            android.util.Log.d("TranscriptionRepository", "API response: success=${response.isSuccessful}, code=${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val candidates = body.candidates
                
                if (candidates != null && candidates.isNotEmpty()) {
                    val content = candidates[0].content
                    val parts = content?.parts
                    
                    if (parts != null && parts.isNotEmpty()) {
                        val textPart = parts.firstOrNull { it.text != null }
                        val transcriptText = textPart?.text ?: ""
                        
                        android.util.Log.d("TranscriptionRepository", "Transcription result: $transcriptText")
                        println("==========================================")
                        println("✅ GOOGLE GEMINI API RESPONSE RECEIVED ✅")
                        println("==========================================")
                        println("Transcription Text: $transcriptText")
                        println("==========================================")
                        
                        Result.success(transcriptText)
                    } else {
                        Result.failure(Exception("No text parts in response"))
                    }
                } else {
                    Result.failure(Exception("No candidates in response"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                android.util.Log.e("TranscriptionRepository", "API error: code=${response.code()}, message=${response.message()}, body=$errorBody")
                
                // Parse Gemini error response
                val errorMessage = try {
                    val gson = com.google.gson.Gson()
                    val errorJson = gson.fromJson(errorBody, Map::class.java)
                    val errorObj = errorJson["error"] as? Map<*, *>
                    val message = errorObj?.get("message") as? String ?: "Unknown error"
                    
                    // Check for quota exceeded errors
                    if (message.contains("quota", ignoreCase = true) || 
                        message.contains("exceeded", ignoreCase = true) ||
                        message.contains("limit: 0", ignoreCase = true)) {
                        "Quota exceeded: Your Google Gemini API key has no quota allocated. " +
                        "Please enable billing in Google Cloud Console or wait for quota allocation. " +
                        "Visit: https://aistudio.google.com/app/apikey to check your quota."
                    } else {
                        message
                    }
                } catch (e: Exception) {
                    when (response.code()) {
                        401 -> "Invalid API key. Please check your Google Gemini API key at https://aistudio.google.com/app/apikey"
                        403 -> {
                            if (errorBody.contains("quota", ignoreCase = true)) {
                                "Quota exceeded: Enable billing in Google Cloud Console or wait for quota allocation. " +
                                "Visit: https://console.cloud.google.com/apis/api/generativelanguage.googleapis.com/quotas"
                            } else {
                                "Access forbidden. Check your API key permissions and enable the Gemini API in Google Cloud Console."
                            }
                        }
                        429 -> "Rate limit exceeded. Please wait before retrying."
                        else -> "API error: ${response.code()} - ${response.message()}"
                    }
                }
                
                println("==========================================")
                println("❌ GOOGLE GEMINI API ERROR ❌")
                println("==========================================")
                println("Code: ${response.code()}")
                println("Error: $errorMessage")
                println("Full Body: $errorBody")
                println("==========================================")
                
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TranscriptionRepository", "Error generating transcription", e)
            Result.failure(e)
        }
    }

    suspend fun updateTranscriptSuccess(sessionId: Long, chunkId: Long, text: String) {
        val transcript = transcriptDao.getTranscriptForChunk(sessionId, chunkId) ?: return
        
        val updated = transcript.copy(
            text = text,
            status = "completed",
            completedAtMs = System.currentTimeMillis()
        )
        transcriptDao.updateTranscript(updated)
    }

    suspend fun updateTranscriptFailure(sessionId: Long, chunkId: Long, errorMessage: String) {
        val transcript = transcriptDao.getTranscriptForChunk(sessionId, chunkId) ?: return
        
        val updated = transcript.copy(
            status = "failed",
            errorMessage = errorMessage
        )
        transcriptDao.updateTranscript(updated)
    }

    suspend fun getPendingTranscripts(): List<Transcript> = transcriptDao.getPendingTranscripts()

    suspend fun getPendingTranscriptsForSession(sessionId: Long): List<Transcript> =
        transcriptDao.getPendingTranscriptsForSession(sessionId)

    suspend fun getTranscriptForChunk(sessionId: Long, chunkId: Long): Transcript? =
        transcriptDao.getTranscriptForChunk(sessionId, chunkId)
}

