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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                    MainNavigation(modifier = Modifier.padding(innerPadding))
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
    private fun MainNavigation(modifier: Modifier = Modifier) {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = modifier
        ) {
            composable("home") {
                RecordingScreen(navController = navController)
            }
            composable("transcript/{sessionId}") { backStackEntry ->
                val sessionId =
                    backStackEntry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
                TranscriptScreen(sessionId = sessionId, navController = navController)
            }
            composable("summary/{sessionId}") { backStackEntry ->
                val sessionId =
                    backStackEntry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
                SummaryScreen(sessionId = sessionId, navController = navController)
            }
        }
    }

    @Composable
    private fun RecordingScreen(navController: NavController, modifier: Modifier = Modifier) {
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
            Text(
                text = "Timer: ${
                    (state.value.elapsedSec / 60).toString().padStart(2, '0')
                }:${(state.value.elapsedSec % 60).toString().padStart(2, '0')}"
            )
            Spacer(Modifier.height(12.dp))

            val currentlyRecording = state.value.activeSessionId != null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (!currentlyRecording) {
                        val intent = Intent(this@MainActivity, RecordingService::class.java)
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(
                            intent
                        )
                    } else {
                        val intent =
                            Intent(this@MainActivity, RecordingService::class.java).setAction(
                                RecordingNotifications.ACTION_STOP
                            )
                        startService(intent)
                    }
                }) { Text(if (currentlyRecording) "Stop" else "Record") }

                if (currentlyRecording) {
                    if (state.value.isPaused) {
                        Button(onClick = {
                            val intent =
                                Intent(this@MainActivity, RecordingService::class.java).setAction(
                                    RecordingNotifications.ACTION_RESUME
                                )
                            startService(intent)
                        }) { Text("Resume") }
                    } else {
                        Button(onClick = {
                            val intent =
                                Intent(this@MainActivity, RecordingService::class.java).setAction(
                                    RecordingNotifications.ACTION_PAUSE
                                )
                            startService(intent)
                        }) { Text("Pause") }
                    }
                }
            }

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
                            text = " 🎵 Complete Audio (${
                                completePath.split("/").lastOrNull() ?: "N/A"
                            })",
                            modifier = Modifier
                                .clickable { openAudio(completePath) }
                                .padding(start = 8.dp)
                        )
                    }
                    SessionChunksList(sessionId = session.id, vm = vm)

                    // Navigation buttons for Transcript and Summary
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { navController.navigate("transcript/${session.id}") }) {
                            Text("View Transcript")
                        }
                        Button(onClick = { navController.navigate("summary/${session.id}") }) {
                            Text("View Summary")
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { RecordingNotifications.ensureChannels(this@MainActivity) }
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
                        Text(
                            text = "• $item",
                            modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
                        )
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

            val resInfoList = packageManager.queryIntentActivities(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
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

    @Composable
    private fun TranscriptScreen(sessionId: Long, navController: NavController) {
        val vm: RecordingViewModel = hiltViewModel()
        val transcripts = vm.transcriptsFor(sessionId).collectAsState(initial = emptyList())
        val scrollState = rememberScrollState()

        Scaffold(
            topBar = {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📝 Transcript",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    Text(
                        text = "Session #$sessionId",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
            ) {
                if (transcripts.value.isEmpty()) {
                    Text(
                        text = "No transcripts yet. Transcription starts automatically when chunks are created.",
                        modifier = Modifier.padding(8.dp),
                        color = Color.Gray
                    )
                } else {
                    transcripts.value.sortedBy { it.chunkIndex }.forEach { transcript ->
                        Spacer(Modifier.height(16.dp))
                        Column {
                            when (transcript.status) {
                                "pending" -> {
                                    Text(
                                        text = "⏳ Chunk ${transcript.chunkIndex}: Transcribing...",
                                        color = Color(0xFF9E9E9E)
                                    )
                                }

                                "failed" -> {
                                    Text(
                                        text = "❌ Chunk ${transcript.chunkIndex}: Failed",
                                        color = Color(0xFFE53935)
                                    )
                                    transcript.errorMessage?.let {
                                        Text(
                                            text = "   Error: $it",
                                            color = Color(0xFFE53935),
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                "completed" -> {
                                    Text(
                                        text = "✓ Chunk ${transcript.chunkIndex}:",
                                        color = Color(0xFF4CAF50)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = transcript.text,
                                        fontSize = 14.sp
                                    )
                                }

                                else -> {
                                    Text(
                                        text = "• Chunk ${transcript.chunkIndex}: ${transcript.status}",
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    val allCompleted = transcripts.value.isNotEmpty() &&
                            transcripts.value.all { it.status == "completed" }
                    if (allCompleted) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "📄 Complete Transcript:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = transcripts.value.sortedBy { it.chunkIndex }
                                .joinToString(" ") { it.text },
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    } else {
                        val completedCount =
                            transcripts.value.count { it.status == "completed" }
                        val totalCount = transcripts.value.size
                        val pendingCount = transcripts.value.count { it.status == "pending" }
                        val failedCount = transcripts.value.count { it.status == "failed" }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Progress: $completedCount/$totalCount completed${if (pendingCount > 0) ", $pendingCount pending" else ""}${if (failedCount > 0) ", $failedCount failed" else ""}",
                            color = Color(0xFF757575),
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Back")
                }
            }
        }
    }

    @Composable
    private fun SummaryScreen(sessionId: Long, navController: NavController) {
        val vm: RecordingViewModel = hiltViewModel()
        val summaryState = vm.summaryFor(sessionId).collectAsState(initial = null)
        val summary = summaryState.value
        val scrollState = rememberScrollState()

        Scaffold(
            topBar = {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📄 Summary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    Text(
                        text = "Session #$sessionId",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
            ) {
                when (summary?.status) {
                    null, "idle" -> {
                        Text(
                            text = "No summary generated yet.",
                            color = Color.Gray,
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.generateSummary(sessionId) }) {
                            Text("Generate Summary")
                        }
                    }

                    "generating" -> {
                        Text(
                            text = "Generating summary... (${summary.sectionsCompleted}/4 sections ready)",
                            color = Color.Gray,
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        SummarySections(summary, isGenerating = true)
                    }

                    "failed" -> {
                        Text(
                            text = summary.errorMessage ?: "Failed to generate summary.",
                            color = Color(0xFFE53935),
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.generateSummary(sessionId) }) {
                            Text("Retry")
                        }
                        if (summary.sectionsCompleted > 0) {
                            Spacer(Modifier.height(16.dp))
                            SummarySections(summary, isGenerating = false)
                        }
                    }

                    "completed" -> {
                        SummarySections(summary, isGenerating = false)
                    }

                    else -> SummarySections(
                        summary,
                        isGenerating = summary.status == "generating"
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Back")
                }
            }
        }
    }
}
