package com.example.twinmind2

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.twinmind2.recording.RecordingNotifications
import com.example.twinmind2.recording.RecordingService
import com.example.twinmind2.recording.RecordingViewModel
import com.example.twinmind2.ui.components.BottomActionButtons
import com.example.twinmind2.ui.components.Calender
import com.example.twinmind2.ui.components.Header
import com.example.twinmind2.ui.components.NavigationIcons
import com.example.twinmind2.ui.components.RecordingsSession
import com.example.twinmind2.ui.screens.SummaryScreen
import com.example.twinmind2.ui.theme.TwinMind2Theme
import dagger.hilt.android.AndroidEntryPoint
import com.example.twinmind2.ui.screens.TranscriptScreen

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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
                RecordingScreen(navController = navController)
            }
            composable("transcript/{sessionId}") { backStackEntry ->
                val sessionId =
                    backStackEntry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
                TranscriptScreen(sessionId = sessionId, navController = navController)
            }
            composable("summary/{sessionId}") { backStackEntry ->
                val sessionId =
                    backStackEntry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
                SummaryScreen(sessionId = sessionId, navController = navController)
            }
        }
    }

    @Composable
    private fun RecordingScreen(navController: NavController, modifier: Modifier = Modifier) {
        val vm: RecordingViewModel = hiltViewModel()
        val state = vm.recordingState.collectAsState()
        val sessions = vm.sessions.collectAsState()
        val selectedTab = remember { mutableStateOf("Notes") }
        val scrollState = rememberScrollState()

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color(0xFFF5F5F5),
            bottomBar = {
                if (state.value.activeSessionId != null) {
                    // Recording bar - horizontal pill-shaped bar with timer and stop button
                    val elapsed = state.value.elapsedSec
                    val mm = (elapsed / 60).toString().padStart(2, '0')
                    val ss = (elapsed % 60).toString().padStart(2, '0')
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(56.dp)
                            .background(
                                color = Color(0xFF1565C0), // Dark blue
                                shape = RoundedCornerShape(28.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Waveform icon (three vertical lines)
                        Row(
                            modifier = Modifier.width(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(12.dp)
                                    .background(Color.White, RoundedCornerShape(1.5.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(18.dp)
                                    .background(Color.White, RoundedCornerShape(1.5.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .background(Color.White, RoundedCornerShape(1.5.dp))
                            )
                        }
                        
                        // Center: Timer with dropdown chevron
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "$mm:$ss",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // Right: Circular white button with red square inside
                        IconButton(
                            onClick = {
                                val intent = Intent(this@MainActivity, RecordingService::class.java).setAction(
                                    RecordingNotifications.ACTION_STOP
                                )
                                startService(intent)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color(0xFFD32F2F), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                } else {
                    // Show "Capture Now" button when not recording
                    BottomActionButtons(
                        currentlyRecording = false,
                        isPaused = false,
                        onRecordClick = {
                            val intent = Intent(this@MainActivity, RecordingService::class.java)
                            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                        },
                        onPauseClick = {},
                        onResumeClick = {}
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
                Header()

                Spacer(Modifier.height(16.dp))

                NavigationIcons()

                Spacer(Modifier.height(16.dp))

                Calender()

                Spacer(Modifier.height(24.dp))

                RecordingsSession(
                    sessions = sessions.value,
                    selectedTab = selectedTab.value,
                    onTabSelected = { selectedTab.value = it },
                    navController = navController,
                    vm = vm
                )

                // Extra bottom padding to prevent content from being hidden by bottom bar
                Spacer(Modifier.height(80.dp))
            }
        }

        LaunchedEffect(Unit) { RecordingNotifications.ensureChannels(this@MainActivity) }
    }

    private fun openAudio(path: String) {
        try {
            val file = java.io.File(path)
            if (!file.exists()) {
                android.util.Log.e("MainActivity", "Audio file not found: $path")
                return
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                this.packageName + ".provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/wav")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resInfoList = packageManager.queryIntentActivities(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error opening audio file", e)
        }
    }
}
