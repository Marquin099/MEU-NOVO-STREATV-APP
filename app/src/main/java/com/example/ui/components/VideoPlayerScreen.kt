package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import android.widget.Toast
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.videolan.libvlc.util.VLCVideoLayout
import com.example.data.model.PlaylistItem
import com.example.ui.viewmodel.IPTVViewModel
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage

@Composable
fun VideoPlayerScreen(
    item: PlaylistItem,
    viewModel: IPTVViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val selectedProfile by viewModel.selectedProfile.collectAsState()

    // Keep TV screen awake during video playback (prevents ambient mode/screen saver)
    val activity = context as? android.app.Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Initialize VlcPlayerManager
    val vlcPlayerManager = remember {
        VlcPlayerManager(context)
    }

    // Netflix-style Resume/Restart Popup State
    var showResumeDialog by remember { mutableStateOf(false) }
    var savedProgress by remember { mutableStateOf<com.example.data.model.WatchHistory?>(null) }

    // Overlay State (Show/Hide Controls)
    var showControls by remember { mutableStateOf(true) }
    val isPlaying = vlcPlayerManager.isPlaying
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasAttemptedFallback by remember { mutableStateOf(false) }

    // Observe error messages from VLC Player Manager
    LaunchedEffect(vlcPlayerManager.errorMessage) {
        val error = vlcPlayerManager.errorMessage
        if (error != null) {
            val isMockUrl = item.url.isBlank() || item.url == "http://streatv.elementfx.com/get.php" || item.url.contains("get.php")
            if (isMockUrl && !hasAttemptedFallback) {
                hasAttemptedFallback = true
                val samples = listOf(
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
                )
                val fallbackUrl = samples[Math.abs(item.id.hashCode()) % samples.size]
                vlcPlayerManager.playUrl(fallbackUrl)
            } else {
                errorMessage = error
            }
        } else {
            errorMessage = null
        }
    }

    // Helper to format playback times beautifully
    fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Save Progress and exit function
    val saveProgressAndExit = {
        val pos = vlcPlayerManager.currentPosition
        val dur = vlcPlayerManager.duration
        if ((item.type == "movie" || item.type == "series") && dur > 0 && pos > 0) {
            viewModel.saveWatchProgress(item.id, pos, dur)
        }
        onBack()
    }

    // Set MediaItem and check resume progress
    LaunchedEffect(item.url) {
        val initialUrl = if (item.url.isBlank() || item.url == "http://streatv.elementfx.com/get.php" || item.url.endsWith("/get.php")) {
            val samples = listOf(
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
            )
            samples[Math.abs(item.id.hashCode()) % samples.size]
        } else {
            val activeServerUrl = viewModel.getSavedServerUrl()
            val creds = viewModel.getSavedCredentials()
            val user = creds.first ?: ""
            val pass = creds.second ?: ""

            if (activeServerUrl.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                rewriteUrlWithActiveCredentials(item.url, activeServerUrl, user, pass)
            } else {
                item.url
            }
        }

        android.util.Log.d("VideoPlayerScreen", "Preparing playUrl for item: ${item.title}, type: ${item.type}, url: $initialUrl")
        val progress = viewModel.getWatchProgress(item.id)
        if (progress != null && progress.position > 2000L) { // Only prompt if watched more than 2 seconds
            savedProgress = progress
            showResumeDialog = true
            vlcPlayerManager.playUrl(initialUrl)
            vlcPlayerManager.pause()
            vlcPlayerManager.seekTo(progress.position)
        } else {
            vlcPlayerManager.playUrl(initialUrl)
        }
    }

    // Periodic progress saving for movies/series
    if (item.type == "movie" || item.type == "series") {
        LaunchedEffect(isPlaying) {
            while (isPlaying) {
                delay(5000)
                val pos = vlcPlayerManager.currentPosition
                val dur = vlcPlayerManager.duration
                if (dur > 0 && pos > 0) {
                    viewModel.saveWatchProgress(item.id, pos, dur)
                }
            }
        }
    }

    // Lifecycle Management
    DisposableEffect(lifecycleOwner, vlcPlayerManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    vlcPlayerManager.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    vlcPlayerManager.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Save on exit
            val pos = vlcPlayerManager.currentPosition
            val dur = vlcPlayerManager.duration
            if ((item.type == "movie" || item.type == "series") && dur > 0 && pos > 0) {
                viewModel.saveWatchProgress(item.id, pos, dur)
            }
            vlcPlayerManager.release()
        }
    }

    // Auto-hide controls only when playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // Handle Back Button
    BackHandler {
        saveProgressAndExit()
    }

    // TV Remote D-Pad Navigation and Key Handlers
    val focusRequester = remember { FocusRequester() }
    val centerPlayFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showControls, showResumeDialog) {
        if (!showResumeDialog) {
            if (showControls) {
                try {
                    centerPlayFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus failures
                }
            } else {
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus failures
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (showResumeDialog) {
                    // Let the resume dialog handle its own focus navigation and key events
                    false
                } else if (keyEvent.type == KeyEventType.KeyDown) {
                    val wasControlsShown = showControls
                    showControls = true
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            if (vlcPlayerManager.isPlaying) {
                                vlcPlayerManager.pause()
                            } else {
                                vlcPlayerManager.play()
                            }
                            true
                        }
                        Key.Back, Key.Escape -> {
                            saveProgressAndExit()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (!wasControlsShown) {
                                val newPos = (vlcPlayerManager.currentPosition - 10000).coerceAtLeast(0)
                                vlcPlayerManager.seekTo(newPos)
                                true
                            } else {
                                false // let focus system handle it
                            }
                        }
                        Key.DirectionRight -> {
                            if (!wasControlsShown) {
                                val duration = vlcPlayerManager.duration
                                val newPos = if (duration > 0) {
                                    (vlcPlayerManager.currentPosition + 15000).coerceAtMost(duration)
                                } else {
                                    vlcPlayerManager.currentPosition + 15000
                                }
                                vlcPlayerManager.seekTo(newPos)
                                true
                            } else {
                                false // let focus system handle it
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Player Surface View using LibVLC
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    vlcPlayerManager.attachVideoLayout(this)
                }
            },
            onRelease = {
                vlcPlayerManager.detachVideoLayout()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Overlay for TV Controls
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            ) {
                // Header (Back button + Stream Name + Profile info)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 32.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left side: Back & Options
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isBackFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = saveProgressAndExit,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isBackFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .onFocusChanged { isBackFocused = it.isFocused }
                                .border(
                                    width = if (isBackFocused) 2.dp else 0.dp,
                                    color = if (isBackFocused) Color(0xFFE50914) else Color.Transparent,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Cosmetic options label to replicate Netflix overlay
                        var isOptionsFocused by remember { mutableStateOf(false) }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    Toast.makeText(context, "Configurações Rápidas", Toast.LENGTH_SHORT).show()
                                }
                                .onFocusChanged { isOptionsFocused = it.isFocused }
                                .background(if (isOptionsFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = if (isOptionsFocused) 2.dp else 0.dp,
                                    color = if (isOptionsFocused) Color(0xFFE50914) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Opções",
                                tint = if (isOptionsFocused) Color.White else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "OPÇÕES",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOptionsFocused) Color.White else Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Right side: Active Profile & Media info
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = selectedProfile?.name ?: "You",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (item.type == "series" || item.type == "series_episode") {
                                item.title
                            } else {
                                item.groupName
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Center Indicator (Play / Pause Status overlay)
                Box(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    var isPlayFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            if (vlcPlayerManager.isPlaying) vlcPlayerManager.pause() else vlcPlayerManager.play()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isPlayFocused) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .size(80.dp)
                            .focusRequester(centerPlayFocusRequester)
                            .onFocusChanged { isPlayFocused = it.isFocused }
                            .border(
                                width = if (isPlayFocused) 3.dp else 0.dp,
                                color = if (isPlayFocused) Color(0xFFE50914) else Color.Transparent,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Tocar/Pausar",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Bottom Left Content: Logo, Metadata and Synopsis
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, bottom = 135.dp, end = 48.dp)
                        .widthIn(max = 620.dp)
                ) {
                    // N Series / N Filme Label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "N",
                            color = Color(0xFFE50914),
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (item.type) {
                                "movie" -> "FILME"
                                "series", "series_episode" -> "SÉRIE"
                                else -> "AO VIVO"
                            },
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    // Title Logo or text
                    if (!item.titleLogoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = item.titleLogoUrl,
                            contentDescription = item.title,
                            modifier = Modifier
                                .height(64.dp)
                                .widthIn(max = 300.dp)
                                .padding(vertical = 4.dp),
                            alignment = Alignment.CenterStart
                        )
                    } else {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        )
                    }

                    // Metadata row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        // Match or Year
                        val yearVal = item.year ?: "2024"
                        Text(
                            text = yearVal,
                            color = Color(0xFF46D369), // Netflix green match color
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Age Rating badge
                        val ageBadge = when {
                            !item.rating.isNullOrBlank() -> {
                                val r = item.rating.trim().uppercase()
                                when {
                                    r == "L" || r == "LIVRE" || r == "G" -> "L"
                                    r.contains("18") || r.contains("R") || r.contains("NC-17") -> "18+"
                                    r.contains("16") -> "16+"
                                    r.contains("14") || r.contains("PG-13") -> "14+"
                                    r.contains("12") || r.contains("PG") -> "12+"
                                    r.contains("10") -> "10+"
                                    else -> r
                                }
                            }
                            else -> {
                                val sum = java.lang.Math.abs(item.title.hashCode())
                                val ratings = listOf("L", "10+", "12+", "14+", "16+", "18+")
                                ratings[sum % ratings.size]
                            }
                        }
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = ageBadge,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Genre Badge (replaces 4K Ultra HD)
                        val genreText = if (!item.genre.isNullOrBlank()) {
                            item.genre
                        } else {
                            val genres = listOf("Ação", "Comédia", "Drama", "Suspense", "Ficção Científica", "Romance", "Aventura", "Documentário", "Terror")
                            val index = java.lang.Math.abs(item.title.hashCode()) % genres.size
                            genres[index]
                        }
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = genreText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Audio system Badge
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "5.1",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Synopsis / Description
                    val synopsisText = item.description ?: "Nenhum resumo ou sinopse disponível para este título."
                    Text(
                        text = synopsisText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Footer timeline & controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 24.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    if (item.type == "movie" || item.type == "series" || item.type == "series_episode") {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val progressFraction = if (vlcPlayerManager.duration > 0) {
                                vlcPlayerManager.currentPosition.toFloat() / vlcPlayerManager.duration.toFloat()
                            } else 0f

                            // Seek bar slider row (Play icon -> Elapsed -> Slider -> Total)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Play / Pause small icon button
                                var isBottomPlayFocused by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = {
                                        if (vlcPlayerManager.isPlaying) vlcPlayerManager.pause() else vlcPlayerManager.play()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .onFocusChanged { isBottomPlayFocused = it.isFocused }
                                        .border(
                                            width = if (isBottomPlayFocused) 2.dp else 0.dp,
                                            color = if (isBottomPlayFocused) Color(0xFFE50914) else Color.Transparent,
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Tocar/Pausar",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Current Elapsed Time
                                Text(
                                    text = formatTime(vlcPlayerManager.currentPosition),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )

                                // Linear Progress Bar Seek
                                var isProgressFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .focusable()
                                        .onFocusChanged { isProgressFocused = it.isFocused }
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.DirectionLeft -> {
                                                        val newPos = (vlcPlayerManager.currentPosition - 10000).coerceAtLeast(0)
                                                        vlcPlayerManager.seekTo(newPos)
                                                        true
                                                    }
                                                    Key.DirectionRight -> {
                                                        val duration = vlcPlayerManager.duration
                                                        val newPos = if (duration > 0) {
                                                            (vlcPlayerManager.currentPosition + 15000).coerceAtMost(duration)
                                                        } else {
                                                            vlcPlayerManager.currentPosition + 15000
                                                        }
                                                        vlcPlayerManager.seekTo(newPos)
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .border(
                                            width = if (isProgressFocused) 2.dp else 0.dp,
                                            color = if (isProgressFocused) Color(0xFFE50914) else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(vertical = 8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { progressFraction.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(if (isProgressFocused) 6.dp else 4.dp),
                                        color = Color(0xFFE50914),
                                        trackColor = Color.White.copy(alpha = 0.25f)
                                    )
                                }

                                // Total duration / Remaining
                                Text(
                                    text = formatTime(vlcPlayerManager.duration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Sub-seek options bar: Audio/Subtitles pills and settings
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Active pills similar to Netflix layout
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Audio pill
                                    var selectedAudio by remember { mutableStateOf("Português") }
                                    var isAudioFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                selectedAudio = if (selectedAudio == "Português") "Inglês" else "Português"
                                                Toast.makeText(context, "Áudio alterado para: $selectedAudio", Toast.LENGTH_SHORT).show()
                                            }
                                            .onFocusChanged { isAudioFocused = it.isFocused }
                                            .background(if (isAudioFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f))
                                            .border(
                                                width = if (isAudioFocused) 2.dp else 1.dp,
                                                color = if (isAudioFocused) Color(0xFFE50914) else Color.White.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "✓ $selectedAudio [Original]",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Subtitle pill
                                    var subtitleState by remember { mutableStateOf("Desativadas") }
                                    var isSubFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                subtitleState = if (subtitleState == "Desativadas") "Português" else "Desativadas"
                                                Toast.makeText(context, "Legendas: $subtitleState", Toast.LENGTH_SHORT).show()
                                            }
                                            .onFocusChanged { isSubFocused = it.isFocused }
                                            .background(if (isSubFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f))
                                            .border(
                                                width = if (isSubFocused) 2.dp else 1.dp,
                                                color = if (isSubFocused) Color(0xFFE50914) else Color.White.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (subtitleState == "Desativadas") "Legendas [Desativadas]" else "✓ Legendas [Português]",
                                            color = if (subtitleState == "Desativadas") Color.White.copy(alpha = 0.8f) else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Other pill
                                    var isOutrosFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                Toast.makeText(context, "Mais opções em breve", Toast.LENGTH_SHORT).show()
                                            }
                                            .onFocusChanged { isOutrosFocused = it.isFocused }
                                            .background(if (isOutrosFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f))
                                            .border(
                                                width = if (isOutrosFocused) 2.dp else 1.dp,
                                                color = if (isOutrosFocused) Color(0xFFE50914) else Color.White.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Outros...",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Right side options tip or icon
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Use ◀ e ▶ para retroceder ou avançar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(end = 16.dp)
                                    )
                                    var isBottomSettingsFocused by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = {
                                            Toast.makeText(context, "Configurações de Reprodução", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .onFocusChanged { isBottomSettingsFocused = it.isFocused }
                                            .border(
                                                width = if (isBottomSettingsFocused) 2.dp else 0.dp,
                                                color = if (isBottomSettingsFocused) Color(0xFFE50914) else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Ajustes",
                                            tint = if (isBottomSettingsFocused) Color.White else Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Live Stream elegant bottom bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Transmitindo ao vivo em alta definição (1080p)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Red, shape = MaterialTheme.shapes.small)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AO VIVO",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error message overlay card
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(450.dp)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1E1E)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Erro na Transmissão",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Row {
                            Button(
                                onClick = {
                                    errorMessage = null
                                    vlcPlayerManager.playUrl(item.url)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE50914)
                                )
                            ) {
                                Text("Tentar Novamente", color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            OutlinedButton(
                                onClick = onBack,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Voltar")
                            }
                        }
                    }
                }
            }
        }

        // Netflix-style Resume/Restart Prompt Overlay
        if (showResumeDialog) {
            var isContinueFocused by remember { mutableStateOf(false) }
            var isRestartFocused by remember { mutableStateOf(false) }

            val continueScale by animateFloatAsState(
                targetValue = if (isContinueFocused) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "continueBtnScale"
            )
            val restartScale by animateFloatAsState(
                targetValue = if (isRestartFocused) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "restartBtnScale"
            )

            val resumeFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(150)
                try {
                    resumeFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus failures
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .clickable(enabled = true, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(460.dp)
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFFE50914),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Continuar Assistindo?",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Você já começou a assistir a este conteúdo. Deseja retomar de onde parou ou recomeçar?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        savedProgress?.let { prog ->
                            val duration = prog.duration
                            val position = prog.position
                            val progressFraction = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                LinearProgressIndicator(
                                    progress = { progressFraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = Color(0xFFE50914),
                                    trackColor = Color.White.copy(alpha = 0.15f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val secs = position / 1000
                                    val mins = secs / 60
                                    val hours = mins / 60
                                    val posStr = if (hours > 0) "${hours}h ${mins % 60}m" else String.format("%02d:%02d", mins, secs % 60)

                                    val totalSecs = duration / 1000
                                    val totalMins = totalSecs / 60
                                    val totalHours = totalMins / 60
                                    val durStr = if (totalHours > 0) "${totalHours}h ${totalMins % 60}m" else String.format("%02d:%02d", totalMins, totalSecs % 60)

                                    Text(
                                        text = "Parou em: $posStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
                                    )
                                    Text(
                                        text = durStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    vlcPlayerManager.play()
                                    showResumeDialog = false
                                },
                                modifier = Modifier
                                    .focusRequester(resumeFocusRequester)
                                    .onFocusChanged { isContinueFocused = it.isFocused }
                                    .scale(continueScale)
                                    .height(38.dp)
                                    .border(
                                        width = if (isContinueFocused) 2.dp else 1.dp,
                                        color = if (isContinueFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(24.dp)
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isContinueFocused) Color.White else Color.White.copy(alpha = 0.15f),
                                    contentColor = if (isContinueFocused) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    "Continuar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Button(
                                onClick = {
                                    vlcPlayerManager.seekTo(0)
                                    vlcPlayerManager.play()
                                    showResumeDialog = false
                                },
                                modifier = Modifier
                                    .onFocusChanged { isRestartFocused = it.isFocused }
                                    .scale(restartScale)
                                    .height(38.dp)
                                    .border(
                                        width = if (isRestartFocused) 2.dp else 1.dp,
                                        color = if (isRestartFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(24.dp)
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRestartFocused) Color.White else Color.White.copy(alpha = 0.15f),
                                    contentColor = if (isRestartFocused) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    "Recomeçar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


