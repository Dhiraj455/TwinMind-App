package com.example.twinmind2.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.twinmind2.recording.RecordingViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingsSession(
    sessions: List<com.example.twinmind2.data.entity.RecordingSession>,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    navController: NavController,
    vm: RecordingViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Date and Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Row {
                TabButton("Notes", selectedTab == "Notes", onTabSelected)
                Spacer(Modifier.width(8.dp))
                TabButton("Chats", selectedTab == "Chats", onTabSelected)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (selectedTab == "Notes") {
            if (sessions.isEmpty()) {
                Text(
                    text = "No recordings yet",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                sessions.take(5).forEach { session ->
                    RecordingCard(
                        session = session,
                        navController = navController,
                        vm = vm
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        } else {
            Text(
                text = "No chats yet",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun TabButton(label: String, isSelected: Boolean, onSelected: (String) -> Unit) {
    OutlinedButton(
        onClick = { onSelected(label) },
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(16.dp),
        colors = if (isSelected) {
            ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1976D2)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isSelected) Color.White else Color(0xFF1976D2),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun RecordingCard(
    session: com.example.twinmind2.data.entity.RecordingSession,
    navController: NavController,
    vm: RecordingViewModel
) {
    val expandedState = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val chunks by vm.chunksFor(session.id).collectAsState(initial = emptyList())

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expandedState.value = !expandedState.value
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MailOutline,
                    contentDescription = null,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.completeAudioPath?.let { "Recording" } ?: "Empty Transcript Recording",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(session.startTimeMs))
                    val duration = if (session.endTimeMs != null && session.endTimeMs > session.startTimeMs) {
                        ((session.endTimeMs - session.startTimeMs) / 1000).toString() + "s"
                    } else {
                        ""
                    }
                    Text(
                        text = "$timeStr • $duration",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                IconButton(
                    onClick = {
                        vm.deleteSession(session.id)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Expanded section with buttons, chunks, and complete audio
            if (expandedState.value) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { navController.navigate("transcript/${session.id}") },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text(
                                text = "Transcript",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                        Button(
                            onClick = { navController.navigate("summary/${session.id}") },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text(
                                text = "Summary",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    session.completeAudioPath?.let { audioPath ->
                        AudioFileItem(
                            label = "Complete Audio",
                            filePath = audioPath,
                            context = context
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    if (chunks.isNotEmpty()) {
                        Text(
                            text = "Audio Chunks:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        chunks.forEach { chunk ->
                            AudioFileItem(
                                label = "Chunk ${chunk.indexInSession} (${(chunk.durationMs / 1000)}s)",
                                filePath = chunk.filePath,
                                context = context
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AudioFileItem(
    label: String,
    filePath: String,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                openAudioFile(context, filePath)
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = Color(0xFF1976D2),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color(0xFF1976D2),
            fontWeight = FontWeight.Medium
        )
    }
}

private fun openAudioFile(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) {
            android.util.Log.e("RecordingsSection", "Audio file not found: $path")
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/wav")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val resInfoList = context.packageManager.queryIntentActivities(
            intent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("RecordingsSection", "Error opening audio file", e)
    }
}