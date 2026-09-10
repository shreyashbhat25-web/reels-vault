package com.reelsvault.app

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reelsvault.app.ui.ReelsFeedScreen

class MainActivity : ComponentActivity() {

    private var pendingMinutes = 30

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenRecordService::class.java).apply {
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenRecordService.EXTRA_DURATION_MINUTES, pendingMinutes)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppRoot(
                    onStartRecording = { minutes ->
                        pendingMinutes = minutes
                        val manager = getSystemService(MediaProjectionManager::class.java)
                        projectionLauncher.launch(manager.createScreenCaptureIntent())
                    }
                )
            }
        }
    }
}

@Composable
private fun AppRoot(onStartRecording: (Int) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val videos = remember { mutableStateOf(listOf<java.io.File>()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun refresh() {
        val dir = ScreenRecordService.sessionsDir(context)
        videos.value = dir.listFiles { f -> f.extension == "mp4" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(tab) { if (tab == 1) refresh() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.FiberManualRecord, contentDescription = "Record") },
                    label = { Text("Record") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.List, contentDescription = "Feed") },
                    label = { Text("Feed") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (tab == 0) RecordScreen(onStartRecording) else ReelsFeedScreen(videos.value)
        }
    }
}

@Composable
private fun RecordScreen(onStartRecording: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(30f) }
    val recording = ScreenRecordService.isRecording

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Record a scroll session", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick how long you'll be scrolling. Once it starts, switch to Instagram or YouTube and scroll normally - it captures your screen and stops itself.",
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(32.dp))

        Text("${minutes.toInt()} minutes")
        Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 5f..90f, steps = 16)

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onStartRecording(minutes.toInt()) },
            enabled = !recording,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (recording) "Recording\u2026" else "Start recording")
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "It records exactly what's on your screen - captions, likes, whatever's showing. Switch to Feed after it finishes.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}
