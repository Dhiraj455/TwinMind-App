package com.example.twinmind2.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.twinmind2.chat.ChatViewModel
import com.example.twinmind2.data.entity.Summary
import com.example.twinmind2.recording.RecordingViewModel
import com.example.twinmind2.ui.components.GeminiMarkdownContent
import com.example.twinmind2.ui.components.geminiResponseToPlainText
import com.example.twinmind2.ui.theme.BackgroundHome
import com.example.twinmind2.ui.theme.DividerGray
import com.example.twinmind2.ui.theme.GradientBlueStart
import com.example.twinmind2.ui.theme.GradientPurpleEnd
import com.example.twinmind2.ui.theme.OrangeAccent
import com.example.twinmind2.ui.theme.StatusError
import com.example.twinmind2.ui.theme.StatusSuccess
import com.example.twinmind2.ui.theme.TabSelected
import com.example.twinmind2.ui.theme.TextPrimary
import com.example.twinmind2.ui.theme.TextSecondary
import com.example.twinmind2.ui.theme.TwinMindDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// "Questions" tab hidden for now — code preserved below, re-add "Questions" to re-enable
private val detailTabs = listOf(/*"Questions",*/ "Notes", "Transcript")
private val audioTabLabel = "Audio"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(sessionId: Long, navController: NavController) {
    val vm: RecordingViewModel = hiltViewModel()
    val chatVm: ChatViewModel = hiltViewModel()
    val sessions by vm.sessions.collectAsState()
    val session = sessions.firstOrNull { it.id == sessionId }
    val summary by vm.summaryFor(sessionId).collectAsState(initial = null)
    val transcripts by vm.transcriptsFor(sessionId).collectAsState(initial = emptyList())
    var selectedTab by remember { mutableStateOf("Notes") }
    var showMenu by remember { mutableStateOf(false) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf("") }
    var showChatSheet by remember { mutableStateOf(false) }
    var chatSheetInitialQuery by remember { mutableStateOf("") }
    val chatSheetState = rememberChatSheetState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(sessionId) {
        val existingSummary = vm.getSummaryOnce(sessionId)
        if (existingSummary == null || existingSummary.status == "idle") {
            val transcriptsList = vm.transcriptsFor(sessionId).first()
            if (transcriptsList.any { it.status == "completed" }) {
                vm.generateSummary(sessionId)
            }
        }
    }

    val title = summary?.title?.ifBlank { null }
        ?: session?.title?.ifBlank { null }
        ?: "Recording"

    val dateStr = session?.let {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it.startTimeMs))
    } ?: ""
    val timeStr = session?.let {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it.startTimeMs))
    } ?: ""
    val durationMs = session?.let {
        if (it.endTimeMs != null && it.endTimeMs > it.startTimeMs) it.endTimeMs - it.startTimeMs else null
    }
    val durationLabel = durationMs?.let { ms ->
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "${totalSecs}s"
        }
    }
    val metaStr = listOfNotNull(
        if (dateStr.isNotEmpty()) "$dateStr · $timeStr" else timeStr,
        durationLabel
    ).joinToString(" · ")

    Scaffold(
        containerColor = BackgroundHome,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundHome)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF3A7BD5),
                                modifier = Modifier.size(14.dp)
                            )
