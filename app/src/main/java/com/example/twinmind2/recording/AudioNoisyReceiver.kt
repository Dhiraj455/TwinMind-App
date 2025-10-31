package com.example.twinmind2.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AudioNoisyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Forward to service to handle source changes gracefully
        context.startService(Intent(context, RecordingService::class.java).setAction("SOURCE_CHANGED"))
    }
}


