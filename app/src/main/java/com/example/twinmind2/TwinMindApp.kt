package com.example.twinmind2

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TwinMindApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    init {
        // This runs BEFORE onCreate, so factory won't be ready yet
        android.util.Log.d("TwinMindApp", "TwinMindApp init block")
    }

    override fun onCreate() {
        super.onCreate()
        
        android.util.Log.d("TwinMindApp", "=== TwinMindApp.onCreate() START ===")
        
        // Hilt injection happens AFTER super.onCreate() completes
        // So at this point, @Inject fields should be initialized
        android.util.Log.d("TwinMindApp", "Checking HiltWorkerFactory: initialized=${::workerFactory.isInitialized}")
        
        if (!::workerFactory.isInitialized) {
            android.util.Log.e("TwinMindApp", "❌❌❌ FATAL: HiltWorkerFactory not initialized!")
            android.util.Log.e("TwinMindApp", "This means Hilt @Inject failed - check Hilt setup!")
            // Try again after a brief delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (::workerFactory.isInitialized) {
                    android.util.Log.d("TwinMindApp", "Factory now initialized after delay - initializing WorkManager")
                    initializeWorkManager()
                } else {
                    android.util.Log.e("TwinMindApp", "Factory STILL not initialized - Hilt setup failed!")
                }
            }, 100)
            return
        }
        
        // Factory is ready - initialize WorkManager IMMEDIATELY
        initializeWorkManager()
        
        android.util.Log.d("TwinMindApp", "=== TwinMindApp.onCreate() END ===")
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
            android.util.Log.d("TwinMindApp", "✅✅✅ WorkManager.initialize() SUCCESS!")
            android.util.Log.d("TwinMindApp", "WorkManager now has HiltWorkerFactory configured")
        } catch (e: IllegalStateException) {
            // WorkManager already initialized
            android.util.Log.e("TwinMindApp", "❌❌❌ WorkManager ALREADY INITIALIZED!")
            android.util.Log.e("TwinMindApp", "This means getInstance() was called BEFORE onCreate() finished")
            android.util.Log.e("TwinMindApp", "Or WorkManager auto-initialized despite manifest config")
            android.util.Log.e("TwinMindApp", "Error: ${e.message}")
            android.util.Log.e("TwinMindApp", "Stack trace:")
            e.printStackTrace()
            
            // Try to get the existing instance and check its config
            try {
                val wm = WorkManager.getInstance(this)
                android.util.Log.d("TwinMindApp", "Existing WorkManager instance: $wm")
            } catch (ex: Exception) {
                android.util.Log.e("TwinMindApp", "Cannot get WorkManager instance: ${ex.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("TwinMindApp", "❌ Unexpected error: ${e.message}", e)
            e.printStackTrace()
        }
    }

    override val workManagerConfiguration: Configuration
        get() {
            android.util.Log.d("TwinMindApp", "getWorkManagerConfiguration() called - factory ready: ${::workerFactory.isInitialized}")
            
            if (!::workerFactory.isInitialized) {
                android.util.Log.e("TwinMindApp", "❌ CRITICAL: Factory not ready when Configuration requested!")
                android.util.Log.e("TwinMindApp", "WorkManager will initialize WITHOUT factory - workers will fail!")
                
                // Return config without factory (this will cause the error)
                // But also try to initialize later
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (::workerFactory.isInitialized) {
                        android.util.Log.d("TwinMindApp", "Factory now ready - but WorkManager already initialized without it")
                    }
                }
                
                return Configuration.Builder()
                    .setMinimumLoggingLevel(android.util.Log.DEBUG)
                    .build()
            }
            
            android.util.Log.d("TwinMindApp", "✅ Providing Configuration with HiltWorkerFactory")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
        }
}
