package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout
import com.example.ui.components.VlcPlayerManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.input.key.*
import androidx.activity.compose.BackHandler
import com.example.data.model.PlaylistItem
import com.example.data.model.CastMember
import com.example.data.model.Episode
import com.example.data.model.Season
import com.example.ui.viewmodel.IPTVViewModel
import com.example.ui.viewmodel.Screen
import com.example.ui.components.playInVLC
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

// Domain models for the details are now imported from com.example.data.model.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    item: PlaylistItem,
    viewModel: IPTVViewModel,
    onBack: () -> Unit,
    autoPlay: Boolean = false
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentItem by remember(item) { mutableStateOf(item) }

    LaunchedEffect(item) {
        val dbItem = viewModel.getItemById(item.id)
        if (dbItem != null) {
            currentItem = dbItem
        }
        if (item.type == "movie" || item.type == "series") {
            if (currentItem.description.isNullOrBlank() || currentItem.titleLogoUrl.isNullOrBlank()) {
                try {
                    val enriched = viewModel.enrichItemWithTMDB(item)
                    currentItem = enriched
                } catch (e: Exception) {
                    android.util.Log.e("DetailScreen", "Failed dynamic TMDB enrichment inside DetailScreen", e)
                }
            }
        }
    }

    val item = currentItem

    // Extract metadata dynamically from TMDB or local fallback
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var castList by remember { mutableStateOf<List<CastMember>>(emptyList()) }
    var isLoadingMetadata by remember { mutableStateOf(true) }
    var selectedSeasonIndex by remember { mutableStateOf(0) }

    val backFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isLoadingMetadata) {
        if (!isLoadingMetadata) {
            delay(150) // Allow layout to settle and items to render
            try {
                backFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // Initialize VlcPlayerManager for background trailer playback
    val vlcPlayerManager = remember {
        VlcPlayerManager(context, loop = true, muted = true)
    }
    val isVideoReady = vlcPlayerManager.isVideoReady
    var isMuted by remember { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        val activeServerUrl = viewModel.getSavedServerUrl()
        val creds = viewModel.getSavedCredentials()
        val user = creds.first ?: ""
        val pass = creds.second ?: ""

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
            if (activeServerUrl.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                com.example.ui.components.rewriteUrlWithActiveCredentials(item.url, activeServerUrl, user, pass)
            } else {
                item.url
            }
        }

        vlcPlayerManager.playUrl(initialUrl)
    }

    // Handle lifecycle pause/resume/release
    DisposableEffect(lifecycleOwner, vlcPlayerManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    vlcPlayerManager.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    vlcPlayerManager.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vlcPlayerManager.release()
        }
    }

    LaunchedEffect(item.id) {
        isLoadingMetadata = true
        coroutineScope {
            val sDeferred = async { viewModel.getRealSeasons(item) }
            val cDeferred = async { viewModel.getRealCast(item) }
            try {
                seasons = sDeferred.await()
            } catch (e: Exception) {
                seasons = getMockSeasons(item)
            }
            try {
                castList = cDeferred.await()
            } catch (e: Exception) {
                castList = getMockCast(item)
            }
        }
        isLoadingMetadata = false
    }

    val currentSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.firstOrNull()

    var hasAutoPlayed by remember { mutableStateOf(false) }

    val playContent: () -> Unit = {
        if (item.type == "series") {
            coroutineScope.launch {
                val lastEp = viewModel.getLastWatchedEpisode(item.id)
                if (lastEp != null) {
                    viewModel.navigateTo(
                        Screen.Player(
                            item.copy(
                                title = "${item.title} - ${lastEp.first}",
                                url = lastEp.second
                            )
                        )
                    )
                } else {
                    // Start playing the first available episode
                    val firstEp = currentSeason?.episodes?.firstOrNull { it.isReleased }
                    if (firstEp != null) {
                        val seasonName = currentSeason?.name ?: "Temporada 1"
                        if (!firstEp.playUrl.isNullOrBlank()) {
                            viewModel.saveLastWatchedEpisode(item.id, "S01E01: ${firstEp.title}", firstEp.playUrl)
                            viewModel.navigateTo(
                                Screen.Player(
                                    item.copy(
                                        title = "${item.title} - S01E01: ${firstEp.title}",
                                        url = firstEp.playUrl
                                    )
                                )
                            )
                        } else {
                            val realItem = viewModel.findRealEpisodeItem(item.title, seasonName, 0, firstEp.title)
                            if (realItem != null) {
                                viewModel.saveLastWatchedEpisode(item.id, "S01E01: ${firstEp.title}", realItem.url)
                                viewModel.navigateTo(
                                    Screen.Player(
                                        realItem.copy(
                                            title = "${item.title} - S01E01: ${firstEp.title}"
                                        )
                                    )
                                )
                            } else {
                                val realSeries = viewModel.verifyAndGetRealPlaylistItem(item)
                                if (realSeries != null) {
                                    viewModel.saveLastWatchedEpisode(item.id, "S01E01: ${firstEp.title}", realSeries.url)
                                    viewModel.navigateTo(
                                        Screen.Player(
                                            item.copy(
                                                title = "${item.title} - S01E01: ${firstEp.title}",
                                                url = realSeries.url
                                            )
                                        )
                                    )
                                } else {
                                    Toast.makeText(context, "Espere o Lançamento ainda nao disponivel", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "Espere o Lançamento ainda nao disponivel", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            coroutineScope.launch {
                val realMovie = viewModel.verifyAndGetRealPlaylistItem(item)
                if (realMovie != null) {
                    viewModel.navigateTo(Screen.Player(realMovie))
                } else {
                    viewModel.navigateTo(Screen.Player(item))
                }
            }
        }
    }

    LaunchedEffect(isLoadingMetadata) {
        if (!isLoadingMetadata && autoPlay && !hasAutoPlayed) {
            hasAutoPlayed = true
            playContent()
        }
    }

    // Colors of the movie overlay - gradient bottom to top
    val baseVibeColor = remember(item) {
        val titleLower = item.title.lowercase()
        when {
            titleLower.contains("dragão") || titleLower.contains("dragon") -> Color(0xFF2B0909) // Red-dark
            titleLower.contains("moana") -> Color(0xFF04212D) // Cyan-ocean dark
            titleLower.contains("aranha") || titleLower.contains("spider") -> Color(0xFF1B0B2E) // Purple dark
            titleLower.contains("estrela") || titleLower.contains("la la land") -> Color(0xFF0B172E) // Royal Blue dark
            else -> Color(0xFF0F0F0F)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Escape || keyEvent.key == Key.Back)) {
                    onBack()
                    true
                } else {
                    false
                }
            }
    ) {
        // 1. Full-screen backdrop banner (shown initially and as a fallback)
        AsyncImage(
            model = item.backdropUrl ?: item.logoUrl,
            contentDescription = "Banner de fundo",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isVideoReady) 0.15f else 0.45f },
            contentScale = ContentScale.Crop
        )

        // Background Trailer video
        if (vlcPlayerManager.mediaPlayer != null) {
            val animatedAlpha by animateFloatAsState(
                targetValue = if (isVideoReady) 0.45f else 0f,
                animationSpec = tween(1000),
                label = "VideoBackgroundFade"
            )
            AndroidView(
                factory = { ctx ->
                    VLCVideoLayout(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        vlcPlayerManager.attachVideoLayout(this)
                    }
                },
                onRelease = {
                    vlcPlayerManager.detachVideoLayout()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = animatedAlpha }
            )
        }

        // 2. Cinematic gradient bottom to top
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0F0F),
                            baseVibeColor.copy(alpha = 0.95f),
                            baseVibeColor.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        startY = Float.POSITIVE_INFINITY,
                        endY = 0f
                    )
                )
        )

        // 3. Main Content
        if (item.type == "series") {
            // Series Layout: Top header is 100% static, bottom area contains Episode & Season lists
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // BACK BUTTON & MUTE BUTTON (Top Header Row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(modifier = Modifier.focusRequester(backFocusRequester), onClick = onBack)

                    if (isVideoReady) {
                        MuteButton(
                            isMuted = isMuted,
                            onClick = {
                                isMuted = !isMuted
                                vlcPlayerManager.setMute(isMuted)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Title + Synopsis Compact Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (!item.titleLogoUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = item.titleLogoUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .height(65.dp)
                                    .wrapContentWidth()
                                    .align(Alignment.Start),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = item.title,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item.year?.let {
                                Text(text = it, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                            item.rating?.let {
                                Text(text = "⭐ $it", color = Color(0xFFF7BD15), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            item.genre?.let {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = it, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = item.description ?: "Nenhuma sinopse disponível para este título.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Split content layout: Left = Episodes, Right = Season Selector (Filling remaining screen space)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // LEFT PANEL: Episodes (weighted 0.65f)
                    Column(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "EPISÓDIOS (${currentSeason?.episodes?.size ?: 0})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (currentSeason != null && currentSeason.episodes.isNotEmpty()) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(currentSeason.episodes) { index, ep ->
                                        EpisodeItemRow(
                                            episode = ep,
                                            episodeNumber = index + 1,
                                            onClick = {
                                                if (ep.isReleased) {
                                                    val episodeTitle = "S${String.format("%02d", selectedSeasonIndex + 1)}E${String.format("%02d", index + 1)}: ${ep.title}"
                                                    val seasonName = currentSeason?.name ?: "Temporada ${selectedSeasonIndex + 1}"
                                                    coroutineScope.launch {
                                                        if (!ep.playUrl.isNullOrBlank()) {
                                                            viewModel.saveLastWatchedEpisode(item.id, episodeTitle, ep.playUrl)
                                                            viewModel.navigateTo(
                                                                Screen.Player(
                                                                    item.copy(
                                                                        title = "${item.title} - $episodeTitle",
                                                                        url = ep.playUrl
                                                                    )
                                                                )
                                                            )
                                                        } else {
                                                            val realItem = viewModel.findRealEpisodeItem(item.title, seasonName, index, ep.title)
                                                            if (realItem != null) {
                                                                viewModel.saveLastWatchedEpisode(item.id, episodeTitle, realItem.url)
                                                                viewModel.navigateTo(
                                                                    Screen.Player(
                                                                        realItem.copy(
                                                                            title = "${item.title} - $episodeTitle"
                                                                        )
                                                                    )
                                                                )
                                                            } else {
                                                                val realSeries = viewModel.verifyAndGetRealPlaylistItem(item)
                                                                if (realSeries != null) {
                                                                    viewModel.saveLastWatchedEpisode(item.id, episodeTitle, realSeries.url)
                                                                    viewModel.navigateTo(
                                                                        Screen.Player(
                                                                            item.copy(
                                                                                title = "${item.title} - $episodeTitle",
                                                                                url = realSeries.url
                                                                            )
                                                                        )
                                                                    )
                                                                } else {
                                                                    Toast.makeText(context, "Espere o Lançamento ainda nao disponivel", Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Este episódio estará disponível em ${ep.releaseDate}. Aguarde o lançamento!",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nenhum episódio cadastrado nesta temporada.",
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }

                    // RIGHT PANEL: Season List (weighted 0.35f)
                    Column(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "TEMPORADAS",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(seasons) { idx, season ->
                                SeasonSelectorItem(
                                    name = season.name,
                                    isSelected = idx == selectedSeasonIndex,
                                    onClick = { selectedSeasonIndex = idx }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Movie Layout: Non-scrolling single screen structure where Back button stays firmly visible at top
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // BACK BUTTON & MUTE BUTTON
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(modifier = Modifier.focusRequester(backFocusRequester), onClick = onBack)

                    if (isVideoReady) {
                        MuteButton(
                            isMuted = isMuted,
                            onClick = {
                                isMuted = !isMuted
                                vlcPlayerManager.setMute(isMuted)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // HEADER INFO SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 24.dp)
                    ) {
                        if (!item.titleLogoUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = item.titleLogoUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .height(85.dp)
                                    .wrapContentWidth()
                                    .align(Alignment.Start),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = item.title,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item.year?.let {
                                Text(text = it, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            }
                            item.rating?.let {
                                Text(text = "⭐ $it", color = Color(0xFFF7BD15), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            item.genre?.let {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(text = it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = item.description ?: "Nenhuma sinopse disponível para este título.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WatchNowButton(
                                modifier = Modifier.focusRequester(playFocusRequester),
                                onClick = { playContent() }
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .width(170.dp)
                            .height(255.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = item.logoUrl ?: item.backdropUrl,
                            contentDescription = "Poster",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                if (castList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "ELENCO PRINCIPAL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(castList) { cast ->
                            CastMemberCard(cast)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(50.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.15f))
            .border(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Voltar",
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "VOLTAR",
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun MuteButton(
    isMuted: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.15f))
            .border(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            contentDescription = if (isMuted) "Ativar som" else "Desativar som",
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isMuted) "ATIVAR SOM" else "MUTAR SOM",
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun WatchNowButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.9f) else Color(0xFFE50914).copy(alpha = 0.65f),
            contentColor = if (isFocused) Color.Black else Color.White
        ),
        shape = RoundedCornerShape(24.dp), // Fully rounded corners
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .height(40.dp) // Smaller height
            .padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "ASSISTIR AGORA",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun CastMemberCard(cast: CastMember) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        // Profile image as a clean circle
        AsyncImage(
            model = cast.photoUrl,
            contentDescription = cast.name,
            modifier = Modifier
                .size(75.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Name immediately below the image
        Text(
            text = cast.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EpisodeItemRow(
    episode: Episode,
    episodeNumber: Int,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(
            1.dp,
            if (isFocused) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Thumbnail
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(65.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Overlay play icon if released, otherwise show lock and release date
                if (!episode.isReleased) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Bloqueado",
                                tint = Color(0xFFE50914),
                                modifier = Modifier.size(16.dp)
                            )
                            if (episode.releaseDate != null) {
                                Text(
                                    text = episode.releaseDate,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Assistir",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Episode Title / Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$episodeNumber. ${episode.title}",
                        color = if (episode.isReleased) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = episode.duration,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (episode.isReleased) {
                        episode.synopsis
                    } else {
                        "Este episódio será lançado em ${episode.releaseDate}. O aplicativo liberará para assistir no momento do lançamento."
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SeasonSelectorItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> Color(0xFFE50914)
                isFocused -> Color.White.copy(alpha = 0.15f)
                else -> Color.White.copy(alpha = 0.05f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isFocused && !isSelected) Color.White.copy(alpha = 0.4f) else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = name.uppercase(),
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }
    }
}


// --- PROCEDURAL AND MAPPED MOCK DATA GENERATOR ---

fun getMockCast(item: PlaylistItem): List<CastMember> {
    val titleLower = item.title.lowercase()
    return when {
        titleLower.contains("dragão") || titleLower.contains("dragon") -> listOf(
            CastMember("Emma D'Arcy", "Princesa Rhaenyra", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80"),
            CastMember("Matt Smith", "Príncipe Daemon", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Olivia Cooke", "Rainha Alicent", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80"),
            CastMember("Rhys Ifans", "Otto Hightower", "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Steve Toussaint", "Lord Corlys", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80")
        )
        titleLower.contains("moana") -> listOf(
            CastMember("Auli'i Cravalho", "Moana (Voz)", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&q=80"),
            CastMember("Dwayne Johnson", "Maui (Voz)", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Rachel House", "Vovó Tala", "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?auto=format&fit=crop&w=150&q=80"),
            CastMember("Temuera Morrison", "Chefe Tui", "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=150&q=80")
        )
        titleLower.contains("aranha") || titleLower.contains("spider") -> listOf(
            CastMember("Shameik Moore", "Miles Morales", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Hailee Steinfeld", "Gwen Stacy", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80"),
            CastMember("Jake Johnson", "Peter B. Parker", "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Oscar Isaac", "Miguel O'Hara", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80")
        )
        else -> listOf(
            CastMember("Christian Bale", "Protagonista", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Margot Robbie", "Co-Protagonista", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80"),
            CastMember("Cillian Murphy", "Mentor", "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Florence Pugh", "Rival", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80")
        )
    }
}

fun getMockSeasons(item: PlaylistItem): List<Season> {
    val titleLower = item.title.lowercase()

    // Check if we have specialized seasons
    return when {
        titleLower.contains("dragão") || titleLower.contains("dragon") -> listOf(
            Season(
                name = "Temporada 1",
                episodes = listOf(
                    Episode("Os Herdeiros do Dragão", "60 min", "Viserys organiza um torneio para celebrar o nascimento de seu herdeiro, enquanto Rhaenyra e Daemon disputam a atenção do rei.", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=300&q=80", isReleased = true),
                    Episode("O Príncipe Canalha", "54 min", "Daemon ocupa Dragonstone com seus guardas dourados e desafia abertamente o rei, forçando uma resposta drástica.", "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=300&q=80", isReleased = true),
                    Episode("O Segundo de seu Nome", "58 min", "Dois anos depois, Aegon celebra seu segundo aniversário e Rhaenyra se sente pressionada pelo casamento iminente.", "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=300&q=80", isReleased = true),
                    Episode("Rei do Mar Estreito", "60 min", "Daemon retorna a Porto Real vitorioso e restabelece os laços com seu irmão, levando Rhaenyra a explorar os segredos da cidade.", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=300&q=80", isReleased = true),
                    Episode("Iluminamos o Caminho", "62 min", "O casamento real de Rhaenyra e Laenor Velaryon é marcado por tensões políticas, rivalidades e uma trágica explosão de violência.", "https://images.unsplash.com/photo-1469371670807-013ccf25f16a?auto=format&fit=crop&w=300&q=80", isReleased = true)
                )
            ),
            Season(
                name = "Temporada 2",
                episodes = listOf(
                    Episode("Filho por Filho", "59 min", "Após a morte trágica de Lucerys, Rhaenyra busca vingança e Daemon planeja um ato ultrajante que abalará Porto Real.", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=300&q=80", isReleased = true),
                    Episode("Rhaenyra, a Cruel", "57 min", "O reino lida com as consequências do ataque ao palácio, forçando Rhaenyra e Daemon a se confrontarem na fortaleza.", "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=300&q=80", isReleased = true),
                    Episode("O Dragão Vermelho e o Dourado", "58 min", "A primeira grande batalha aérea com dragões ocorre em Rook's Rest, mudando o destino de vários personagens principais.", "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=300&q=80", releaseDate = "10/07/2026", isReleased = true),
                    Episode("Regente", "60 min", "A corte lida com as pesadas baixas da batalha aérea enquanto novos líderes surgem no conselho de Porto Real.", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=300&q=80", releaseDate = "15/07/2026", isReleased = false),
                    Episode("Povo Comum", "59 min", "Rhaenyra tenta reconquistar a lealdade do povo faminto através de uma estratégia audaciosa de suprimento de comida.", "https://images.unsplash.com/photo-1514306191717-452ec28c7814?auto=format&fit=crop&w=300&q=80", releaseDate = "22/07/2026", isReleased = false)
                )
            )
        )
        titleLower.contains("pradaria") -> listOf(
            Season(
                name = "Temporada 1",
                episodes = listOf(
                    Episode("Uma Nova Terra", "48 min", "A família Ingalls viaja de carroça coberta para o oeste em busca de terras férteis para construir um lar.", "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=300&q=80", isReleased = true),
                    Episode("Construindo a Casa", "45 min", "Charles trabalha incansavelmente para levantar as paredes de madeira de sua nova cabana antes da chegada do inverno rigoroso.", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=300&q=80", isReleased = true),
                    Episode("A Primeira Colheita", "50 min", "Toda a família ajuda na plantação, mas uma tempestade de granizo inesperada ameaça destruir o sustento do ano inteiro.", "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=300&q=80", releaseDate = "12/07/2026", isReleased = false)
                )
            )
        )
        else -> {
            // Procedurally generate based on item id/title
            val totalSeasons = if (item.id % 2 == 0) 3 else 2
            List(totalSeasons) { sIdx ->
                val seasonNum = sIdx + 1
                val totalEp = 4 + (item.id % 3)
                Season(
                    name = "Temporada $seasonNum",
                    episodes = List(totalEp) { eIdx ->
                        val epNum = eIdx + 1
                        // Set the last episodes as unreleased future episodes!
                        val isUpcoming = seasonNum == totalSeasons && eIdx >= totalEp - 2
                        val (releaseDate, isReleased) = if (isUpcoming) {
                            val daysInFuture = (eIdx - (totalEp - 2)) * 7 + 4
                            // Date format: 2026-07-11 plus daysInFuture
                            val calendar = Calendar.getInstance().apply {
                                set(2026, Calendar.JULY, 11)
                                add(Calendar.DAY_OF_YEAR, daysInFuture)
                            }
                            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            Pair(sdf.format(calendar.time), false)
                        } else {
                            Pair(null, true)
                        }

                        Episode(
                            title = "Episódio $epNum: Mistérios Revelados",
                            duration = "${42 + (eIdx * 3) % 15} min",
                            synopsis = "As consequências das escolhas passadas vêm à tona neste capítulo eletrizante de ${item.title}. Segredos do passado começam a ser expostos.",
                            thumbnailUrl = when (eIdx % 4) {
                                0 -> "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=300&q=80"
                                1 -> "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=300&q=80"
                                2 -> "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=300&q=80"
                                else -> "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=300&q=80"
                            },
                            releaseDate = releaseDate,
                            isReleased = isReleased
                        )
                    }
                )
            }
        }
    }
}
