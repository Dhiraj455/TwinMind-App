package com.example.twinmind2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.twinmind2.data.entity.Summary
import com.example.twinmind2.recording.RecordingViewModel
import com.example.twinmind2.ui.components.GeminiMarkdownContent
import com.example.twinmind2.ui.theme.BackgroundMain
import com.example.twinmind2.ui.theme.GradientBlueStart
import com.example.twinmind2.ui.theme.GradientPurpleEnd
import com.example.twinmind2.ui.theme.StatusError
import com.example.twinmind2.ui.theme.TextPrimary
import com.example.twinmind2.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first

@Composable
fun SummaryScreen(sessionId: Long, navController: NavController) {
    val vm: RecordingViewModel = hiltViewModel()
    val summaryState = vm.summaryFor(sessionId).collectAsState(initial = null)
    val summary = summaryState.value
    val transcripts = vm.transcriptsFor(sessionId).collectAsState(initial = emptyList())
    val scrollState = rememberScrollState()

    LaunchedEffect(sessionId) {
        if (summary == null || summary.status == "idle") {
            val transcriptsList = vm.transcriptsFor(sessionId).first()
            if (transcriptsList.isNotEmpty() && transcriptsList.any { it.status == "completed" }) {
                vm.generateSummary(sessionId)
            }
        }
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Summary",
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
                    // Retry button when failed
                    if (summary?.status == "failed") {
                        IconButton(onClick = { vm.generateSummary(sessionId) }) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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
            when (summary?.status) {
                null, "idle" -> {
                    GeneratingPlaceholder("Preparing your summary...")
                }
                "generating" -> {
                    GeneratingPlaceholder(
                        "Generating… (${summary.sectionsCompleted}/4 sections ready)"
                    )
                    Spacer(Modifier.height(16.dp))
                    SummarySections(summary, isGenerating = true)
                }
                "failed" -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = StatusError.copy(alpha = 0.08f)
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusError)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = summary.errorMessage ?: "Failed to generate summary.",
                                fontSize = 13.sp,
                                color = StatusError,
                                modifier = Modifier.fillMaxWidth(),
                                softWrap = true
                            )
                        }
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GeneratingPlaceholder(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = GradientBlueStart
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SummarySections(summaryData: Summary, isGenerating: Boolean) {
    val sections = listOf(
        Triple("Title", summaryData.title, false),
        Triple("Summary", summaryData.summary, false),
        Triple("Action Items", summaryData.actionItems, true),
        Triple("Key Points", summaryData.keyPoints, true)
    )
    val sectionGradients = listOf(
        listOf(Color(0xFF4776E6), Color(0xFF00C6FF)),
        listOf(Color(0xFF8E54E9), Color(0xFFD855A0)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFFF7971E), Color(0xFFFFD200))
    )

    sections.forEachIndexed { index, (heading, content, asBullets) ->
        SummarySectionCard(
            heading = heading,
            content = content,
            isLoading = isGenerating && content.isNullOrBlank(),
            asBullets = asBullets,
            accentGradient = sectionGradients[index]
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SummarySectionCard(
    heading: String,
    content: String?,
    isLoading: Boolean,
    asBullets: Boolean = false,
    accentGradient: List<Color>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(5.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    Text(text = "Loading...", fontSize = 13.sp, color = TextSecondary)
                }

                content.isNullOrBlank() -> Text(
                    text = "Not available",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                asBullets -> {
                    val items = content
                        .split('\n')
                        .map { line -> line.trim().trimStart('-', '*', '•').trim() }
                        .filter { it.isNotBlank() }

                    if (items.isEmpty()) {
                        GeminiMarkdownContent(
                            text = content,
                            textColor = TextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
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
                                        .background(
                                            Brush.linearGradient(accentGradient)
                                        )
                                )
                                Spacer(Modifier.width(10.dp))
                                GeminiMarkdownContent(
                                    text = item,
                                    textColor = TextPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }
                    }
                }

                else -> GeminiMarkdownContent(
                    text = content,
                    textColor = TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
