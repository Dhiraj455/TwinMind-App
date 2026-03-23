package com.example.twinmind2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.twinmind2.chat.ChatViewModel
import com.example.twinmind2.data.entity.ChatSession
import com.example.twinmind2.data.entity.RecordingSession
import com.example.twinmind2.recording.RecordingViewModel
import com.example.twinmind2.ui.theme.OrangeAccent
import com.example.twinmind2.ui.theme.TwinMindDark
import com.example.twinmind2.ui.theme.BackgroundHome
import com.example.twinmind2.ui.theme.DividerGray
import com.example.twinmind2.ui.theme.SearchBarBg
import com.example.twinmind2.ui.theme.TabSelected
import com.example.twinmind2.ui.theme.TextPrimary
import com.example.twinmind2.ui.components.geminiResponseToPlainText
import com.example.twinmind2.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoriesScreen(navController: NavController) {
    val vm: RecordingViewModel = hiltViewModel()
    val chatVm: ChatViewModel = hiltViewModel()
    val sessions by vm.searchResults.collectAsState()
    val chatSessions by chatVm.allChatSessions.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedTab by remember { mutableStateOf("Notes") }

    // Notes selection state
    var notesSelectionMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteNotesDialog by remember { mutableStateOf(false) }

    // Chats selection state
    var chatsSelectionMode by remember { mutableStateOf(false) }
    var selectedChatIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteChatsDialog by remember { mutableStateOf(false) }

    val filteredChatSessions = remember(chatSessions, searchQuery) {
        if (searchQuery.isBlank()) chatSessions
        else chatSessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

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
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF3A7BD5),
                            modifier = Modifier.size(16.dp)
                        )
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
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = vm::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions.Default,
                        decorationBox = { inner ->
                            if (searchQuery.isBlank()) {
                                Text(
                                    text = if (selectedTab == "Chats") "Search Chats" else "Search Notes",
                                    color = TextSecondary,
                                    fontSize = 15.sp
                                )
                            }
                            inner()
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MemoriesTabButton("Notes", selectedTab == "Notes") {
                        selectedTab = it
                        notesSelectionMode = false
                        selectedNoteIds = emptySet()
                        chatsSelectionMode = false
                        selectedChatIds = emptySet()
                    }
                    MemoriesTabButton("Chats", selectedTab == "Chats") {
                        selectedTab = it
                        notesSelectionMode = false
                        selectedNoteIds = emptySet()
                        chatsSelectionMode = false
                        selectedChatIds = emptySet()
                    }
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

            // Selection action bar — Notes tab
            if (selectedTab == "Notes" && notesSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedNoteIds.size} selected",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            notesSelectionMode = false
                            selectedNoteIds = emptySet()
                        }) { Text("Cancel") }
                        TextButton(
                            onClick = { showDeleteNotesDialog = true },
                            enabled = selectedNoteIds.isNotEmpty()
                        ) { Text("Delete") }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val newSessionId = vm.combineSessionsToNewNote(selectedNoteIds)
                                    notesSelectionMode = false
                                    selectedNoteIds = emptySet()
                                    if (newSessionId != null) {
                                        navController.navigate("recording/$newSessionId")
                                    }
                                }
                            },
                            enabled = selectedNoteIds.size >= 2
                        ) { Text("Combine") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Selection action bar — Chats tab
            if (selectedTab == "Chats" && chatsSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedChatIds.size} selected",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            chatsSelectionMode = false
                            selectedChatIds = emptySet()
                        }) { Text("Cancel") }
                        TextButton(
                            onClick = { showDeleteChatsDialog = true },
                            enabled = selectedChatIds.isNotEmpty()
                        ) { Text("Delete") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (selectedTab == "Chats") {
                if (filteredChatSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No chats yet.\nTap \"Chat with your memories\" to start." else "No chats match your search.",
                            fontSize = 15.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                } else {
                    val chatGrouped = filteredChatSessions.groupBy { chat ->
                        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(chat.createdAt))
                    }
                    chatGrouped.entries.sortedByDescending { it.value.maxOf { s -> s.lastMessageAt } }.forEach { (dateLabel, dayChats) ->
                        Text(
                            text = dateLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        dayChats.sortedByDescending { it.lastMessageAt }.forEach { chatSession ->
                            ChatCard(
                                chatSession = chatSession,
                                selectionMode = chatsSelectionMode,
                                selected = selectedChatIds.contains(chatSession.id),
                                onTap = {
                                    if (chatsSelectionMode) {
                                        selectedChatIds = if (selectedChatIds.contains(chatSession.id)) {
                                            selectedChatIds - chatSession.id
                                        } else {
                                            selectedChatIds + chatSession.id
                                        }
                                        if (selectedChatIds.isEmpty()) chatsSelectionMode = false
                                    } else {
                                        navController.navigate("chat/${chatSession.id}")
                                    }
                                },
                                onLongPress = {
                                    if (!chatsSelectionMode) {
                                        chatsSelectionMode = true
                                        selectedChatIds = setOf(chatSession.id)
                                    } else {
                                        selectedChatIds = if (selectedChatIds.contains(chatSession.id)) {
                                            selectedChatIds - chatSession.id
                                        } else {
                                            selectedChatIds + chatSession.id
                                        }
                                        if (selectedChatIds.isEmpty()) chatsSelectionMode = false
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            } else if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recordings found.",
                        fontSize = 15.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                val grouped = sessions.groupBy { session ->
                    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(session.startTimeMs))
                }
                grouped.entries.sortedByDescending { it.value.first().startTimeMs }.forEach { (dateLabel, daySessions) ->
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
                            vm = vm,
                            selectionMode = notesSelectionMode,
                            selected = selectedNoteIds.contains(session.id),
                            onTap = {
                                if (notesSelectionMode) {
                                    selectedNoteIds = if (selectedNoteIds.contains(session.id)) {
                                        selectedNoteIds - session.id
                                    } else {
                                        selectedNoteIds + session.id
                                    }
                                    if (selectedNoteIds.isEmpty()) notesSelectionMode = false
                                } else {
                                    navController.navigate("recording/${session.id}")
                                }
                            },
                            onLongPress = {
                                if (!notesSelectionMode) {
                                    notesSelectionMode = true
                                    selectedNoteIds = setOf(session.id)
                                } else {
                                    selectedNoteIds = if (selectedNoteIds.contains(session.id)) {
                                        selectedNoteIds - session.id
                                    } else {
                                        selectedNoteIds + session.id
                                    }
                                    if (selectedNoteIds.isEmpty()) notesSelectionMode = false
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Delete Notes confirmation dialog
    if (showDeleteNotesDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteNotesDialog = false },
            title = { Text("Delete selected recordings?") },
            text = { Text("This will permanently remove ${selectedNoteIds.size} recording(s), including transcripts and summaries.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSessions(selectedNoteIds)
                        showDeleteNotesDialog = false
                        notesSelectionMode = false
                        selectedNoteIds = emptySet()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteNotesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Chats confirmation dialog
    if (showDeleteChatsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatsDialog = false },
            title = { Text("Delete selected chats?") },
            text = { Text("This will permanently remove ${selectedChatIds.size} chat(s) and all their messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedChatIds.forEach { chatId ->
                            chatVm.deleteChatSession(chatId)
                        }
                        showDeleteChatsDialog = false
                        chatsSelectionMode = false
                        selectedChatIds = emptySet()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatCard(
    chatSession: ChatSession,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(chatSession.lastMessageAt))
    val typeLabel = when (chatSession.type) {
        "all" -> "All memories"
        "recording" -> "This note"
        else -> chatSession.type
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onTap() }
                )
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TwinMindDark),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", fontSize = 18.sp, color = OrangeAccent)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chatSession.title.ifBlank { "Chat" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = timeStr,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEAF2FF))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = typeLabel,
                        fontSize = 11.sp,
                        color = Color(0xFF245B9D),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoriesTabButton(label: String, isSelected: Boolean, onSelected: (String) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .then(
                if (isSelected) {
                    Modifier
                        .clip(shape)
                        .background(Color.Transparent)
                        .border(1.5.dp, TabSelected, shape)
                } else {
                    Modifier
                        .clip(shape)
                        .background(Color.Transparent)
                        .border(1.dp, DividerGray, shape)
                }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryCard(
    session: RecordingSession,
    vm: RecordingViewModel,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val summary by vm.summaryFor(session.id).collectAsState(initial = null)
    val title = summary?.title?.ifBlank { null } ?: session.title?.ifBlank { null } ?: "Recording"
    val tags = session.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(session.startTimeMs))
    val durationMs = if (session.endTimeMs != null && session.endTimeMs > session.startTimeMs) {
        session.endTimeMs - session.startTimeMs
    } else {
        null
    }
    val durationLabel = durationMs?.let { formatDuration(it) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onTap() }
                )
                Spacer(Modifier.width(6.dp))
            }
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
                    text = geminiResponseToPlainText(title),
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
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEAF2FF))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tag.replaceFirstChar { it.uppercase() },
                                    fontSize = 11.sp,
                                    color = Color(0xFF245B9D),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
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
