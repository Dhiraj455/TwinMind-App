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
import com.example.twinmind2.data.entity.Summary
import com.example.twinmind2.recording.RecordingViewModel

@Composable
fun SummaryScreen(sessionId: Long, navController: NavController) {
    val vm: RecordingViewModel = hiltViewModel()
    val summaryState = vm.summaryFor(sessionId).collectAsState(initial = null)
    val summary = summaryState.value
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = Color(0xFFF5F5F5),
        topBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📄 Summary",
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
            when (summary?.status) {
                null, "idle" -> {
                    Text(
                        text = "No summary generated yet.",
                        fontSize = 14.sp,
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
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(8.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    SummarySections(summary, isGenerating = true)
                }

                "failed" -> {
                    Text(
                        text = summary.errorMessage ?: "Failed to generate summary.",
                        fontSize = 14.sp,
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
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
    )
    Spacer(Modifier.height(4.dp))

    when {
        isLoading -> Text(
            text = "Loading...",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 12.dp)
        )

        content.isNullOrBlank() -> Text(
            text = "Not available",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 12.dp)
        )

        asBullets -> {
            val items = content
                .split('\n')
                .map { line -> line.trim().trimStart('-', '*', '•').trim() }
                .filter { it.isNotBlank() }

            if (items.isEmpty()) {
                Text(
                    text = content,
                    fontSize = 14.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(start = 12.dp)
                )
            } else {
                items.forEach { item ->
                    Text(
                        text = "• $item",
                        fontSize = 14.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
                    )
                }
            }
        }

        else -> Text(
            text = content,
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}