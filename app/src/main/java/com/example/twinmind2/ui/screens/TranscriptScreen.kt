package com.example.twinmind2.ui.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.twinmind2.recording.RecordingViewModel

@Composable
fun TranscriptScreen(sessionId: Long, navController: NavController) {
    val vm: RecordingViewModel = hiltViewModel()
    val transcripts = vm.transcriptsFor(sessionId).collectAsState(initial = emptyList())
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📝 Transcript",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.Black
                )
                Text(
                    text = "Session #$sessionId",
                    fontSize = 14.sp,
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
                    fontSize = 14.sp,
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
                                    fontSize = 14.sp,
                                    color = Color(0xFF9E9E9E)
                                )
                            }

                            "failed" -> {
                                Text(
                                    text = "❌ Chunk ${transcript.chunkIndex}: Failed",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
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
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4CAF50)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = transcript.text,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            }

                            else -> {
                                Text(
                                    text = "• Chunk ${transcript.chunkIndex}: ${transcript.status}",
                                    fontSize = 14.sp,
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
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = transcripts.value.sortedBy { it.chunkIndex }
                            .joinToString(" ") { it.text },
                        fontSize = 14.sp,
                        color = Color.Black,
                        lineHeight = 20.sp
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