//                            Spacer(Modifier.width(2.dp))
//                            Text("Back", color = Color(0xFF3A7BD5), fontSize = 16.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1A1A))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = durationLabel ?: "--:--",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = TextPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                                DropdownMenuItem(
                                    text = { Text("Rename title") },
                                    onClick = {
                                        renameTitle = title
                                        isEditingTitle = true
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy transcript") },
                                    onClick = {
                                        val transcriptText = transcripts
                                            .sortedBy { it.chunkIndex }
                                            .filter { it.status == "completed" }
                                            .joinToString("\n") { it.text }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Transcript", transcriptText))
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy summary") },
                                    onClick = {
                                        val summaryText = buildString {
                                            summary?.title?.takeIf { it.isNotBlank() }
                                                ?.let { appendLine(geminiResponseToPlainText(it)) }
                                            summary?.summary?.takeIf { it.isNotBlank() }?.let {
                                                appendLine()
                                                appendLine("Summary")
                                                appendLine(geminiResponseToPlainText(it))
                                            }
                                            summary?.actionItems?.takeIf { it.isNotBlank() }?.let {
                                                appendLine()
                                                appendLine("Action Items")
                                                appendLine(geminiResponseToPlainText(it))
                                            }
                                            summary?.keyPoints?.takeIf { it.isNotBlank() }?.let {
                                                appendLine()
                                                appendLine("Key Points")
                                                appendLine(geminiResponseToPlainText(it))
                                            }
                                        }.ifBlank { "No summary available yet." }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Summary", summaryText))
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showDeleteDialog = true
                                        showMenu = false
                                    }
                                )
                        }
                    }
                }

                // Title + metadata
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    if (isEditingTitle) {
                        OutlinedTextField(
                            value = renameTitle,
                            onValueChange = { renameTitle = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.Black),
//                            label = { Text("Recording title") }
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    val trimmed = renameTitle.trim()
                                    if (trimmed.isNotEmpty()) {
                                        vm.renameSessionTitle(sessionId, trimmed)
                                    }
                                    isEditingTitle = false
                                }
                            ) {
                                Text("Save")
                            }
                            TextButton(
                                onClick = {
                                    renameTitle = title
                                    isEditingTitle = false
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    } else {
                        Text(
                            text = geminiResponseToPlainText(title),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            lineHeight = 28.sp
                        )
                    }
                    if (metaStr.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = metaStr,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    val tags = session?.tags
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.take(3).forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
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

                Spacer(Modifier.height(16.dp))

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    (detailTabs + audioTabLabel).forEach { tab ->
                        DetailTabItem(
                            label = tab,
                            isSelected = selectedTab == tab,
                            onClick = { selectedTab = tab }
                        )
                    }
                }

                Divider(color = DividerGray, thickness = 1.dp)
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundHome)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(4.dp, RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White)
                        .border(1.dp, DividerGray, RoundedCornerShape(26.dp))
                        .clickable {
                            chatSheetInitialQuery = ""
                            showChatSheet = true
                        }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "✦", fontSize = 16.sp, color = OrangeAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chat with ",
                        fontSize = 15.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "this note",
                        fontSize = 15.sp,
                        color = OrangeAccent,
                        fontWeight = FontWeight.Medium
                    )
                }
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
            when (selectedTab) {
                // "Questions" tab hidden — uncomment in detailTabs list above to re-enable
                // "Questions" -> QuestionsTab(
                //     onQuestionSelected = { question ->
                //         chatSheetInitialQuery = question
                //         showChatSheet = true
                //     }
                // )
                "Notes" -> NotesTab(summary = summary, onRegenerate = { vm.generateSummary(sessionId) })
                "Transcript" -> TranscriptTab(
                    transcripts = transcripts,
                    session = session,
                    context = context,
                    onRetryChunk = { vm.retryTranscriptChunk(sessionId, it) }
                )
                audioTabLabel -> AudioTab(session = session, context = context)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete recording?") },
            text = { Text("This will permanently remove this recording and its generated data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSession(sessionId)
                        showDeleteDialog = false
                        navController.popBackStack()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showChatSheet) {
        ChatPromptBottomSheet(
            sheetState = chatSheetState,
            placeholder = "Ask anything about this recording…",
            initialText = chatSheetInitialQuery,
            onDismiss = { showChatSheet = false },
            onSubmit = { query ->
                showChatSheet = false
                scope.launch {
                    val chatSessionId = chatVm.createSessionAndSendFirstMessage(
                        query = query,
                        type = "recording",
                        recordingSessionId = sessionId
                    )
                    navController.navigate("chat/$chatSessionId")
                }
            }
        )
    }
}

@Composable
private fun DetailTabItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) TabSelected else TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(if (isSelected) 36.dp else 0.dp)
                .height(2.dp)
                .background(if (isSelected) TabSelected else Color.Transparent)
        )
    }
}

