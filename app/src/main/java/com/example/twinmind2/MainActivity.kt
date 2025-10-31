package com.example.twinmind2

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.twinmind2.recording.RecordingNotifications
import com.example.twinmind2.recording.RecordingService
import com.example.twinmind2.recording.RecordingViewModel
import com.example.twinmind2.ui.theme.TwinMind2Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TwinMind2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecordingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
        requestAllRuntimePermissions()
    }

    private fun requestAllRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        requestPermission.launch(permissions)
    }

    @Composable
    private fun RecordingScreen(modifier: Modifier = Modifier) {
        val vm: RecordingViewModel = hiltViewModel()
        val state = vm.recordingState.collectAsState()
        val sessions = vm.sessions.collectAsState()
        val expandedState = remember { mutableStateOf(setOf<Long>()) }

        Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.Top) {
            val mm = (state.value.elapsedSec / 60).toString().padStart(2, '0')
            val ss = (state.value.elapsedSec % 60).toString().padStart(2, '0')
            Text(text = "Status: ${state.value.status}")
            Spacer(Modifier.height(4.dp))
            Text(text = "Timer: $mm:$ss")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val currentlyRecording = state.value.activeSessionId != null
                if (!currentlyRecording) {
                    val intent = Intent(this@MainActivity, RecordingService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                } else {
                    val intent = Intent(this@MainActivity, RecordingService::class.java).setAction(RecordingNotifications.ACTION_STOP)
                    startService(intent)
                }
            }) { Text(if (state.value.activeSessionId != null) "Stop" else "Record") }

            Spacer(Modifier.height(24.dp))
            Text("Meetings (sessions):")
            sessions.value.forEach { session ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Session #${session.id} • ${session.status} • ${session.startTimeMs}",
                    modifier = Modifier
                        .clickable {
                            expandedState.value = if (expandedState.value.contains(session.id)) {
                                expandedState.value - session.id
                            } else {
                                expandedState.value + session.id
                            }
                        }
                        .padding(vertical = 4.dp)
                )
                if (expandedState.value.contains(session.id)) {
                    // Show complete audio file if available
                    session.completeAudioPath?.let { completePath ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = " 🎵 Complete Audio (${completePath.split("/").lastOrNull() ?: "N/A"})",
                            modifier = Modifier
                                .clickable { openAudio(completePath) }
                                .padding(start = 8.dp)
                        )
                    }
                    SessionChunksList(sessionId = session.id)
                }
            }
        }
        LaunchedEffect(Unit) { RecordingNotifications.ensureChannels(this@MainActivity) }
    }

    @Composable
    private fun SessionChunksList(sessionId: Long, vm: RecordingViewModel = hiltViewModel()) {
        val chunks = vm.chunksFor(sessionId).collectAsState(initial = emptyList())
        chunks.value.forEach { chunk ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = " - Chunk ${chunk.indexInSession} • ${chunk.durationMs}ms\n   ${chunk.filePath}",
                modifier = Modifier
                    .clickable { openAudio(chunk.filePath) }
                    .padding(start = 8.dp)
            )
        }
    }

    private fun openAudio(path: String) {
        // This will be wired via FileProvider in the next step
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            this.packageName + ".provider",
            java.io.File(path)
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/wav")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }
}