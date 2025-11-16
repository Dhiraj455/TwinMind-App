package com.example.twinmind2.recording

import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import com.example.twinmind2.data.entity.AudioChunk
import com.example.twinmind2.transcription.TranscriptionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {
    @Inject lateinit var repository: RecordingRepository
    @Inject lateinit var transcriptionRepository: TranscriptionRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var sessionId: Long? = null
    private var isPaused: Boolean = false
    private var startElapsedMs: Long = 0
    private var pauseStartMs: Long = 0
    private var totalPausedMs: Long = 0
    private var timerJob: Job? = null
    private var recordJob: Job? = null
    @Volatile private var requestedStop: Boolean = false

    private var telephonyManager: TelephonyManager? = null
    private val phoneListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            if (state != TelephonyManager.CALL_STATE_IDLE) {
                if (!isPaused) {
                    pauseStartMs = System.currentTimeMillis()
                }
                isPaused = true
                repository.setPaused(true, "Paused - Phone call")
                updateNotification("Paused - Phone call")
            } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                if (isPaused && pauseStartMs > 0) {
                    totalPausedMs += System.currentTimeMillis() - pauseStartMs
                    pauseStartMs = 0
                }
                isPaused = false
                repository.setPaused(false, "Recording")
                updateNotification("Recording")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            RecordingNotifications.ACTION_STOP -> {
                // Request graceful stop to flush the last partial chunk
                requestedStop = true
                return START_STICKY
            }
            RecordingNotifications.ACTION_PAUSE -> {
                if (!isPaused) {
                    pauseStartMs = System.currentTimeMillis()
                }
                isPaused = true
                repository.setPaused(true, "Paused")
                updateNotification("Paused")
                return START_STICKY
            }
            RecordingNotifications.ACTION_RESUME -> {
                if (isPaused && pauseStartMs > 0) {
                    totalPausedMs += System.currentTimeMillis() - pauseStartMs
                    pauseStartMs = 0
                }
                isPaused = false
                repository.setPaused(false, "Recording")
                updateNotification("Recording")
                return START_STICKY
            }
        }

        if (recordJob == null) {
            startRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        RecordingNotifications.ensureChannels(this)
        startForeground(
            RecordingNotifications.NOTIF_ID_RECORDING,
            RecordingNotifications.buildRecordingNotification(
                this,
                status = "Starting...",
                elapsedText = "00:00",
                showPause = true,
                showResume = false
            )
        )

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        try { telephonyManager?.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE) } catch (_: SecurityException) {}

        serviceScope.launch {
            if (!checkStorageSafe()) {
                updateNotification("Recording stopped - Low storage")
                stopSelfSafely()
                return@launch
            }
            val newSessionId = repository.startSession()
            sessionId = newSessionId
            startElapsedMs = System.currentTimeMillis()
            totalPausedMs = 0
            pauseStartMs = 0
            startTimer()
            doRecordLoop(newSessionId)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                val currentPausedTime = if (isPaused && pauseStartMs > 0) {
                    System.currentTimeMillis() - pauseStartMs
                } else {
                    0
                }
                val elapsed = ((System.currentTimeMillis() - startElapsedMs - totalPausedMs - currentPausedTime) / 1000).toInt()
                val mm = (elapsed / 60).toString().padStart(2, '0')
                val ss = (elapsed % 60).toString().padStart(2, '0')
                repository.updateElapsed(elapsed)
                updateNotification(if (isPaused) "Paused" else "Recording", "$mm:$ss")
                delay(1000)
            }
        }
    }
    
    private suspend fun doRecordLoop(sessionId: Long) {
        recordJob = serviceScope.launch {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBuf.coerceAtLeast(sampleRate)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            val buffer = ShortArray(bufferSize / 2)

            val chunkMs = 30_000L
            val overlapMs = 2_000L
            val bytesPerSecond = sampleRate * 2 // 16-bit mono
            val overlapBytes = (bytesPerSecond * overlapMs / 1000).toInt()
            var lastOverlap: ByteArray = ByteArray(0)
            var chunkIndex = 0

            audioRecord.startRecording()
            var currentChunkStart = System.currentTimeMillis()
            var currentChunkPcm = ByteArray(0)
            var silentAccumulatedMs = 0L

            try {
                while (isActive) {
                    if (requestedStop) break
                    if (isPaused) {
                        delay(100)
                        currentChunkStart = System.currentTimeMillis()
                        continue
                    }
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val bytes = shortArrayToByteArray(buffer, read)
                        currentChunkPcm += bytes

                        // Silence detection (simple RMS threshold)
                        val rms = rms(buffer, read)
                        if (rms < 200) silentAccumulatedMs += (read * 1000L) / (sampleRate)
                        else silentAccumulatedMs = 0
                        if (silentAccumulatedMs >= 10_000L) {
                            repository.setStatus("No audio detected - Check microphone")
                            updateNotification("No audio detected - Check microphone")
                        }

                        val elapsedChunkMs = System.currentTimeMillis() - currentChunkStart
                        if (elapsedChunkMs >= chunkMs) {
                            val pcmToWrite = if (lastOverlap.isNotEmpty()) lastOverlap + currentChunkPcm else currentChunkPcm
                            val file = createChunkFile(sessionId, chunkIndex)
                            writeWav(file, pcmToWrite, sampleRate)
                            val durationMs = elapsedChunkMs + (if (lastOverlap.isNotEmpty()) overlapMs else 0L)
                            val chunkId = repository.addChunk(sessionId, chunkIndex, file, durationMs)
                            
                            // Enqueue transcription work for this chunk
                            transcribeChunk(sessionId, chunkId)
                            
                            // Prepare overlap for next
                            lastOverlap = pcmToWrite.takeLast(overlapBytes).toByteArray()
                            currentChunkPcm = ByteArray(0)
                            currentChunkStart = System.currentTimeMillis()
                            chunkIndex += 1
                            if (!checkStorageSafe()) {
                                repository.setStatus("Recording stopped - Low storage")
                                updateNotification("Recording stopped - Low storage")
                                break
                            }
                        }
                    } else {
                        delay(10)
                    }
                }
            } finally {
                // Flush remaining as a final chunk if any
                if (currentChunkPcm.isNotEmpty()) {
                    val file = createChunkFile(sessionId, chunkIndex)
                    val pcmToWrite = if (lastOverlap.isNotEmpty()) lastOverlap + currentChunkPcm else currentChunkPcm
                    writeWav(file, pcmToWrite, sampleRate)
                    val durationMs = System.currentTimeMillis() - currentChunkStart + (if (lastOverlap.isNotEmpty()) overlapMs else 0L)
                    val chunkId = repository.addChunk(sessionId, chunkIndex, file, durationMs)
                    
                    // Transcribe final chunk
                    transcribeChunk(sessionId, chunkId)
                }
                audioRecord.stop()
                audioRecord.release()
                
                // Concatenate all chunks into complete audio file
                serviceScope.launch {
                    try {
                        val completeFile = concatenateChunksToCompleteAudio(sessionId, sampleRate)
                        repository.updateSessionCompleteAudio(sessionId, completeFile.absolutePath)
                    } catch (e: Exception) {
                        android.util.Log.e("RecordingService", "Failed to create complete audio", e)
                    }
                }
                
                repository.clearActive()
                stopSelfSafely()
            }
        }
    }

    private fun updateNotification(status: String, elapsed: String = "") {
        val notif = RecordingNotifications.buildRecordingNotification(
            this,
            status = status,
            elapsedText = elapsed,
            showPause = !isPaused,
            showResume = isPaused
        )
        NotificationManagerCompat.from(this).notify(RecordingNotifications.NOTIF_ID_RECORDING, notif)
    }

    private fun createChunkFile(sessionId: Long, index: Int): File {
        val dir = repository.getSessionDir(sessionId)
        return File(dir, "chunk_${index.toString().padStart(4, '0')}.wav")
    }

    private fun shortArrayToByteArray(data: ShortArray, length: Int): ByteArray {
        val bb = ByteBuffer.allocate(length * 2)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until length) bb.putShort(data[i])
        return bb.array()
    }

    private fun writeWav(file: File, pcmData: ByteArray, sampleRate: Int) {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8
        FileOutputStream(file).use { out ->
            // RIFF header
            out.write(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
            out.write(intToLittleEndian(totalDataLen.toInt()))
            out.write(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
            out.write(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
            out.write(intToLittleEndian(16)) // Subchunk1Size for PCM
            out.write(shortToLittleEndian(1)) // PCM format
            out.write(shortToLittleEndian(channels.toShort()))
            out.write(intToLittleEndian(sampleRate))
            out.write(intToLittleEndian(byteRate))
            out.write(shortToLittleEndian((channels * 16 / 8).toShort())) // block align
            out.write(shortToLittleEndian(16.toShort())) // bits per sample
            out.write(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
            out.write(intToLittleEndian(totalAudioLen.toInt()))
            out.write(pcmData)
        }
    }

    private suspend fun concatenateChunksToCompleteAudio(sessionId: Long, sampleRate: Int): File {
        val chunks: List<AudioChunk> = repository.observeChunks(sessionId).first()
        if (chunks.isEmpty()) throw IllegalStateException("No chunks to concatenate")
        
        val allPcmData = mutableListOf<ByteArray>()
        chunks.sortedBy { it.indexInSession }.forEach { chunk ->
            val chunkFile = File(chunk.filePath)
            if (chunkFile.exists()) {
                val pcmData = extractPcmFromWav(chunkFile)
                allPcmData.add(pcmData)
            }
        }
        
        val completePcm = allPcmData.reduce { acc, bytes -> acc + bytes }
        val completeFile = File(repository.getSessionDir(sessionId), "complete_audio.wav")
        writeWav(completeFile, completePcm, sampleRate)
        return completeFile
    }
    
    private fun extractPcmFromWav(wavFile: File): ByteArray {
        val bytes = wavFile.readBytes()
        // Find "data" chunk (skip WAV headers)
        var dataStart = -1
        for (i in 0 until bytes.size - 4) {
            if (bytes[i] == 'd'.code.toByte() && bytes[i + 1] == 'a'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() && bytes[i + 3] == 'a'.code.toByte()) {
                dataStart = i + 8 // Skip "data" tag (4 bytes) + size (4 bytes)
                break
            }
        }
        if (dataStart == -1) throw IllegalStateException("Invalid WAV file: no data chunk found")
        
        // Read size of data chunk (4 bytes before PCM data)
        val dataSizeBytes = bytes.sliceArray(dataStart - 4 until dataStart)
        val dataSize = ByteBuffer.wrap(dataSizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        
        return bytes.sliceArray(dataStart until dataStart + dataSize)
    }

    private fun intToLittleEndian(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun shortToLittleEndian(value: Short): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()

    private fun rms(buffer: ShortArray, length: Int): Int {
        var sum = 0.0
        for (i in 0 until length) sum += buffer[i] * buffer[i].toDouble()
        val mean = sum / length.coerceAtLeast(1)
        return kotlin.math.sqrt(mean).toInt()
    }

    private fun transcribeChunk(sessionId: Long, chunkId: Long) {
        android.util.Log.d("RecordingService", "Starting transcription for chunkId=$chunkId, sessionId=$sessionId")
        println("==========================================")
        println("📝 STARTING TRANSCRIPTION 📝")
        println("==========================================")
        println("Chunk ID: $chunkId")
        println("Session ID: $sessionId")
        println("==========================================")
        
        serviceScope.launch {
            try {
                // Get the chunk from database
                val chunks = repository.observeChunks(sessionId).first()
                val chunk = chunks.find { it.id == chunkId }
                
                if (chunk == null) {
                    android.util.Log.e("RecordingService", "Chunk not found: chunkId=$chunkId")
                    return@launch
                }
                
                android.util.Log.d("RecordingService", "Found chunk: ${chunk.filePath}, exists: ${java.io.File(chunk.filePath).exists()}")
                
                // Create pending transcript if it doesn't exist
                val existingTranscript = transcriptionRepository.getTranscriptForChunk(sessionId, chunkId)
                if (existingTranscript == null) {
                    transcriptionRepository.createPendingTranscript(chunk)
                }
                
                // Transcribe with retry logic
                transcribeWithRetry(sessionId, chunkId, chunk, retryCount = 0)
                
            } catch (e: Exception) {
                android.util.Log.e("RecordingService", "Error in transcription coroutine", e)
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun transcribeWithRetry(
        sessionId: Long,
        chunkId: Long,
        chunk: AudioChunk,
        retryCount: Int,
        maxRetries: Int = 3
    ) {
        android.util.Log.d("RecordingService", "Transcribing chunk (attempt ${retryCount + 1}/$maxRetries)")
        
        val result = transcriptionRepository.transcribeChunk(chunk)
        
        result.fold(
            onSuccess = { text ->
                android.util.Log.d("RecordingService", "✅ Transcription successful: $text")
                transcriptionRepository.updateTranscriptSuccess(sessionId, chunkId, text)
                println("==========================================")
                println("✅ TRANSCRIPTION SUCCESS ✅")
                println("==========================================")
                println("Text: $text")
                println("==========================================")
            },
            onFailure = { error ->
                android.util.Log.e("RecordingService", "Transcription failed (attempt ${retryCount + 1}/$maxRetries): ${error.message}")
                
                if (retryCount < maxRetries) {
                    val nextRetryCount = retryCount + 1
                    transcriptionRepository.updateTranscriptFailure(
                        sessionId,
                        chunkId,
                        "Retrying... ($nextRetryCount/$maxRetries): ${error.message}"
                    )
                    
                    // Exponential backoff: 5s, 10s, 20s
                    val delaySeconds = when (retryCount) {
                        0 -> 5L
                        1 -> 10L
                        2 -> 20L
                        else -> 30L
                    }
                    
                    android.util.Log.d("RecordingService", "Retrying in ${delaySeconds}s")
                    delay(delaySeconds * 1000)
                    
                    // Retry
                    transcribeWithRetry(sessionId, chunkId, chunk, nextRetryCount, maxRetries)
                } else {
                    // Max retries reached
                    android.util.Log.e("RecordingService", "❌ Transcription failed after $maxRetries retries")
                    transcriptionRepository.updateTranscriptFailure(
                        sessionId,
                        chunkId,
                        "Failed after $maxRetries retries: ${error.message}"
                    )
                    println("==========================================")
                    println("❌ TRANSCRIPTION FAILED ❌")
                    println("==========================================")
                    println("After $maxRetries retries: ${error.message}")
                    println("==========================================")
                }
            }
        )
    }

    private fun checkStorageSafe(): Boolean {
        val usable = filesDir.usableSpace
        // Require at least ~20MB free to start/continue
        return usable > 20L * 1024L * 1024L
    }

    private fun stopSelfSafely() {
        timerJob?.cancel()
        telephonyManager?.listen(phoneListener, PhoneStateListener.LISTEN_NONE)
        // Ensure UI updates immediately even if recording loop finalizer didn't run yet
        repository.clearActive()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}


