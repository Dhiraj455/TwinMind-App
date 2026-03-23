package com.example.twinmind2.recording

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.twinmind2.data.dao.RecordingDao
import com.example.twinmind2.data.dao.SummaryDao
import com.example.twinmind2.data.entity.AudioChunk
import com.example.twinmind2.data.entity.RecordingSession
import com.example.twinmind2.summary.SummaryWorker
import com.example.twinmind2.transcription.TranscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@HiltWorker
class RecordingRecoveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recordingDao: RecordingDao,
    private val summaryDao: SummaryDao,
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val activeSessions = recordingDao.getActiveSessions()
        val pendingOrFailed = transcriptionRepository.getPendingTranscripts()
        val pendingSessionIds = pendingOrFailed.map { it.sessionId }.toSet()
        val activeSessionIds = activeSessions.map { it.id }.toSet()
        val allSessionIdsToRecover = (activeSessionIds + pendingSessionIds).toList()
        if (allSessionIdsToRecover.isEmpty()) return Result.success()

        var shouldRetry = false

        allSessionIdsToRecover.forEach { sessionId ->
            try {
                val session = recordingDao.getSession(sessionId) ?: return@forEach
                val chunks = recordingDao.getChunksForSession(session.id)
                if (session.status == "active") {
                    val completeAudioPath = finalizeSessionAudioIfNeeded(session, chunks)
                    stopSession(session, completeAudioPath)
                }

                if (chunks.isNotEmpty()) {
                    transcribeIncompleteChunks(session.id, chunks)
                    val hasIncomplete = hasIncompleteTranscripts(session.id, chunks)
                    if (hasIncomplete) {
                        shouldRetry = true
                    } else {
                        // Only enqueue summary if one doesn't already exist or completed.
                        val existingSummary = summaryDao.getSummary(session.id)
                        if (existingSummary == null || existingSummary.status == "failed") {
                            SummaryWorker.enqueue(applicationContext, session.id, replaceExisting = false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recovery failed for session $sessionId", e)
                shouldRetry = true
            }
        }

        return if (shouldRetry) Result.retry() else Result.success()
    }

    private suspend fun stopSession(session: RecordingSession, completeAudioPath: String?) {
        val fresh = recordingDao.getSession(session.id) ?: return
        recordingDao.updateSession(
            fresh.copy(
                status = "stopped",
                endTimeMs = fresh.endTimeMs ?: System.currentTimeMillis(),
                completeAudioPath = completeAudioPath ?: fresh.completeAudioPath
            )
        )
    }

    private fun finalizeSessionAudioIfNeeded(
        session: RecordingSession,
        chunks: List<AudioChunk>
    ): String? {
        if (chunks.isEmpty()) return session.completeAudioPath

        val existingPath = session.completeAudioPath
        if (!existingPath.isNullOrBlank() && File(existingPath).exists()) {
            return existingPath
        }

        val allPcmData = mutableListOf<ByteArray>()
        chunks.sortedBy { it.indexInSession }.forEach { chunk ->
            val chunkFile = File(chunk.filePath)
            if (chunkFile.exists()) {
                allPcmData.add(extractPcmFromWav(chunkFile))
            }
        }

        if (allPcmData.isEmpty()) return existingPath

        val combinedPcm = allPcmData.reduce { acc, bytes -> acc + bytes }
        val outputFile = File(applicationContext.filesDir, "recordings/${session.id}/complete_audio.wav")
        outputFile.parentFile?.mkdirs()
        writeWav(outputFile, combinedPcm, sampleRate = 16_000)
        return outputFile.absolutePath
    }

    private suspend fun transcribeIncompleteChunks(sessionId: Long, chunks: List<AudioChunk>) {
        chunks.sortedBy { it.indexInSession }.forEach { chunk ->
            val transcript = transcriptionRepository.getTranscriptForChunk(sessionId, chunk.id)
            if (transcript?.status == "completed") return@forEach

            if (transcript == null) {
                transcriptionRepository.createPendingTranscript(chunk)
            }

            retryTranscription(sessionId, chunk)
        }
    }

    private suspend fun retryTranscription(sessionId: Long, chunk: AudioChunk) {
        val maxRetries = 3
        var attempt = 0
        var lastError: String? = null

        while (attempt < maxRetries) {
            val result = transcriptionRepository.transcribeChunk(chunk)
            var isSuccess = false
            result.fold(
                onSuccess = { rawText ->
                    val cleanedText = runCatching {
                        transcriptionRepository.contextCorrectTranscript(sessionId, rawText).getOrDefault(rawText)
                    }.getOrDefault(rawText)
                    transcriptionRepository.updateTranscriptSuccess(
                        sessionId = sessionId,
                        chunkId = chunk.id,
                        rawText = rawText,
                        cleanedText = cleanedText
                    )
                    isSuccess = true
                },
                onFailure = { error ->
                    lastError = error.message ?: "Unknown transcription error"
                }
            )
            if (isSuccess) return

            attempt++
            if (attempt < maxRetries) {
                delay((2L shl (attempt - 1)) * 1000L)
            }
        }

        transcriptionRepository.updateTranscriptFailure(
            sessionId = sessionId,
            chunkId = chunk.id,
            errorMessage = "Recovery retry failed: ${lastError ?: "Unknown error"}"
        )
    }

    private suspend fun hasIncompleteTranscripts(sessionId: Long, chunks: List<AudioChunk>): Boolean {
        return chunks.any { chunk ->
            val transcript = transcriptionRepository.getTranscriptForChunk(sessionId, chunk.id)
            transcript == null || transcript.status != "completed"
        }
    }

    private fun writeWav(file: File, pcmData: ByteArray, sampleRate: Int) {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8

        FileOutputStream(file).use { out ->
            out.write(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
            out.write(intToLittleEndian(totalDataLen.toInt()))
            out.write(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
            out.write(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
            out.write(intToLittleEndian(16))
            out.write(shortToLittleEndian(1))
            out.write(shortToLittleEndian(channels.toShort()))
            out.write(intToLittleEndian(sampleRate))
            out.write(intToLittleEndian(byteRate))
            out.write(shortToLittleEndian((channels * 16 / 8).toShort()))
            out.write(shortToLittleEndian(16.toShort()))
            out.write(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
            out.write(intToLittleEndian(totalAudioLen.toInt()))
            out.write(pcmData)
        }
    }

    private fun extractPcmFromWav(wavFile: File): ByteArray {
        val bytes = wavFile.readBytes()
        var dataStart = -1
        for (i in 0 until bytes.size - 4) {
            if (bytes[i] == 'd'.code.toByte() &&
                bytes[i + 1] == 'a'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() &&
                bytes[i + 3] == 'a'.code.toByte()
            ) {
                dataStart = i + 8
                break
            }
        }
        if (dataStart == -1) throw IllegalStateException("Invalid WAV file: no data chunk found")

        val dataSizeBytes = bytes.sliceArray(dataStart - 4 until dataStart)
        val dataSize = ByteBuffer.wrap(dataSizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        return bytes.sliceArray(dataStart until (dataStart + dataSize).coerceAtMost(bytes.size))
    }

    private fun intToLittleEndian(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun shortToLittleEndian(value: Short): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()

    companion object {
        private const val UNIQUE_WORK_NAME = "recording_recovery_work"
        private const val TAG = "RecordingRecoveryWorker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<RecordingRecoveryWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