@Composable
private fun AudioTab(
    session: com.example.twinmind2.data.entity.RecordingSession?,
    context: Context
) {
    val audioPath = session?.completeAudioPath
    if (audioPath.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Complete audio will appear here after recording finishes.",
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Full Recording",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
//        Text(
//            text = "Path: $audioPath",
//            fontSize = 12.sp,
//            color = TextSecondary
//        )
        Button(
            onClick = {
                val file = java.io.File(audioPath)
                if (!file.exists()) return@Button
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "audio/wav")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val resInfoList = context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                resInfoList.forEach { resolveInfo ->
                    context.grantUriPermission(
                        resolveInfo.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                if (resInfoList.isNotEmpty()) {
                    context.startActivity(intent)
                }
            }
        ) {
            Text("Open Full Audio")
        }
    }
}

// --- Questions Tab ---

private val questionSuggestions = listOf(
    "✍️" to "Draft a follow-up email with next steps",
    "🌟" to "Find memorable moments and funny quotes",
    "💡" to "What are the key insights?"
)

@Composable
private fun QuestionsTab(onQuestionSelected: (String) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        questionSuggestions.forEach { (emoji, text) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable { onQuestionSelected(text) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = emoji, fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = text,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    lineHeight = 20.sp
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// --- Notes (Summary) Tab ---

@Composable
private fun NotesTab(summary: Summary?, onRegenerate: () -> Unit) {
    Column {
        // Share banner
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .shadow(2.dp, RoundedCornerShape(14.dp))
//                .clip(RoundedCornerShape(14.dp))
//                .background(Color(0xFFEFF6FF))
//                .padding(16.dp)
//        ) {
//            Column {
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    Text("✦", fontSize = 16.sp, color = GradientBlueStart)
//                    Spacer(Modifier.width(8.dp))
//                    Text(
//                        text = "Share a link to this summary!",
//                        fontSize = 14.sp,
//                        color = TextPrimary,
//                        fontWeight = FontWeight.Medium
//                    )
//                }
//                Spacer(Modifier.height(10.dp))
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(44.dp)
//                        .clip(RoundedCornerShape(10.dp))
//                        .background(TwinMindDark)
//                        .clickable {}
//                        .padding(horizontal = 16.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.Center
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.Share,
//                        contentDescription = null,
//                        tint = Color.White,
//                        modifier = Modifier.size(16.dp)
//                    )
//                    Spacer(Modifier.width(8.dp))
//                    Text(
//                        text = "Share now",
//                        fontSize = 15.sp,
//                        color = Color.White,
//                        fontWeight = FontWeight.SemiBold
//                    )
//                }
//            }
//        }

        Spacer(Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = "Regenerate summary",
                tint = TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onRegenerate() }
            )
        }

        Spacer(Modifier.height(8.dp))

        when (summary?.status) {
            null, "idle" -> {
                SummaryLoadingCard("Preparing your summary…")
            }
            "generating" -> {
                SummaryLoadingCard("Generating… (${summary.sectionsCompleted}/4 sections ready)")
                Spacer(Modifier.height(12.dp))
                SummarySectionsDetail(summary, isGenerating = true)
            }
            "failed" -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusError.copy(alpha = 0.08f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = summary.errorMessage ?: "Failed to generate summary.",
                        fontSize = 13.sp,
                        color = StatusError,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = StatusError,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onRegenerate() }
                    )
                }
                if (summary.sectionsCompleted > 0) {
                    Spacer(Modifier.height(12.dp))
                    SummarySectionsDetail(summary, isGenerating = false)
                }
            }
            "completed" -> {
                SummarySectionsDetail(summary, isGenerating = false)
            }
            else -> SummarySectionsDetail(summary, isGenerating = summary.status == "generating")
        }
    }
}

