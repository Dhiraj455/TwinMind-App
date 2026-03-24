package com.example.twinmind2.wakeword

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.twinmind2.MainActivity
import com.example.twinmind2.R
import com.example.twinmind2.recording.RecordingNotifications
import com.example.twinmind2.recording.RecordingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

class WakeWordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val listenLock = Any()
    private var listenJob: Job? = null

    @Volatile
    private var restartPending: Boolean = false

    private var recordingStoppedReceiver: BroadcastReceiver? = null

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    private val lastTriggerMs = AtomicLong(0L)

    companion object {
        private const val TAG = "WakeWordService"
        const val CHANNEL_ID = "wakeword_channel"
        const val NOTIF_ID = 1002

        const val ACTION_START = "com.example.twinmind2.wakeword.START"
        const val ACTION_STOP = "com.example.twinmind2.wakeword.STOP"

        // Phrases to detect (case-insensitive)
        private const val TARGET_START = "hey twin start"
        private const val TARGET_STOP = "hey twin stop"
        // Include shorter "hey twin stop" - often more reliably recognized than the full phrase.
        // "hey twin stock" handles common misrecognition of "stop".
        private const val GRAMMAR_JSON =
            """["hey twin stop recording", "hey twin stop", "hey twin stock recording", "hey twin stock", "hey twin start recording"]"""
        private const val SAMPLE_RATE = 16000f
        private const val COOLDOWN_MS = 5000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                synchronized(listenLock) {
                    listenJob?.cancel()
                }
                releaseRecognitionResources()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIF_ID, buildNotification())
        registerRecordingStoppedReceiver()
        startOrContinueListening()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterRecordingStoppedReceiver()
        synchronized(listenLock) {
            listenJob?.cancel()
        }
        releaseRecognitionResources()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerRecordingStoppedReceiver() {
        if (recordingStoppedReceiver != null) return
        recordingStoppedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == RecordingNotifications.ACTION_RECORDING_STOPPED &&
                    WakeWordPreferences.isEnabled(this@WakeWordService)
                ) {
                    startOrContinueListening()
                }
            }
        }
        val filter = IntentFilter(RecordingNotifications.ACTION_RECORDING_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordingStoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(recordingStoppedReceiver, filter)
        }
    }

    private fun unregisterRecordingStoppedReceiver() {
        recordingStoppedReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            recordingStoppedReceiver = null
        }
    }

    /** Assigns [listenJob] synchronously so rapid duplicate starts cannot spawn two listeners. */
    private fun startOrContinueListening() {
        synchronized(listenLock) {
            if (listenJob?.isActive == true) return
            listenJob = serviceScope.launch {
                runRecognitionBody()
            }
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hey Twin - Voice activation",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val mainIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Hey Twin")
            .setContentText("Listening for \"Hey Twin Start/Stop Recording\"")
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun releaseRecognitionResources() {
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (_: Exception) {
        }
        speechService = null
        try {
            recognizer?.close()
        } catch (_: Exception) {
        }
        recognizer = null
        try {
            model?.close()
        } catch (_: Exception) {
        }
        model = null
    }

    private suspend fun runRecognitionBody() {
        try {
            val modelPath = extractModelIfNeeded()
            if (modelPath == null) {
                Log.e(TAG, "Failed to extract Vosk model")
                stopSelf()
                return
            }

            model = Model(modelPath)
            recognizer = Recognizer(model!!, SAMPLE_RATE, GRAMMAR_JSON)
            speechService = SpeechService(recognizer!!, SAMPLE_RATE)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let { checkAndTrigger(it) }
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis?.let { checkAndTrigger(it) }
                }

                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let { checkAndTrigger(it) }
                }

                override fun onError(e: Exception?) {
                    Log.e(TAG, "Recognition error", e)
                    scheduleRecognitionRestart()
                }

                override fun onTimeout() {}
            })

            while (coroutineContext.isActive) {
                delay(1000)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Wake word setup failed", e)
            stopSelf()
        } finally {
            releaseRecognitionResources()
        }
    }

    private fun scheduleRecognitionRestart() {
        if (restartPending) return
        restartPending = true
        serviceScope.launch {
            try {
                delay(1200)
                if (!WakeWordPreferences.isEnabled(this@WakeWordService)) {
                    return@launch
                }
                val toJoin = synchronized(listenLock) {
                    val j = listenJob
                    j?.cancel()
                    j
                }
                toJoin?.join()
                releaseRecognitionResources()
                if (WakeWordPreferences.isEnabled(this@WakeWordService)) {
                    startOrContinueListening()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recognition restart failed", e)
            } finally {
                restartPending = false
            }
        }
    }

    private fun extractModelIfNeeded(): String? {
        val destDir = File(filesDir, "vosk_model")
        val sentinel = File(destDir, ".extracted")
        if (sentinel.exists()) return destDir.absolutePath

        return try {
            copyAssetFolder("model", destDir)
            sentinel.createNewFile()
            destDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Model extraction failed", e)
            destDir.deleteRecursively()
            null
        }
    }

    private fun copyAssetFolder(assetPath: String, destDir: File) {
        destDir.mkdirs()
        val entries = assets.list(assetPath) ?: return
        for (name in entries) {
            val childPath = "$assetPath/$name"
            val childDest = File(destDir, name)
            val subList = assets.list(childPath)
            if (subList.isNullOrEmpty()) {
                childDest.parentFile?.mkdirs()
                assets.open(childPath).use { input ->
                    FileOutputStream(childDest).use { input.copyTo(it) }
                }
            } else {
                copyAssetFolder(childPath, childDest)
            }
        }
    }

    private fun checkAndTrigger(text: String) {
        val normalized = normalizeResult(text)
        if (normalized.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs.get() <= COOLDOWN_MS) return

        // Check stop first: "hey twin stop" or "hey twin stock" (common misrecognition)
        when {
            normalized.contains(TARGET_STOP) || normalized.contains("hey twin stock") -> {
                lastTriggerMs.set(now)
                Log.d(TAG, "Detected stop: $normalized")
                triggerStopRecording()
            }
            normalized.contains(TARGET_START) -> {
                lastTriggerMs.set(now)
                Log.d(TAG, "Detected start: $normalized")
                triggerRecording()
            }
        }
    }

    private fun normalizeResult(json: String): String {
        return try {
            val obj = JSONObject(json)
            obj.optString("text", "").lowercase().trim()
        } catch (_: Exception) {
            json.lowercase().trim()
        }
    }

    private fun triggerRecording() {
        val intent = Intent(this, RecordingService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun triggerStopRecording() {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingNotifications.ACTION_STOP
        }
        startService(intent)
    }
}
