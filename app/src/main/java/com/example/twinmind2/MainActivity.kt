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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.twinmind2.data.entity.Summary
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
        val scrollState = rememberScrollState()

        Column(
            modifier = modifier
                .padding(24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top
        ) {
            Text(text = "Status: ${state.value.status}")
            Spacer(Modifier.height(4.dp))
            Text(text = "Timer: ${(state.value.elapsedSec / 60).toString().padStart(2, '0')}:${(state.value.elapsedSec % 60).toString().padStart(2, '0')}")
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
                    text = "Session #${session.id} • ${session.status}",
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
                    session.completeAudioPath?.let { completePath ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = " 🎵 Complete Audio (${completePath.split("/").lastOrNull() ?: "N/A"})",
                            modifier = Modifier
                                .clickable { openAudio(completePath) }
                                .padding(start = 8.dp)
                        )
                    }
                    SessionSummarySection(sessionId = session.id, vm = vm)
                    SessionChunksList(sessionId = session.id, vm = vm)
                    SessionTranscriptsList(sessionId = session.id, vm = vm)
                }
            }
        }
        LaunchedEffect(Unit) { RecordingNotifications.ensureChannels(this@MainActivity) }
    }

    @Composable
    private fun SessionSummarySection(sessionId: Long, vm: RecordingViewModel) {
        val summaryState = vm.summaryFor(sessionId).collectAsState(initial = null)
        val summary = summaryState.value

        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp)) {
            Text(
                text = "📄 Summary:",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            when (summary?.status) {
                null, "idle" -> {
                    Text(
                        text = "No summary generated yet.",
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.generateSummary(sessionId) }) {
                        Text("Generate Summary")
                    }
                }
                "generating" -> {
                    Text(
                        text = "Generating summary... (${summary.sectionsCompleted}/4 sections ready)",
                        modifier = Modifier.padding(start = 8.dp),
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    SummarySections(summary, isGenerating = true)
                }
                "failed" -> {
                    Text(
                        text = summary.errorMessage ?: "Failed to generate summary.",
                        color = Color(0xFFE53935),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.generateSummary(sessionId) }) {
                        Text("Retry")
                    }
                    if (summary.sectionsCompleted > 0) {
                        Spacer(Modifier.height(8.dp))
                        SummarySections(summary, isGenerating = false)
                    }
                }
                "completed" -> {
                    SummarySections(summary, isGenerating = false)
                }
                else -> SummarySections(summary, isGenerating = summary.status == "generating")
            }
        }
    }

    @Composable
    private fun SummarySections(summaryData: Summary, isGenerating: Boolean) {
        SummarySectionContent(
            heading = "Title",
            content = summaryData.title,
            isLoading = isGenerating && summaryData.title.isNullOrBlank()
        )
        SummarySectionContent(
            heading = "Summary",
            content = summaryData.summary,
            isLoading = isGenerating && summaryData.summary.isNullOrBlank()
        )
        SummarySectionContent(
            heading = "Action Items",
            content = summaryData.actionItems,
            isLoading = isGenerating && summaryData.actionItems.isNullOrBlank(),
            asBullets = true
        )
        SummarySectionContent(
            heading = "Key Points",
            content = summaryData.keyPoints,
            isLoading = isGenerating && summaryData.keyPoints.isNullOrBlank(),
            asBullets = true
        )
    }

    @Composable
    private fun SummarySectionContent(
        heading: String,
        content: String?,
        isLoading: Boolean,
        asBullets: Boolean = false
    ) {
        Text(
            text = heading,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )
        Spacer(Modifier.height(4.dp))

        when {
            isLoading -> Text(
                text = "Loading...",
                color = Color.Gray,
                modifier = Modifier.padding(start = 12.dp)
            )
            content.isNullOrBlank() -> Text(
                text = "Not available",
                color = Color.Gray,
                modifier = Modifier.padding(start = 12.dp)
            )
            asBullets -> {
                val items = content
                    .split('\n')
                    .map { line -> line.trim().trimStart('-', '*', '•').trim() }
                    .filter { it.isNotBlank() }

                if (items.isEmpty()) {
                    Text(text = content, modifier = Modifier.padding(start = 12.dp))
                } else {
                    items.forEach { item ->
                        Text(text = "• $item", modifier = Modifier.padding(start = 16.dp, bottom = 2.dp))
                    }
                }
            }
            else -> Text(text = content, modifier = Modifier.padding(start = 12.dp))
        }
    }

    @Composable
    private fun SessionChunksList(sessionId: Long, vm: RecordingViewModel) {
        val chunks = vm.chunksFor(sessionId).collectAsState(initial = emptyList())
        if (chunks.value.isNotEmpty()) {
            Text("Chunks:", modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        }
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

@Composable
    private fun SessionTranscriptsList(sessionId: Long, vm: RecordingViewModel) {
        val transcripts = vm.transcriptsFor(sessionId).collectAsState(initial = emptyList())
        
        Spacer(Modifier.height(8.dp))
        Text("📝 Transcripts:", modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        
        if (transcripts.value.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "   No transcripts yet. Transcription starts automatically when chunks are created.",
                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                style = androidx.compose.ui.text.TextStyle(
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            )
        } else {
            transcripts.value.sortedBy { it.chunkIndex }.forEach { transcript ->
                Spacer(Modifier.height(6.dp))
                Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
                    when (transcript.status) {
                        "pending" -> {
                            Text(
                                text = "⏳ Chunk ${transcript.chunkIndex}: Transcribing...",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = androidx.compose.ui.graphics.Color(0xFF9E9E9E)
                                )
                            )
                        }
                        "failed" -> {
                            Text(
                                text = "❌ Chunk ${transcript.chunkIndex}: Failed",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = androidx.compose.ui.graphics.Color(0xFFE53935)
                                )
                            )
                            transcript.errorMessage?.let {
                                Text(
                                    text = "   Error: $it",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = androidx.compose.ui.graphics.Color(0xFFE53935),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                        "completed" -> {
                            Text(
                                text = "✓ Chunk ${transcript.chunkIndex}:",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "   ${transcript.text}",
                                modifier = Modifier.padding(start = 4.dp),
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp
                                )
                            )
                        }
                        else -> {
                            Text(
                                text = "• Chunk ${transcript.chunkIndex}: ${transcript.status}",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = androidx.compose.ui.graphics.Color.Gray
                                )
                            )
                        }
                    }
                }
            }
            
            val allCompleted = transcripts.value.isNotEmpty() && 
                transcripts.value.all { it.status == "completed" }
            if (allCompleted) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "📄 Complete Transcript:",
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = transcripts.value.sortedBy { it.chunkIndex }
                        .joinToString(" ") { it.text },
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp
                    )
                )
            } else {
                val completedCount = transcripts.value.count { it.status == "completed" }
                val totalCount = transcripts.value.size
                val pendingCount = transcripts.value.count { it.status == "pending" }
                val failedCount = transcripts.value.count { it.status == "failed" }
                
                Spacer(Modifier.height(8.dp))
    Text(
                    text = "Progress: $completedCount/$totalCount completed${if (pendingCount > 0) ", $pendingCount pending" else ""}${if (failedCount > 0) ", $failedCount failed" else ""}",
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                    style = androidx.compose.ui.text.TextStyle(
                        color = androidx.compose.ui.graphics.Color(0xFF757575),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }

    private fun openAudio(path: String) {
        try {
            val file = java.io.File(path)
            if (!file.exists()) {
                android.util.Log.e("MainActivity", "Audio file not found: $path")
                return
            }
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                this.packageName + ".provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/wav")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val resInfoList = packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error opening audio file", e)
        }
    }
}
