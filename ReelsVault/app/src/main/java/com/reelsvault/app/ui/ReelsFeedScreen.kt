package com.reelsvault.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.reelsvault.app.VideoStore
import kotlinx.coroutines.launch
import java.io.File

data class ReelsSettings(
    val autoplay: Boolean = true,
    val autoAdvance: Boolean = true,
    val speed: Float = 1.0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsFeedScreen(videos: List<File>, onOpenSettingsChanged: (ReelsSettings) -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { VideoStore(context) }
    var settings by remember { mutableStateOf(ReelsSettings()) }
    var showSettings by remember { mutableStateOf(false) }

    if (videos.isEmpty()) {
        EmptyState()
        return
    }

    val pagerState = rememberPagerState(pageCount = { videos.size })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            ReelPage(
                file = videos[page],
                isActive = pagerState.currentPage == page,
                settings = settings,
                store = store,
                onEnded = {
                    if (settings.autoAdvance && page < videos.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                    }
                }
            )
        }

        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(settings) {
                settings = it
                onOpenSettingsChanged(it)
            }
        }
    }
}

@Composable
private fun ReelPage(
    file: File,
    isActive: Boolean,
    settings: ReelsSettings,
    store: VideoStore,
    onEnded: () -> Unit
) {
    val context = LocalContext.current
    var meta by remember(file.name) { mutableStateOf(store.get(file.name)) }
    var showCommentBox by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
            prepare()
        }
    }

    LaunchedEffect(isActive, settings.autoplay, settings.speed) {
        exoPlayer.setPlaybackSpeed(settings.speed)
        if (isActive && settings.autoplay) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(file) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) onEnded()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Right-side action rail - like Reels' own layout, just our own icons/colors
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ActionIcon(
                icon = if (meta.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (meta.liked) Color(0xFFFF4D6D) else Color.White,
                onClick = {
                    meta = meta.copy(liked = !meta.liked)
                    store.set(file.name, meta)
                }
            )
            ActionIcon(
                icon = Icons.Filled.ChatBubbleOutline,
                tint = Color.White,
                onClick = { showCommentBox = true }
            )
            ActionIcon(
                icon = if (meta.saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                tint = if (meta.saved) Color(0xFF7C5CFF) else Color.White,
                onClick = {
                    meta = meta.copy(saved = !meta.saved)
                    store.set(file.name, meta)
                }
            )
            ActionIcon(
                icon = Icons.Filled.Fullscreen,
                tint = Color.White,
                onClick = { fullscreen = !fullscreen }
            )
        }

        // Filename + your note preview, bottom-left, like a caption
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp).widthIn(max = 240.dp)
        ) {
            Text(file.name, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (meta.comment.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(meta.comment, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }

    if (showCommentBox) {
        CommentDialog(
            initial = meta.comment,
            onDismiss = { showCommentBox = false },
            onSave = {
                meta = meta.copy(comment = it)
                store.set(file.name, meta)
                showCommentBox = false
            }
        )
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun CommentDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Add a comment for yourself\u2026") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsSheet(current: ReelsSettings, onChange: (ReelsSettings) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("Playback settings", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Autoplay", modifier = Modifier.weight(1f))
            Switch(checked = current.autoplay, onCheckedChange = { onChange(current.copy(autoplay = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto-scroll to next when a video ends", modifier = Modifier.weight(1f))
            Switch(checked = current.autoAdvance, onCheckedChange = { onChange(current.copy(autoAdvance = it)) })
        }

        Spacer(Modifier.height(12.dp))
        Text("Playback speed: ${current.speed}x")
        Slider(
            value = current.speed,
            onValueChange = { onChange(current.copy(speed = it)) },
            valueRange = 0.5f..2f,
            steps = 5
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0F)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No sessions yet", color = Color.White)
            Text("Record a scroll session to see it here", color = Color.White.copy(alpha = 0.6f))
        }
    }
}
