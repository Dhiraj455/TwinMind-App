package com.example.twinmind2

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.twinmind2.ui.theme.BackgroundHome
import com.example.twinmind2.ui.theme.DividerGray
import com.example.twinmind2.ui.theme.OrangeAccent
import com.example.twinmind2.ui.theme.RecordingPillDark
import com.example.twinmind2.ui.theme.RecordingPillMid
import com.example.twinmind2.ui.theme.RecordingRed
import com.example.twinmind2.ui.theme.TextPrimary
import com.example.twinmind2.ui.theme.TextSecondary
import com.example.twinmind2.ui.theme.TwinMindDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.twinmind2.chat.ChatViewModel
import com.example.twinmind2.recording.RecordingNotifications
import com.example.twinmind2.recording.RecordingService
import com.example.twinmind2.wakeword.WakeWordPreferences
import com.example.twinmind2.recording.RecordingViewModel
import com.example.twinmind2.ui.components.BottomActionButtons
import com.example.twinmind2.ui.components.ComingUpSection
import com.example.twinmind2.ui.components.HomeFeatureCards
import com.example.twinmind2.ui.components.HomeTopBar
import com.example.twinmind2.ui.screens.ChatPromptBottomSheet
import com.example.twinmind2.ui.screens.ChatScreen
import com.example.twinmind2.ui.screens.MemoriesScreen
import com.example.twinmind2.ui.screens.RecordingDetailScreen
import com.example.twinmind2.ui.screens.TodoScreen
import com.example.twinmind2.ui.screens.rememberChatSheetState
import com.example.twinmind2.ui.theme.TwinMind2Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TwinMind2Theme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BackgroundHome
                ) { innerPadding ->
                    MainNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
        requestAllRuntimePermissions()
    }

    private fun requestAllRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        requestPermission.launch(permissions)
    }

    @Composable
    private fun MainNavigation(modifier: Modifier = Modifier) {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = modifier
        ) {
            composable("home") {
                HomeScreen(navController = navController)
            }
            composable("memories") {
                MemoriesScreen(navController = navController)
            }
            composable("todo") {
                TodoScreen(navController = navController)
            }
            composable("recording/{sessionId}") { backStackEntry ->
                val sessionId =
                    backStackEntry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
                RecordingDetailScreen(sessionId = sessionId, navController = navController)
            }
            composable("chat/{chatSessionId}") { backStackEntry ->
                val chatSessionId =
                    backStackEntry.arguments?.getString("chatSessionId")?.toLongOrNull() ?: 0L
                ChatScreen(chatSessionId = chatSessionId, navController = navController)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeScreen(navController: NavController, modifier: Modifier = Modifier) {
        val vm: RecordingViewModel = hiltViewModel()
        val chatVm: ChatViewModel = hiltViewModel()
        val state = vm.recordingState.collectAsState()
        val scrollState = rememberScrollState()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val showStartRecordingDialog = remember { mutableStateOf(false) }
        var showChatSheet by remember { mutableStateOf(false) }
        val chatSheetState = rememberChatSheetState()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF3F5))
                        )

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Dhiraj Shelke",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0D5A79),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        ) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = BackgroundHome,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (state.value.activeSessionId != null) {
                        val elapsed = state.value.elapsedSec
                        val mm = (elapsed / 60).toString().padStart(2, '0')
                        val ss = (elapsed % 60).toString().padStart(2, '0')

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(62.dp)
                                    .shadow(10.dp, RoundedCornerShape(31.dp))
                                    .clip(RoundedCornerShape(31.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(RecordingPillDark, RecordingPillMid)
                                        )
                                    )
                                    .padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(10.dp, 18.dp, 12.dp, 20.dp, 14.dp).forEach { h ->
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(h)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color(0xFF8E54E9), Color(0xFF4776E6))
                                                    ),
                                                    RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "$mm:$ss",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .clickable {
                                                val action = if (state.value.isPaused) {
                                                    RecordingNotifications.ACTION_RESUME
                                                } else {
                                                    RecordingNotifications.ACTION_PAUSE
                                                }
                                                val intent = Intent(
                                                    this@MainActivity,
                                                    RecordingService::class.java
                                                ).setAction(action)
                                                startService(intent)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (state.value.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                            contentDescription = if (state.value.isPaused) "Resume recording" else "Pause recording",
                                            tint = Color(0xFF1A1A1A),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .clickable {
                                                val intent = Intent(
                                                    this@MainActivity, RecordingService::class.java
                                                ).setAction(RecordingNotifications.ACTION_STOP)
                                                startService(intent)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(RecordingRed)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        BottomActionButtons(
                            currentlyRecording = false,
                            onRecordClick = {
                                showStartRecordingDialog.value = true
                            },
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                ) {
                    HomeTopBar(
                        onMenuClick = { scope.launch { drawerState.open() } }
                    )

                    Spacer(Modifier.height(8.dp))

                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TwinMindDark)) {
                                    append("Hey Dhiraj Shelke,\n")
                                }
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TwinMindDark)) {
                                    append("Welcome to TwinMind")
                                }
                            },
                            lineHeight = 30.sp
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // Chat with all your memories button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(2.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White)
                            .border(1.dp, DividerGray, RoundedCornerShape(28.dp))
                            .clickable { showChatSheet = true }
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = TwinMindDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = TextSecondary, fontSize = 15.sp)) {
                                        append("Chat with all ")
                                    }
                                    withStyle(SpanStyle(color = OrangeAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium)) {
                                        append("your memories")
                                    }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Hey Twin - Voice activation
                    var wakeWordEnabled by remember { mutableStateOf(WakeWordPreferences.isEnabled(this@MainActivity)) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(2.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White)
                            .border(1.dp, DividerGray, RoundedCornerShape(28.dp))
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = TwinMindDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hey Twin",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TwinMindDark
                                )
                                Text(
                                    text = "Say \"Hey Twin Start Recording\" to start",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = wakeWordEnabled,
                                onCheckedChange = {
                                    wakeWordEnabled = it
                                    WakeWordPreferences.setEnabled(this@MainActivity, it)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    HomeFeatureCards(
                        onTodoClick = { navController.navigate("todo") },
                        onMemoriesClick = { navController.navigate("memories") }
                    )

                    Spacer(Modifier.height(24.dp))

                    ComingUpSection()

                    Spacer(Modifier.height(80.dp))
                }
            }

            if (showStartRecordingDialog.value) {
                AlertDialog(
                    onDismissRequest = { showStartRecordingDialog.value = false },
                    title = { Text("Start recording?") },
                    text = { Text("Do you want to start capturing now?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showStartRecordingDialog.value = false
                                val intent = Intent(this@MainActivity, RecordingService::class.java)
                                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                            }
                        ) { Text("Start") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartRecordingDialog.value = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        if (showChatSheet) {
            ChatPromptBottomSheet(
                sheetState = chatSheetState,
                placeholder = "Search across all your recorded memories…",
                onDismiss = { showChatSheet = false },
                onSubmit = { query ->
                    showChatSheet = false
                    scope.launch {
                        val sessionId = chatVm.createSessionAndSendFirstMessage(
                            query = query,
                            type = "all",
                            recordingSessionId = null
                        )
                        navController.navigate("chat/$sessionId")
                    }
                }
            )
        }

        LaunchedEffect(Unit) {
            RecordingNotifications.ensureChannels(this@MainActivity)
            WakeWordPreferences.startIfEnabled(this@MainActivity)
        }
    }
}
