package com.example.twinmind2.wakeword

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

object WakeWordPreferences {
    private const val PREFS = "wakeword_prefs"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        val intent = Intent(context, WakeWordService::class.java)
        if (enabled) {
            intent.action = WakeWordService.ACTION_START
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            intent.action = WakeWordService.ACTION_STOP
            context.startService(intent)
        }
    }

    fun startIfEnabled(context: Context) {
        if (isEnabled(context)) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = WakeWordService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
