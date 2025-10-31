package com.example.twinmind2.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val work = OneTimeWorkRequestBuilder<RecoveryWorker>().build()
        WorkManager.getInstance(context).enqueue(work)
    }
}


