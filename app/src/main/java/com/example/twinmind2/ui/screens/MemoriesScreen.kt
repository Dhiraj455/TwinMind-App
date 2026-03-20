package com.example.twinmind2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.twinmind2.data.entity.RecordingSession
import com.example.twinmind2.recording.RecordingViewModel
import com.example.twinmind2.ui.theme.BackgroundHome
import com.example.twinmind2.ui.theme.DividerGray
import com.example.twinmind2.ui.theme.SearchBarBg
import com.example.twinmind2.ui.theme.TabSelected
import com.example.twinmind2.ui.theme.TextPrimary
import com.example.twinmind2.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoriesScreen(navController: NavController) {
    val vm: RecordingViewModel = hiltViewModel()
    val sessions by vm.sessions.collectAsState()
    var selectedTab by remember { mutableStateOf("Notes") }
    var searchQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = BackgroundHome,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF3A7BD5),
                                modifier = Modifier.size(16.dp)
                            )
//                            Spacer(Modifier.width(2.dp))
//                            Text(
//                                text = "Back",
//                                color = Color(0xFF3A7BD5),
//                                fontSize = 16.sp
//                            )
                        }
                    }
                    Text(
                        text = "Memories",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.width(72.dp))
                }

                // Search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SearchBarBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (searchQuery.isEmpty()) "Search Notes" else searchQuery,
                        color = if (searchQuery.isEmpty()) TextSecondary else TextPrimary,
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Notes / Chats tabs
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MemoriesTabButton(
                        label = "Notes",
                        isSelected = selectedTab == "Notes",
                        onSelected = { selectedTab = it }
                    )
                    MemoriesTabButton(
                        label = "Chats",
                        isSelected = selectedTab == "Chats",
                        onSelected = { selectedTab = it }
                    )
                }

                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerGray)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            if (selectedTab == "Notes") {
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recordings yet.\nTap Capture Notes to start.",
                            fontSize = 15.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    val grouped = sessions.groupBy { session ->
                        SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                            .format(Date(session.startTimeMs))
                    }
                    grouped.entries.sortedByDescending { it.value.first().startTimeMs }
                        .forEach { (dateLabel, daySessions) ->
                            Text(
                                text = dateLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                            daySessions.sortedByDescending { it.startTimeMs }.forEach { session ->
                                MemoryCard(
                                    session = session,
                                    navController = navController,
                                    vm = vm
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No chats yet.",
                        fontSize = 15.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MemoriesTabButton(label: String, isSelected: Boolean, onSelected: (String) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .then(
                if (isSelected)
                    Modifier
                        .clip(shape)
                        .background(Color.Transparent)
                        .border(1.5.dp, TabSelected, shape)
                else
                    Modifier
                        .clip(shape)
                        .background(Color.Transparent)
                        .border(1.dp, DividerGray, shape)
            )
            .clickable { onSelected(label) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) TabSelected else TextSecondary
        )
    }
}

@Composable
private fun MemoryCard(
    session: RecordingSession,
    navController: NavController,
    vm: RecordingViewModel
) {
    val summary by vm.summaryFor(session.id).collectAsState(initial = null)
    val title = summary?.title?.ifBlank { null }
        ?: session.title?.ifBlank { null }
        ?: "Recording"

    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(session.startTimeMs))
    val durationMs = if (session.endTimeMs != null && session.endTimeMs > session.startTimeMs) {
        session.endTimeMs - session.startTimeMs
    } else null
    val durationLabel = durationMs?.let { formatDuration(it) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable { navController.navigate("recording/${session.id}") }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0EEE8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = timeStr + if (durationLabel != null) " • $durationLabel" else "",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins} min"
        else -> "${totalSecs}s"
    }
}
