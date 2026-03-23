package com.example.twinmind2.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.twinmind2.chat.ChatViewModel
import com.example.twinmind2.data.entity.ChatMessage
import com.example.twinmind2.ui.components.GeminiMarkdownContent
import com.example.twinmind2.ui.components.geminiResponseToPlainText
import com.example.twinmind2.ui.theme.BackgroundHome
import com.example.twinmind2.ui.theme.DividerGray
import com.example.twinmind2.ui.theme.OrangeAccent
import com.example.twinmind2.ui.theme.TextPrimary
import com.example.twinmind2.ui.theme.TextSecondary
import com.example.twinmind2.ui.theme.TwinMindDark

// ─── Chat screen ──────────────────────────────────────────────────────────────

@Composable
fun ChatScreen(chatSessionId: Long, navController: NavController) {
    val vm: ChatViewModel = hiltViewModel()
    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val currentSession by vm.currentSession.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatSessionId) { vm.loadSession(chatSessionId) }

    LaunchedEffect(messages.size, isLoading) {
        val count = messages.size + if (isLoading || error != null) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottomPx) {
        if (imeBottomPx > 0) {
            val count = messages.size + if (isLoading || error != null) 1 else 0
            if (count > 0) listState.animateScrollToItem(count - 1)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = BackgroundHome,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(BackgroundHome)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                            tint = Color(0xFF3A7BD5), modifier = Modifier.size(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSession?.title?.ifBlank { null } ?: "Chat",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        when (currentSession?.type) {
                            "all" -> Text("All memories", fontSize = 12.sp, color = TextSecondary)
                            "recording" -> Text("This note", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.width(48.dp))
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerGray))
            }
        },
        bottomBar = {
            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    val text = inputText.trim()
                    if (text.isNotBlank() && !isLoading) { inputText = ""; vm.sendMessage(text) }
                },
                enabled = !isLoading
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val isLastAssistantMsg = !isLoading && error == null &&
                        message.role == "assistant" &&
                        message.id == messages.lastOrNull { it.role == "assistant" }?.id
                ChatMessageBubble(
                    message = message,
                    showRegenerate = isLastAssistantMsg,
                    onRegenerate = { vm.retryLastMessage() }
                )
            }
            if (isLoading) { item(key = "typing") { TypingIndicator() } }
            error?.let { errorMsg ->
                item(key = "error") {
                    ErrorBubble(message = errorMsg, onRetry = { vm.clearError(); vm.retryLastMessage() })
                }
            }
        }
    }
}

// ─── Message bubble ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    showRegenerate: Boolean = false,
    onRegenerate: () -> Unit = {}
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    if (isUser) {
        // User bubble — right aligned, long press to copy
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.width(56.dp))
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                        .background(TwinMindDark)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showMenu = true }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(message.content, fontSize = 15.sp, color = Color.White, lineHeight = 22.sp)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy message") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            copyText(context, message.content)
                            showMenu = false
                        }
                    )
                }
            }
        }
    } else {
        // AI bubble — left aligned, SelectionContainer for word selection + long press for copy
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier.padding(top = 4.dp).size(32.dp).clip(CircleShape).background(TwinMindDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✦", fontSize = 14.sp, color = OrangeAccent)
                }
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                            .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                            .background(Color.White)
                            .combinedClickable(
                                onClick = {},
                                // Long press on bubble background (outside text) → copy dialog
                                onLongClick = { showMenu = true }
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        // SelectionContainer enables native Android word selection on long press of text.
                        // Clicking elsewhere outside the selection automatically dismisses it (native behavior).
                        SelectionContainer {
                            GeminiMarkdownContent(
                                text = message.content,
                                textColor = TextPrimary,
                                fontSize = 15.sp
                            )
                        }
                    }
                    // Copy dropdown — anchored to top-end of bubble
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy message") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                copyText(context, geminiResponseToPlainText(message.content))
                                showMenu = false
                            }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            // Action row — copy is always visible, regenerate only on last AI response
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.padding(start = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Copy icon — always shown on AI messages
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(1.dp, DividerGray, CircleShape)
                        .clickable { copyText(context, geminiResponseToPlainText(message.content)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy",
                        tint = TextSecondary, modifier = Modifier.size(15.dp))
                }
                // Regenerate icon — only shown under the last AI response
                if (showRegenerate) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.dp, DividerGray, CircleShape)
                            .clickable { onRegenerate() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate",
                            tint = TextSecondary, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("chat_message", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

// ─── Typing indicator ─────────────────────────────────────────────────────────

@Composable
private fun TypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.padding(top = 4.dp).size(32.dp).clip(CircleShape).background(TwinMindDark),
            contentAlignment = Alignment.Center
        ) { Text("✦", fontSize = 14.sp, color = OrangeAccent) }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .shadow(2.dp, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) { AnimatedDots() }
    }
}

@Composable
private fun AnimatedDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0, 150, 300).forEach { delay ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = delay, easing = LinearEasing), RepeatMode.Reverse
                ), label = "dot_$delay"
            )
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(TextSecondary.copy(alpha = alpha)))
        }
    }
}

// ─── Error bubble ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorBubble(message: String, onRetry: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Spacer(Modifier.width(40.dp))
        Box(
            modifier = Modifier.weight(1f)
                .shadow(2.dp, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(Color(0xFFFFF3F3))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, fontSize = 13.sp, color = Color(0xFFCC3333),
                    modifier = Modifier.weight(1f), lineHeight = 18.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Refresh, "Retry", tint = Color(0xFFCC3333),
                    modifier = Modifier.size(18.dp).clickable { onRetry() })
            }
        }
    }
}

// ─── Chat input bar ───────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().background(BackgroundHome).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .border(1.dp, DividerGray, RoundedCornerShape(26.dp))
                .padding(start = 20.dp, end = 12.dp)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✦", fontSize = 16.sp, color = OrangeAccent)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value, onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, keyboardType = KeyboardType.Text),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { inner ->
                    if (value.isBlank()) Text(if (enabled) "Ask a follow-up…" else "Thinking…", color = TextSecondary, fontSize = 15.sp)
                    inner()
                }
            )
            if (value.isNotBlank() && enabled) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(OrangeAccent).clickable { onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowForward, "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPromptBottomSheet(
    sheetState: SheetState,
    placeholder: String = "Ask anything about your memories…",
    initialText: String = "",
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf(initialText) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp)) {
            Text("Ask a question", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(placeholder, fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BackgroundHome)
                    .border(1.dp, DividerGray, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(onSend = { val t = text.trim(); if (t.isNotBlank()) onSubmit(t) }),
                    decorationBox = { inner ->
                        if (text.isBlank()) Text("Type your question…", color = TextSecondary, fontSize = 15.sp)
                        inner()
                    }
                )
                if (text.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(OrangeAccent)
                            .clickable { val t = text.trim(); if (t.isNotBlank()) onSubmit(t) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowForward, "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberChatSheetState() = rememberModalBottomSheetState(skipPartiallyExpanded = true)
