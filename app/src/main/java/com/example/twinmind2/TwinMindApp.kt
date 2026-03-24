package com.example.twinmind2

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.twinmind2.data.dao.SummaryDao
import com.example.twinmind2.recording.RecordingRecoveryWorker
import com.example.twinmind2.summary.SummaryWorker
import com.example.twinmind2.wakeword.WakeWordPreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TwinMindApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var summaryDao: SummaryDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun startWakeWordIfPrefsOn() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                WakeWordPreferences.startIfEnabled(this@TwinMindApp)
            } catch (e: Exception) {
                android.util.Log.e("TwinMindApp", "WakeWord startIfEnabled failed", e)
            }
        }
    }

    init {
        android.util.Log.d("TwinMindApp", "TwinMindApp init block")
    }

    override fun onCreate() {
        super.onCreate()
        
        android.util.Log.d("TwinMindApp", "=== TwinMindApp.onCreate() START ===")

        android.util.Log.d("TwinMindApp", "Checking HiltWorkerFactory: initialized=${::workerFactory.isInitialized}")
        
        if (!::workerFactory.isInitialized) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (::workerFactory.isInitialized) {
                    android.util.Log.d("TwinMindApp", "Factory now initialized after delay - initializing WorkManager")
                    initializeWorkManager()
                    RecordingRecoveryWorker.enqueue(this@TwinMindApp)
                    resumeStuckSummaries()
                    startWakeWordIfPrefsOn()
                } else {
                    android.util.Log.e("TwinMindApp", "Factory STILL not initialized - Hilt setup failed!")
                }
            }, 100)
            startWakeWordIfPrefsOn()
            return
        }
        
        // Factory is ready - initialize WorkManager IMMEDIATELY
        initializeWorkManager()
        RecordingRecoveryWorker.enqueue(this)
        resumeStuckSummaries()
        startWakeWordIfPrefsOn()
    }

    private fun resumeStuckSummaries() {
        appScope.launch {
            try {
                // Any summary left in 'generating' means the process was killed mid-run.
                // Re-enqueue their workers so they complete on restart.
                val stuckSessionIds = summaryDao.getSessionIdsByStatus("generating")
                stuckSessionIds.forEach { sessionId ->
                    android.util.Log.d("TwinMindApp", "Resuming stuck summary for session $sessionId")
                    SummaryWorker.enqueue(this@TwinMindApp, sessionId, replaceExisting = true)
                }
            } catch (e: Exception) {
                android.util.Log.e("TwinMindApp", "Failed to resume stuck summaries", e)
            }
        }
    }

    private fun initializeWorkManager() {
        android.util.Log.d("TwinMindApp", "initializeWorkManager() called")
        
        if (!::workerFactory.isInitialized) {
            android.util.Log.e("TwinMindApp", "Cannot initialize - factory not ready")
            return
        }

        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        
        try {
            WorkManager.initialize(this, config)
            android.util.Log.d("TwinMindApp", "WorkManager.initialize() SUCCESS!")
        } catch (e: IllegalStateException) {
            // WorkManager already initialized
            android.util.Log.e("TwinMindApp", "WorkManager ALREADY INITIALIZED!")
            android.util.Log.e("TwinMindApp", "Error: ${e.message}")
            e.printStackTrace()
            
            try {
                val wm = WorkManager.getInstance(this)
                android.util.Log.d("TwinMindApp", "Existing WorkManager instance: $wm")
            } catch (ex: Exception) {
                android.util.Log.e("TwinMindApp", "Cannot get WorkManager instance: ${ex.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("TwinMindApp", "Unexpected error: ${e.message}", e)
            e.printStackTrace()
        }
    }

    override val workManagerConfiguration: Configuration
        get() {
            android.util.Log.d("TwinMindApp", "getWorkManagerConfiguration() called - factory ready: ${::workerFactory.isInitialized}")
            
            if (!::workerFactory.isInitialized) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (::workerFactory.isInitialized) {
                        android.util.Log.d("TwinMindApp", "Factory now ready - but WorkManager already initialized without it")
                    }
                }
                
                return Configuration.Builder()
                    .setMinimumLoggingLevel(android.util.Log.DEBUG)
                    .build()
            }
            
            android.util.Log.d("TwinMindApp", "Providing Configuration with HiltWorkerFactory")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
        }
}
