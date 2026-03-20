package com.example.twinmind2.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.twinmind2.recording.RecordingViewModel
import com.example.twinmind2.ui.theme.BackgroundMain
import com.example.twinmind2.ui.theme.GradientBlueStart
import com.example.twinmind2.ui.theme.GradientPurpleEnd
import com.example.twinmind2.ui.theme.RecordingRed
import com.example.twinmind2.ui.theme.TextPrimary
import com.example.twinmind2.ui.theme.TextSecondary
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabButton("Notes", selectedTab == "Notes", onTabSelected)
                TabButton("Chats", selectedTab == "Chats", onTabSelected)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (selectedTab == "Notes") {
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recordings yet",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chats yet",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TabButton(label: String, isSelected: Boolean, onSelected: (String) -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isSelected)
                    Modifier.background(
                        Brush.horizontalGradient(listOf(GradientBlueStart, GradientPurpleEnd))
                    )
                else
                    Modifier.background(BackgroundMain)
            )
            .clickable { onSelected(label) }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isSelected) Color.White else TextSecondary,
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
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedState.value = !expandedState.value }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(GradientBlueStart, GradientPurpleEnd))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MailOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.completeAudioPath?.let { "Recording" } ?: "Empty Transcript Recording",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        softWrap = true
                    )
                    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(session.startTimeMs))
                    val duration = if (session.endTimeMs != null && session.endTimeMs > session.startTimeMs) {
                        ((session.endTimeMs - session.startTimeMs) / 1000).toString() + "s"
                    } else ""
                    Text(
                        text = "$timeStr${if (duration.isNotEmpty()) " • $duration" else ""}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = { vm.deleteSession(session.id) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = RecordingRed.copy(alpha = 0.75f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (expandedState.value) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(GradientBlueStart, GradientPurpleEnd)
                                    )
                                )
                                .clickable { navController.navigate("transcript/${session.id}") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Transcript",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(GradientPurpleEnd, GradientBlueStart)
                                    )
                                )
                                .clickable { navController.navigate("summary/${session.id}") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Summary",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    session.completeAudioPath?.let { audioPath ->
                        Spacer(Modifier.height(14.dp))
                        AudioFileItem(
                            label = "Complete Audio",
                            filePath = audioPath,
                            context = context
                        )
                    }

                    if (chunks.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Audio Chunks",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        chunks.forEach { chunk ->
                            AudioFileItem(
                                label = "Chunk ${chunk.indexInSession} (${(chunk.durationMs / 1000)}s)",
                                filePath = chunk.filePath,
                                context = context
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioFileItem(label: String, filePath: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF0F2FF))
            .clickable { openAudioFile(context, filePath) }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = GradientBlueStart,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = GradientBlueStart,
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
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/wav")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resInfoList = context.packageManager.queryIntentActivities(
            intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resInfoList) {
            context.grantUriPermission(
                resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("RecordingsSection", "Error opening audio file", e)
    }
}