@Composable
private fun SummaryLoadingCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = GradientBlueStart
        )
        Spacer(Modifier.width(12.dp))
        Text(text = message, fontSize = 14.sp, color = TextSecondary)
    }
}

@Composable
private fun SummarySectionsDetail(summaryData: Summary, isGenerating: Boolean) {
    val sections = listOf(
        Triple("Summary", summaryData.summary, false),
        Triple("Action Items", summaryData.actionItems, true),
        Triple("Key Points", summaryData.keyPoints, true)
    )
    val accentColors = listOf(
        listOf(Color(0xFF4776E6), Color(0xFF00C6FF)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFFF7971E), Color(0xFFFFD200))
    )

    // Title as bold heading
    summaryData.title?.ifBlank { null }?.let { titleText ->
        GeminiMarkdownContent(
            text = titleText,
            textColor = TextPrimary,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }

    sections.forEachIndexed { index, (heading, content, asBullets) ->
        SummaryDetailCard(
            heading = heading,
            content = content,
            isLoading = isGenerating && content.isNullOrBlank(),
            asBullets = asBullets,
            accentGradient = accentColors[index]
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SummaryDetailCard(
    heading: String,
    content: String?,
    isLoading: Boolean,
    asBullets: Boolean,
    accentGradient: List<Color>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(accentGradient))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = heading,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = accentGradient.first()
                )
                Spacer(Modifier.width(8.dp))
                Text("Loading…", fontSize = 13.sp, color = TextSecondary)
            }
            content.isNullOrBlank() -> Text("Not available", fontSize = 13.sp, color = TextSecondary)
            asBullets -> {
                val items = content.split('\n')
                    .map { it.trim().trimStart('-', '*', '•').trim() }
                    .filter { it.isNotBlank() }
                if (items.isEmpty()) {
                    GeminiMarkdownContent(
                        text = content,
                        textColor = TextPrimary,
                        fontSize = 14.sp
                    )
                } else {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.padding(bottom = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(accentGradient))
                            )
                            Spacer(Modifier.width(10.dp))
                            GeminiMarkdownContent(
                                text = item,
                                textColor = TextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            else -> GeminiMarkdownContent(
                text = content,
                textColor = TextPrimary,
                fontSize = 14.sp
            )
        }
    }
}

// --- Transcript Tab ---

@Composable
private fun TranscriptTab(
    transcripts: List<com.example.twinmind2.data.entity.Transcript>,
    session: com.example.twinmind2.data.entity.RecordingSession?,
    context: Context,
    onRetryChunk: (chunkId: Long) -> Unit = {}
) {
    Column {
        if (transcripts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Transcription starts automatically after recording.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            val sorted = transcripts.sortedBy { it.chunkIndex }
            val completedCount = sorted.count { it.status == "completed" }
            val totalCount = sorted.size

            if (completedCount < totalCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = GradientBlueStart
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Processing $completedCount / $totalCount chunks…",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            sorted.forEach { transcript ->
                val chunkTimeMs = session?.startTimeMs?.plus(
                    transcript.chunkIndex.toLong() * 30_000L
                ) ?: transcript.createdAtMs
                val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(chunkTimeMs))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = timeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    when (transcript.status) {
                        "pending" -> Text(
                            text = "Transcribing…",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                        "failed" -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = transcript.errorMessage ?: "Transcription failed",
                                fontSize = 14.sp,
                                color = StatusError,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRetryChunk(transcript.chunkId) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = GradientBlueStart,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        "completed" -> Text(
                            text = transcript.text,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            lineHeight = 24.sp
                        )
                        else -> Text(transcript.status, fontSize = 13.sp, color = TextSecondary)
                    }
                }

                Divider(color = DividerGray, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
            }

            val allCompleted = sorted.isNotEmpty() && sorted.all { it.status == "completed" }
            if (allCompleted) {
                Spacer(Modifier.height(8.dp))
                val fullText = sorted.joinToString(" ") { it.text }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFF0EEE8))
                        .clickable {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Transcript", fullText))
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Copy Transcript",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
