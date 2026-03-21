package com.example.twinmind2.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AudioNoisyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sourceMessage = when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> "Recording source changed - Wired headset"
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED" ->
                "Recording source changed - Bluetooth headset"
            "android.media.ACTION_SCO_AUDIO_STATE_UPDATED" ->
                "Recording source changed - Bluetooth audio"
            else -> "Recording source changed"
        }
        context.startService(
            Intent(context, RecordingService::class.java)
                .setAction("SOURCE_CHANGED")
                .putExtra("source_message", sourceMessage)
        )
    }
}


