package com.example.twinmind2.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Recovery functionality can be added here if needed
        // For now, this is a placeholder
        Log.d("StartupReceiver", "Boot completed - recovery can be implemented here")
    }
}


