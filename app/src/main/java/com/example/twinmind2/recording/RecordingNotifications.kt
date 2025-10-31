package com.example.twinmind2.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.twinmind2.MainActivity
import com.example.twinmind2.R

object RecordingNotifications {
    const val CHANNEL_ID_RECORDING = "recording_channel"
    const val NOTIF_ID_RECORDING = 1001
    const val ACTION_STOP = "com.example.twinmind2.action.STOP"
    const val ACTION_PAUSE = "com.example.twinmind2.action.PAUSE"
    const val ACTION_RESUME = "com.example.twinmind2.action.RESUME"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID_RECORDING,
                context.getString(R.string.app_name) + " Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }

    fun buildRecordingNotification(
        context: Context,
        status: String,
        elapsedText: String,
        showPause: Boolean,
        showResume: Boolean
    ): Notification {
        ensureChannels(context)
        val mainIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, RecordingService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val resumeIntent = PendingIntent.getService(
            context,
            3,
            Intent(context, RecordingService::class.java).setAction(ACTION_RESUME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_RECORDING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Recording")
            .setContentText("$status • $elapsedText")
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (showPause) builder.addAction(0, "Pause", pauseIntent)
        if (showResume) builder.addAction(0, "Resume", resumeIntent)
        builder.addAction(0, "Stop", stopIntent)

        return builder.build()
    }
}


