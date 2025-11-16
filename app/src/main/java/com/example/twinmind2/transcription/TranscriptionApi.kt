package com.example.twinmind2.transcription

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Google Gemini 2.5 Flash API interface for audio transcription
 * Base URL: https://generativelanguage.googleapis.com/v1beta/
 * 
 * Process:
 * 1. Upload file to get file URI
 * 2. Use generateContent with file URI and transcription prompt
 * 
 * Authorization: API key as query parameter ?key=YOUR_KEY
 */
interface TranscriptionApi {
    /**
     * Note: File upload is done via OkHttp in TranscriptionRepository
     * because Retrofit doesn't handle multipart uploads to different base URLs well
     */

    /**
     * Generate content (transcription) from uploaded file
     */
    @POST("models/gemini-2.0-flash-exp:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): Response<GenerateContentResponse>

    // Request/Response data classes
    data class FileUploadRequest(
        val file: FileData,
        val file_config: FileConfig
    )

    data class FileData(
        val file_uri: String? = null,
        val mime_type: String
    )

    data class FileConfig(
        val mime_types: List<String>
    )

    data class FileUploadResponse(
        val file: FileMetadata?
    )

    data class FileMetadata(
        val uri: String,
        val mime_type: String
    )

    data class GenerateContentRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null
    )

    data class Content(
        val parts: List<Part>
    )

    data class Part(
        val file_data: FileData? = null,
        val text: String? = null
    )

    data class GenerationConfig(
        val response_mime_type: String? = "text/plain"
    )

    data class GenerateContentResponse(
        val candidates: List<Candidate>?
    )

    data class Candidate(
        val content: Content?,
        val finishReason: String? = null
    )

    data class TranscriptionResponse(
        val text: String,
        val language: String? = null
    )
}

