package com.example.twinmind2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.twinmind2.recording.RecordingViewModel
import com.example.twinmind2.ui.theme.BackgroundMain
import com.example.twinmind2.ui.theme.GradientBlueStart
import com.example.twinmind2.ui.theme.GradientPurpleEnd
import com.example.twinmind2.ui.theme.StatusError
import com.example.twinmind2.ui.theme.StatusSuccess
import com.example.twinmind2.ui.theme.TextPrimary
import com.example.twinmind2.ui.theme.TextSecondary

@Composable
fun TranscriptScreen(sessionId: Long, navController: NavController) {
    val vm: RecordingViewModel = hiltViewModel()
    val transcripts = vm.transcriptsFor(sessionId).collectAsState(initial = emptyList())
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = BackgroundMain,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(GradientBlueStart, GradientPurpleEnd))
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Transcript",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Session #$sessionId",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            if (transcripts.value.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Transcription starts automatically when chunks are created.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                val sortedTranscripts = transcripts.value.sortedBy { it.chunkIndex }

                // Progress indicator
                val completedCount = sortedTranscripts.count { it.status == "completed" }
                val totalCount = sortedTranscripts.size
                if (totalCount > 0 && completedCount < totalCount) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Processing",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$completedCount / $totalCount",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { completedCount.toFloat() / totalCount },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = GradientBlueStart,
                                trackColor = Color(0xFFE0E7FF),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                sortedTranscripts.forEach { transcript ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            when (transcript.status) {
                                "pending" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFF59E0B))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Chunk ${transcript.chunkIndex} — Transcribing...",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                "failed" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(StatusError)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Chunk ${transcript.chunkIndex} — Failed",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = StatusError
                                        )
                                    }
                                    transcript.errorMessage?.let {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = it,
                                            color = StatusError.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth(),
                                            softWrap = true
                                        )
                                    }
                                }
                                "completed" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(StatusSuccess)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Chunk ${transcript.chunkIndex}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = StatusSuccess
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = transcript.text,
                                        fontSize = 14.sp,
                                        color = TextPrimary,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        softWrap = true
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "Chunk ${transcript.chunkIndex}: ${transcript.status}",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                val allCompleted = transcripts.value.isNotEmpty() &&
                        transcripts.value.all { it.status == "completed" }
                if (allCompleted) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(GradientBlueStart, GradientPurpleEnd)
                                            )
                                        )
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Complete Transcript",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = TextPrimary
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = sortedTranscripts.joinToString(" ") { it.text },
                                fontSize = 14.sp,
                                color = TextPrimary,
                                lineHeight = 22.sp,
                                modifier = Modifier.fillMaxWidth(),
                                softWrap = true
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
