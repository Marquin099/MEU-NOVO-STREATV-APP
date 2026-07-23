package com.example.ui.screens

import com.example.LocalIPTVViewModel

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.size.Size
import coil.imageLoader
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.model.PlaylistItem
import com.example.data.util.EPGGenerator
import com.example.data.util.cleanSeriesName
import com.example.ui.components.StreaTVLogo
import com.example.ui.viewmodel.IPTVViewModel
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: IPTVViewModel
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val bannerItems by viewModel.bannerItems.collectAsState()
    val bannerIndex by viewModel.bannerIndex.collectAsState()

    val trendingItems by viewModel.trendingItems.collectAsState()
    val popularItems by viewModel.popularItems.collectAsState()
    val trailerItems by viewModel.trailerItems.collectAsState()

    val channels by viewModel.filteredChannels.collectAsState()
    val movies by viewModel.filteredMovies.collectAsState()
    val series by viewModel.filteredSeries.collectAsState()
    val groupedChannels by viewModel.groupedChannels.collectAsState()
    val groupedMovies by viewModel.groupedMovies.collectAsState()
    val groupedSeries by viewModel.groupedSeries.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    val isSearchActive by viewModel.isSearchActive.collectAsState()

    val scope = rememberCoroutineScope()
    val continueWatchingFirstItemFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val navBarFocusRequester = remember { FocusRequester() }
    val trendingFirstItemFocusRequester = remember { FocusRequester() }
    val trailersFirstItemFocusRequester = remember { FocusRequester() }
    val popularFirstItemFocusRequester = remember { FocusRequester() }

    val context = LocalContext.current

    val scrollState = rememberScrollState(initial = viewModel.mainScrollPosition)

    val onItemPlayOrDetail: (PlaylistItem) -> Unit = { item ->
        viewModel.mainScrollPosition = scrollState.value
        viewModel.lastClickedItemId = item.id
        if (item.type == "movie" || item.type == "series" || item.type == "series_episode") {
            viewModel.redirectDetailOnPlayerExit = item
            viewModel.navigateTo(Screen.Detail(item, autoPlay = false))
        } else {
            viewModel.navigateTo(Screen.Player(item))
        }
    }

    val onContinueWatchingClick: (PlaylistItem) -> Unit = { item ->
        viewModel.mainScrollPosition = scrollState.value
        viewModel.lastClickedItemId = item.id
        if (item.type == "series" || item.type == "series_episode") {
            viewModel.redirectDetailOnPlayerExit = item
            scope.launch {
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
                    viewModel.navigateTo(Screen.Detail(item, autoPlay = false))
                }
            }
        } else if (item.type == "movie") {
            viewModel.redirectDetailOnPlayerExit = item
            scope.launch {
                val realMovie = viewModel.verifyAndGetRealPlaylistItem(item)
                if (realMovie != null) {
                    viewModel.navigateTo(Screen.Player(realMovie))
                } else {
                    viewModel.navigateTo(Screen.Player(item))
                }
            }
        } else {
            viewModel.navigateTo(Screen.Player(item))
        }
    }

    // Custom focused category tracker for the requested collapse transition
    var focusedCategoryIndex by remember(selectedTab) { mutableStateOf(viewModel.focusedCategoryIndex) }

    LaunchedEffect(focusedCategoryIndex) {
        viewModel.focusedCategoryIndex = focusedCategoryIndex
    }

    // Save scroll position when the user scrolls or when layout settles (avoiding resetting to 0 during empty initial composition)
    LaunchedEffect(scrollState.value, scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress || scrollState.value > 0) {
            viewModel.mainScrollPosition = scrollState.value
        }
    }

    // Explicitly restore scroll position when content layout becomes ready
    val isContentReady = remember(selectedTab, trendingItems, trailerItems, popularItems, movies, series, channels) {
        when (selectedTab) {
            "Inicio" -> trendingItems.isNotEmpty() && trailerItems.isNotEmpty() && popularItems.isNotEmpty()
            "Filmes" -> movies.isNotEmpty()
            "Series" -> series.isNotEmpty()
            "Canais" -> channels.isNotEmpty()
            else -> true
        }
    }

    LaunchedEffect(isContentReady) {
        if (isContentReady && viewModel.mainScrollPosition > 0) {
            delay(150) // Tiny delay to allow full layout pass to execute
            try {
                scrollState.scrollTo(viewModel.mainScrollPosition)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Fast scroll to top, reset category focus, and request focus on the header tabs
    val scrollToTopAndFocusHeader: () -> Unit = {
        scope.launch {
            scrollState.scrollTo(0)
            focusedCategoryIndex = 0
            try {
                navBarFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val isBackEnabled = isSearchActive || scrollState.value > 0 || focusedCategoryIndex > 0 || selectedTab != "Inicio"

    BackHandler(enabled = isBackEnabled) {
        if (isSearchActive) {
            viewModel.setSearchActive(false)
            viewModel.updateSearchQuery("")
        } else if (scrollState.value > 0 || focusedCategoryIndex > 0) {
            scrollToTopAndFocusHeader()
        } else if (selectedTab != "Inicio") {
            scope.launch {
                viewModel.setTab("Inicio")
                try {
                    navBarFocusRequester.requestFocus()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Dynamic background color & image state
    var currentBackgroundColor by remember { mutableStateOf(Color(0xFF050505)) }
    var currentBackgroundImageUrl by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (keyEvent.key == Key.Escape || keyEvent.key == Key.Back) {
                        if (scrollState.value > 0 || focusedCategoryIndex > 0) {
                            scrollToTopAndFocusHeader()
                            true // Consumes key event, prevents default back action
                        } else {
                            false // Let other back handlers (like BackHandler) handle it
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .background(Color(0xFF08080A))
    ) {
        val screenMaxHeight = maxHeight

        // Dynamic background backdrop image layer from selected item (moderate blur so image is visible)
        Crossfade(
            targetState = currentBackgroundImageUrl,
            animationSpec = tween(700),
            modifier = Modifier.fillMaxSize(),
            label = "ScreenBackgroundImageCrossfade"
        ) { imageUrl ->
            if (!imageUrl.isNullOrEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .size(width = 1280, height = 720)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(4.dp)
                    )
                    // Semi-transparent scrim overlay for readability while keeping backdrop visible
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.50f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF08080A).copy(alpha = 0.65f),
                                        Color.Transparent,
                                        Color(0xFF08080A).copy(alpha = 0.90f)
                                    )
                                )
                            )
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF08080A))
                )
            }
        }
        // A. Bottom layer: Scrollable Screen Content
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isSearchActive || selectedTab == "Canais") Modifier else Modifier.verticalScroll(scrollState)
                    )
                    .padding(bottom = 36.dp)
            ) {
                // Top spacer so that scrolled content starts below the floating top navigation bar nicely,
                // but scrolls behind it as the user scrolls down (flutuante sobre o aplicativo)
                Spacer(modifier = Modifier.height(72.dp))
                // 2. Banner and Grid layouts based on selected Tab
                // 2. Banner and Grid layouts based on selected Tab with AnimatedContent transitions
                val stateKey = if (isSearchActive) "Search" else selectedTab
                val targetState = stateKey
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSearchActive || selectedTab == "Canais") Modifier.weight(1f) else Modifier
                        )
                ) {
                    when (targetState) {
                        "Search" -> {
                            NetflixSearchScreen(
                                movies = movies,
                                series = series,
                                channels = channels,
                                onPlay = onItemPlayOrDetail,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                firstItemFocusRequester = contentFocusRequester,
                                lastClickedItemId = viewModel.lastClickedItemId
                            )
                        }
                        "Inicio" -> {
                            // Evita que banner e fileiras apareçam em tempos diferentes.
                            // A Home é revelada somente quando os três blocos remotos estão prontos.
                            val isHomeReady = trendingItems.isNotEmpty() &&
                                    trailerItems.isNotEmpty() &&
                                    popularItems.isNotEmpty()

                            if (isHomeReady) {
                                Column {
                                    val continueWatchingList by viewModel.continueWatching.collectAsState(initial = emptyList())

                                    // 1. Central Sliding Banner (Cycling through Tendências/Featured)
                                    val activeBannerItems = trendingItems

                                    LaunchedEffect(activeBannerItems) {
                                        if (activeBannerItems.isNotEmpty()) {
                                            viewModel.startBannerTimer(activeBannerItems.size)
                                        }
                                    }

                                    if (activeBannerItems.isNotEmpty()) {
                                        val safeIndex = if (activeBannerItems.isNotEmpty()) bannerIndex % activeBannerItems.size else 0
                                        val activeBanner = activeBannerItems[safeIndex]

                                        // Atualiza filmes e séries do banner mesmo quando já existe logo:
                                        // a URL inicial pode estar em en-US e ser substituída pela pt-BR.
                                        LaunchedEffect(activeBanner.id, activeBanner.type) {
                                            val bgUrl = activeBanner.getBestBackgroundImageUrl()
                                            if (!bgUrl.isNullOrEmpty()) {
                                                currentBackgroundImageUrl = bgUrl
                                            }
                                            if (activeBanner.type != "live") {
                                                viewModel.enrichItemOnDemand(activeBanner)
                                            }
                                        }

                                        // Pré-carrega fundo E LOGO do próximo banner. A troca ocorre a
                                        // cada 5 s; assim ambos já estão no cache quando ele aparece.
                                        val nextIndex = (safeIndex + 1) % activeBannerItems.size
                                        val nextBannerItem = activeBannerItems[nextIndex]
                                        val nextBackdrop = nextBannerItem.getEffectiveBackdropUrl()
                                        val nextTitleLogo = nextBannerItem.titleLogoUrl?.takeIf { it.isNotBlank() }
                                            ?: nextBannerItem.logo?.takeIf { it.isNotBlank() }
                                            ?: nextBannerItem.getEffectiveTitleLogoUrl()
                                        if (!nextBackdrop.isNullOrEmpty() || !nextTitleLogo.isNullOrEmpty()) {
                                            val context = LocalContext.current
                                            LaunchedEffect(nextBackdrop, nextTitleLogo) {
                                                listOfNotNull(nextBackdrop, nextTitleLogo)
                                                    .distinct()
                                                    .forEach { imageUrl ->
                                                        context.imageLoader.enqueue(
                                                            ImageRequest.Builder(context)
                                                                .data(imageUrl)
                                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                                .build()
                                                        )
                                                    }
                                            }
                                        }

                                        FeaturedHeroBanner(
                                            item = activeBanner,
                                            onNext = { viewModel.nextBanner(activeBannerItems.size) }, viewModel = viewModel,
                                            onPrev = { viewModel.prevBanner(activeBannerItems.size) },
                                            onClick = { onItemPlayOrDetail(activeBanner) },
                                            onColorExtracted = { color -> currentBackgroundColor = color },
                                            onUpPressed = { navBarFocusRequester.requestFocus() },
                                            onDownPressed = {
                                                if (continueWatchingList.isNotEmpty()) {
                                                    continueWatchingFirstItemFocusRequester.requestFocus()
                                                } else {
                                                    trendingFirstItemFocusRequester.requestFocus()
                                                }
                                            },
                                            modifier = Modifier.focusRequester(contentFocusRequester)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // 3. Continue Watching Row (preserves user progress)
                                    ContinueWatchingRowSection(
                                        viewModel = viewModel,
                                        onPlay = onContinueWatchingClick,
                                        firstItemFocusRequester = continueWatchingFirstItemFocusRequester
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 4. Favorites Row
                                    FavoriteRowSection(
                                        viewModel = viewModel,
                                        onPlay = onItemPlayOrDetail
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 5. TMDB Tendências Section
                                    if (trendingItems.isNotEmpty()) {
                                        TrendingSection(
                                            trendingItems = trendingItems,
                                            onPlay = onItemPlayOrDetail,
                                            firstItemFocusRequester = trendingFirstItemFocusRequester,
                                            downFocusRequester = trailersFirstItemFocusRequester
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    // 6. TMDB Últimos Trailers Section
                                    if (trailerItems.isNotEmpty()) {
                                        LatestTrailersSection(
                                            trailerItems = trailerItems,
                                            onPlay = onItemPlayOrDetail,
                                            firstItemFocusRequester = trailersFirstItemFocusRequester,
                                            downFocusRequester = popularFirstItemFocusRequester
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    // 7. TMDB Os Mais Populares Section
                                    if (popularItems.isNotEmpty()) {
                                        MostPopularSection(
                                            popularItems = popularItems,
                                            onPlay = onItemPlayOrDetail,
                                            firstItemFocusRequester = popularFirstItemFocusRequester
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(620.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = Color(0xFFE50914))
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "Carregando tela inicial...",
                                            color = Color.LightGray,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            // FILMES / SERIES / CANAIS
                            val itemsSource = when (targetState) {
                                "Canais" -> channels
                                "Filmes" -> movies
                                "Series" -> series
                                else -> emptyList()
                            }
                            val groupedSource = when (targetState) {
                                "Canais" -> groupedChannels
                                "Filmes" -> groupedMovies
                                "Series" -> groupedSeries
                                else -> emptyMap()
                            }

                            var activeFocusedItem by remember(targetState) { mutableStateOf<PlaylistItem?>(null) }

                            LaunchedEffect(activeFocusedItem?.id, targetState) {
                                activeFocusedItem?.let { item ->
                                    val bgUrl = item.getBestBackgroundImageUrl()
                                    if (!bgUrl.isNullOrEmpty()) {
                                        currentBackgroundImageUrl = bgUrl
                                    }
                                    currentBackgroundColor = getItemAmbientColor(item)
                                } ?: run {
                                    itemsSource.firstOrNull()?.getBestBackgroundImageUrl()?.let { bgUrl ->
                                        currentBackgroundImageUrl = bgUrl
                                    }
                                }
                            }

                            if (itemsSource.isNotEmpty()) {
                                if (targetState == "Canais") {
                                    ChannelsDualPaneLayout(
                                        categories = categories,
                                        groupedChannels = groupedChannels,
                                        onPlay = onItemPlayOrDetail,
                                        viewModel = viewModel,
                                        focusedCategoryIndex = focusedCategoryIndex,
                                        onCategoryChanged = { newIdx ->
                                            focusedCategoryIndex = newIdx
                                        },
                                        contentFocusRequester = contentFocusRequester
                                    )
                                } else {
                                    // Automatic Focus request when active category changes to prevent focus from jumping back to the top
                                    LaunchedEffect(focusedCategoryIndex) {
                                        if ((targetState == "Filmes" || targetState == "Series") && viewModel.lastClickedItemId == null) {
                                            delay(150) // Tiny delay to allow AnimatedVisibility layout to construct the new card
                                            try {
                                                contentFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                android.util.Log.e("MainScreen", "Failed to focus new category row", e)
                                            }
                                        }
                                    }

                                    LaunchedEffect(targetState) {
                                        if (targetState == "Filmes" || targetState == "Series") {
                                            scrollState.scrollTo(0)
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Cinematic dynamic banner at the top of Movies and Series screens
                                        if (false) {
                                            val bannerItem = activeFocusedItem ?: itemsSource.firstOrNull()
                                            bannerItem?.let { bItem ->
                                                val calculatedBannerHeight = (screenMaxHeight - 200.dp).coerceIn(320.dp, 400.dp)
                                                FeaturedHeroBanner(
                                                    item = bItem,
                                                    onNext = { /* No auto rotation needed */ }, viewModel = viewModel,
                                                    onPrev = { /* No auto rotation needed */ },
                                                    onClick = { onItemPlayOrDetail(bItem) },
                                                    onDownPressed = { contentFocusRequester.requestFocus() },
                                                    onUpPressed = { navBarFocusRequester.requestFocus() },
                                                    bannerHeight = calculatedBannerHeight,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        // Render category headers vertically
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp)
                                                .padding(bottom = 16.dp)
                                                .onPreviewKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        when (keyEvent.key) {
                                                            Key.DirectionDown -> {
                                                                if (focusedCategoryIndex < categories.size - 1) {
                                                                    focusedCategoryIndex++
                                                                    true
                                                                } else false
                                                            }
                                                            Key.DirectionUp -> {
                                                                if (focusedCategoryIndex > 0) {
                                                                    focusedCategoryIndex--
                                                                    true
                                                                } else {
                                                                    // Return focus to the top navigation bar when scrolled to the top category
                                                                    navBarFocusRequester.requestFocus()
                                                                    true
                                                                }
                                                            }
                                                            else -> false
                                                        }
                                                    } else false
                                                }
                                        ) {
                                            if (targetState != "Filmes" && targetState != "Series") {
                                                Text(
                                                    text = "$targetState • Categorias Disponíveis",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.LightGray,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(bottom = 16.dp)
                                                )
                                            }

                                            categories.forEachIndexed { idx, category ->
                                                // If Filmes or Series, show ONLY the focused category row (idx == focusedCategoryIndex)
                                                val isVisible = if (targetState == "Filmes" || targetState == "Series") {
                                                    idx == focusedCategoryIndex
                                                } else {
                                                    idx >= focusedCategoryIndex
                                                }

                                                // Static visibility block prevents dynamic animation height truncation / layout cuts when returning from DetailScreen
                                                if (isVisible) {
                                                    val catItems = remember(groupedSource, category) {
                                                        groupedSource[category] ?: emptyList()
                                                    }
                                                    if (catItems.isNotEmpty()) {
                                                        if (targetState == "Filmes" || targetState == "Series") {
                                                            MoviesSeriesCategoryRow(
                                                                categoryType = targetState,
                                                                title = category,
                                                                items = catItems,
                                                                isSelectedHighlight = (idx == focusedCategoryIndex),
                                                                onPlay = onItemPlayOrDetail,
                                                                onItemFocused = { item ->
                                                                    activeFocusedItem = item
                                                                    val bgUrl = item.getBestBackgroundImageUrl()
                                                                    if (!bgUrl.isNullOrEmpty()) {
                                                                        currentBackgroundImageUrl = bgUrl
                                                                    }
                                                                    if (item.type != "live") {
                                                                        viewModel.enrichItemOnDemand(item)
                                                                    }
                                                                },
                                                                firstItemFocusRequester = if (idx == focusedCategoryIndex) contentFocusRequester else null,
                                                                onItemRendered = { item ->
                                                                    if (item.type != "live") {
                                                                        viewModel.enrichItemOnDemand(item)
                                                                    }
                                                                }
                                                            )
                                                        } else {
                                                            CategoryRow(
                                                                title = category,
                                                                items = catItems,
                                                                isSelectedHighlight = (idx == focusedCategoryIndex),
                                                                onPlay = onItemPlayOrDetail,
                                                                onItemFocused = { item ->
                                                                    activeFocusedItem = item
                                                                    if (item.type != "live") {
                                                                        viewModel.enrichItemOnDemand(item)
                                                                    }
                                                                },
                                                                firstItemFocusRequester = if (idx == focusedCategoryIndex) contentFocusRequester else null,
                                                                onItemRendered = { item ->
                                                                    if (item.type != "live") {
                                                                        viewModel.enrichItemOnDemand(item)
                                                                    }
                                                                }
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Empty State
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Tv, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(64.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Nenhum item carregado para a aba $targetState.", color = Color.Gray, fontSize = 14.sp)
                                        Text("Verifique sua lista de canais IPTV.", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // B. Top layer: Floating Top Navigation & Overlays Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            // 1. Netflix-like Top Navigation Bar floating on top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF050505).copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Center Navigation Tabs ("Inicio", "Canais", "Filmes", "Series")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    val tabs = listOf("Inicio", "Canais", "Filmes", "Series")
                    tabs.forEach { tab ->
                        var isTabFocused by remember { mutableStateOf(false) }
                        val isSelected = selectedTab == tab

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(
                                    if (isSelected) Color(0xFFE50914)
                                    else if (isTabFocused) Color.White.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = if (isTabFocused) 2.dp else 0.dp,
                                    color = if (isTabFocused) Color.White else Color.Transparent,
                                    shape = MaterialTheme.shapes.extraLarge
                                )
                                .clickable {
                                    viewModel.setTab(tab)
                                    viewModel.setSearchActive(false)
                                    viewModel.updateSearchQuery("")
                                }
                                .onFocusChanged { isTabFocused = it.isFocused }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                        try {
                                            contentFocusRequester.requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    } else false
                                }
                                .focusRequester(if (isSelected) navBarFocusRequester else remember { FocusRequester() })
                                .focusable()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected || isTabFocused) Color.White else Color.LightGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Right Bar: Search Icon, Active Profile Avatar, Sync Button, Logout Button (all standard size 40.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    // Search Loupe (Lupa)
                    var isSearchIconFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSearchIconFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                width = if (isSearchIconFocused) 2.dp else 0.dp,
                                color = if (isSearchIconFocused) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { viewModel.setSearchActive(!isSearchActive) }
                            .onFocusChanged { isSearchIconFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                    try {
                                        contentFocusRequester.requestFocus()
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                } else false
                            }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Pesquisar",
                            tint = if (isSearchActive) Color(0xFFE50914) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Profile Indicator (Avatar)
                    selectedProfile?.let { profile ->
                        val avatarColor = try {
                            Color(android.graphics.Color.parseColor(profile.avatarColorHex))
                        } catch (e: Exception) {
                            Color(0xFFE50914)
                        }
                        val context = LocalContext.current
                        val svgImageLoader = remember {
                            ImageLoader.Builder(context)
                                .components {
                                    add(SvgDecoder.Factory())
                                }
                                .build()
                        }
                        var isProfileFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isProfileFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    width = if (isProfileFocused) 2.dp else 0.dp,
                                    color = if (isProfileFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.navigateTo(Screen.ProfileSelection) }
                                .onFocusChanged { isProfileFocused = it.isFocused }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                        try {
                                            contentFocusRequester.requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    } else false
                                }
                                .focusable(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profile.avatarIconName.startsWith("http")) {
                                    SubcomposeAsyncImage(
                                        model = profile.avatarIconName,
                                        contentDescription = profile.name,
                                        imageLoader = svgImageLoader,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        loading = {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 1.dp,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        },
                                        error = {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = profile.name,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = profile.name,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Sync Playlist Button
                    var isSyncFocused by remember { mutableStateOf(false) }
                    val isSyncing = syncState is SyncState.Syncing

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSyncFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                width = if (isSyncFocused) 2.dp else 0.dp,
                                color = if (isSyncFocused) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable(enabled = !isSyncing) { viewModel.triggerManualSync() }
                            .onFocusChanged { isSyncFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                    try {
                                        contentFocusRequester.requestFocus()
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                } else false
                            }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sincronizar Lista",
                            tint = if (isSyncing) Color(0xFFE50914) else Color.LightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Logout Exit button
                    var isLogoutFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isLogoutFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                width = if (isLogoutFocused) 2.dp else 0.dp,
                                color = if (isLogoutFocused) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { viewModel.logout() }
                            .onFocusChanged { isLogoutFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                    try {
                                        contentFocusRequester.requestFocus()
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                } else false
                            }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Sair",
                            tint = Color.LightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 1.5 Real-time Search Input Field Row (Disabled - integrated in NetflixSearchScreen)
            if (false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    var isSearchInputFocused by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Pesquisar canais, filmes ou gêneros...", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Pesquisa", tint = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE50914),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .onFocusChanged { isSearchInputFocused = it.isFocused }
                            .border(
                                width = if (isSearchInputFocused) 2.dp else 0.dp,
                                color = if (isSearchInputFocused) Color(0xFFE50914) else Color.Transparent,
                                shape = MaterialTheme.shapes.small
                            )
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                    try {
                                        contentFocusRequester.requestFocus()
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                } else false
                            }
                    )
                }
            }

            // 1.6 Sync progress overlay bar
            if (syncState is SyncState.Syncing) {
                val state = syncState as SyncState.Syncing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE50914))
                        .padding(vertical = 4.dp, horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${state.message} (${(state.progress * 100).toInt()}%)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// 2. Central Floating Card Banner (Enlarged and Cinematic)
fun PlaylistItem.getClassificationBadge(): String {
    val titleLower = title.lowercase()
    val ratingVal = rating?.toDoubleOrNull() ?: 8.0
    return when {
        titleLower.contains("furious", ignoreCase = true) ||
                titleLower.contains("obsessão", ignoreCase = true) ||
                titleLower.contains("demônio", ignoreCase = true) ||
                titleLower.contains("morte", ignoreCase = true) -> "16"

        titleLower.contains("backrooms", ignoreCase = true) ||
                titleLower.contains("rancho", ignoreCase = true) ||
                titleLower.contains("yellowstone", ignoreCase = true) -> "14"

        titleLower.contains("odisseia", ignoreCase = true) ||
                titleLower.contains("perder", ignoreCase = true) -> "12"

        titleLower.contains("moana", ignoreCase = true) ||
                titleLower.contains("pradaria", ignoreCase = true) -> "L"

        ratingVal >= 8.5 -> "14"
        ratingVal >= 8.0 -> "12"
        else -> "10"
    }
}

fun PlaylistItem.getClassificationColor(): Color {
    return when (getClassificationBadge()) {
        "L" -> Color(0xFF00A34F) // Green
        "10" -> Color(0xFF009FD6) // Blue
        "12" -> Color(0xFFF7BD15) // Yellow
        "14" -> Color(0xFFE87320) // Orange
        "16" -> Color(0xFFE31B23) // Red
        "18" -> Color(0xFF111111) // Black
        else -> Color.Gray
    }
}

fun PlaylistItem.getPortugueseTypeLabel(): String {
    return when (type) {
        "movie" -> "Filme"
        "series" -> "Série"
        "live" -> "Canal Ao Vivo"
        else -> "Destaque"
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun FeaturedHeroBanner(
    item: PlaylistItem,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClick: () -> Unit,
    viewModel: IPTVViewModel,
    onColorExtracted: (Color) -> Unit = {},
    onUpPressed: () -> Unit = {},
    onDownPressed: () -> Unit = {},
    modifier: Modifier = Modifier,
    bannerHeight: androidx.compose.ui.unit.Dp = 460.dp,
    categoryTitle: String? = null
) {
    var isBannerFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val localDensity = androidx.compose.ui.platform.LocalDensity.current
    val extraTopPadding = with(localDensity) { 76.dp.toPx() }

    LaunchedEffect(isBannerFocused) {
        if (isBannerFocused) {
            try {
                bringIntoViewRequester.bringIntoView(
                    androidx.compose.ui.geometry.Rect(
                        left = 0f,
                        top = -extraTopPadding,
                        right = 0f,
                        bottom = 0f
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("FeaturedHeroBanner", "Failed to bring banner into view", e)
            }
        }
    }

    val bannerScale by animateFloatAsState(
        targetValue = if (isBannerFocused) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val bannerTranslationY by animateFloatAsState(
        targetValue = if (isBannerFocused) -8f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val bannerElevation by animateDpAsState(
        targetValue = if (isBannerFocused) 16.dp else 2.dp,
        animationSpec = spring()
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .graphicsLayer {
                scaleX = bannerScale
                scaleY = bannerScale
                translationY = bannerTranslationY * density
            }
            .onFocusChanged {
                isBannerFocused = it.isFocused
                if (it.isFocused) {
                    coroutineScope.launch {
                        try {
                            bringIntoViewRequester.bringIntoView(
                                androidx.compose.ui.geometry.Rect(
                                    left = 0f,
                                    top = -extraTopPadding,
                                    right = 0f,
                                    bottom = 0f
                                )
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("FeaturedHeroBanner", "Failed to bring banner into view on focus changed", e)
                        }
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionRight -> {
                            onNext()
                            true
                        }
                        Key.DirectionLeft -> {
                            onPrev()
                            true
                        }
                        Key.DirectionUp -> {
                            onUpPressed()
                            true
                        }
                        Key.DirectionDown -> {
                            onDownPressed()
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .border(
                width = if (isBannerFocused) 2.dp else 0.dp,
                color = if (isBannerFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = bannerElevation)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Background dark cinematic gradient (always present as a premium fallback/placeholder!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF300000), Color(0xFF0F0F14))
                        )
                    )
            )

            // Transition Content: Slides and crossfades the backgrounds/details perfectly
            AnimatedContent(
                targetState = item,
                transitionSpec = {
                    (slideInHorizontally(animationSpec = tween(500)) { it / 6 } + fadeIn(animationSpec = tween(500)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(500)) { -it / 6 } + fadeOut(animationSpec = tween(500)))
                },
                label = "HeroBannerTransition",
                modifier = Modifier.fillMaxSize()
            ) { currentItem ->
                var currentRealSeasonsCount by remember(currentItem.id) { mutableStateOf<Int?>(null) }
                var currentRealEpisodesCount by remember(currentItem.id) { mutableStateOf<Int?>(null) }

                LaunchedEffect(currentItem) {
                    if (currentItem.type == "series") {
                        try {
                            val seasonsList = viewModel.getRealSeasons(currentItem)
                            if (seasonsList.isNotEmpty()) {
                                currentRealSeasonsCount = seasonsList.size
                                currentRealEpisodesCount = seasonsList.sumOf { it.episodes?.size ?: 0 }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {

                    // Backdrop Image (High resolution with smart downscaling & memory cache)
                    val effectiveBackdrop = currentItem.getEffectiveBackdropUrl()
                    var isBackdropError by remember(currentItem.id) { mutableStateOf(false) }
                    if (!effectiveBackdrop.isNullOrEmpty() && !isBackdropError) {
                        val context = LocalContext.current
                        val backdropRequest = remember(effectiveBackdrop) {
                            ImageRequest.Builder(context)
                                .data(effectiveBackdrop)
                                .crossfade(true)
                                .size(width = 1920, height = 1080) // Downscale to standard cinematic viewport to prevent glGetError texture overflow
                                .bitmapConfig(Bitmap.Config.ARGB_8888)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(false) // Required for Palette to read Bitmap pixels
                                .listener(
                                    onSuccess = { _, result ->
                                        val drawable = result.drawable
                                        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
                                            val bmp = Bitmap.createBitmap(
                                                if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512,
                                                if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 288,
                                                Bitmap.Config.ARGB_8888
                                            )
                                            val canvas = android.graphics.Canvas(bmp)
                                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                                            drawable.draw(canvas)
                                            bmp
                                        }
                                        Palette.from(bitmap).generate { palette ->
                                            palette?.let { p ->
                                                val extractedColor = p.getDarkVibrantColor(
                                                    p.getVibrantColor(
                                                        p.getDominantColor(0xFF050505.toInt())
                                                    )
                                                )
                                                onColorExtracted(Color(extractedColor))
                                            }
                                        }
                                    }
                                )
                                .build()
                        }
                        AsyncImage(
                            model = backdropRequest,
                            contentDescription = currentItem.title,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                            onState = { state ->
                                if (state is AsyncImagePainter.State.Error) {
                                    isBackdropError = true
                                    android.util.Log.e("HeroBanner", "Failed to load backdrop: $effectiveBackdrop, hiding image to prevent OpenGL texture crash.", state.result.throwable)
                                }
                            }
                        )
                    }

                    // Horizontal gradient (Vignette left-to-right for readability)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.9f),
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Vertical gradient (Fade bottom-to-top for melting effect into screen)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Black.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )

                    // Metadata & Details Info at the Bottom Left
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.68f)
                            .padding(24.dp)
                            .align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // 1. Identificador de Tipo (F ou S) ou Nome da Categoria LOGO ACIMA do logotipo/título
                        if (!categoryTitle.isNullOrEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = categoryTitle.uppercase(),
                                        style = TextStyle(
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp
                                        )
                                    )
                                }
                            }
                        } else if (currentItem.type == "movie" || currentItem.type == "series") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                val (letter, word) = if (currentItem.type == "series") {
                                    "S" to "S É R I E"
                                } else {
                                    "F" to "F I L M E"
                                }
                                Text(
                                    text = letter,
                                    style = TextStyle(
                                        color = Color(0xFFE50914), // Netflix Red
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.SansSerif,
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.85f),
                                            offset = Offset(1f, 1f),
                                            blurRadius = 3f
                                        )
                                    )
                                )
                                Text(
                                    text = word,
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.85f),
                                            offset = Offset(1f, 1f),
                                            blurRadius = 3f
                                        )
                                    )
                                )
                            }
                        }

                        // Prioridade: logo localizado retornado pelo enriquecimento (pt-BR),
                        // depois a URL legada e, por último, os fallbacks conhecidos. Se uma
                        // URL falhar, a próxima é tentada antes de mostrar o título em texto.
                        val titleLogoCandidates = remember(currentItem) {
                            listOfNotNull(
                                currentItem.titleLogoUrl?.takeIf { it.isNotBlank() },
                                currentItem.logo?.takeIf { it.isNotBlank() },
                                currentItem.getEffectiveTitleLogoUrl()
                            ).distinct()
                        }
                        // mutableStateOf é compatível com versões mais antigas do Compose.
                        var currentLogoIndex by remember(currentItem.id) { mutableStateOf(0) }
                        val titleLogo = titleLogoCandidates.getOrNull(currentLogoIndex)
                        // A URL pode chegar alguns segundos depois, quando o repositório termina
                        // a consulta ao TMDB. O estado precisa ser reiniciado pela URL — e não
                        // apenas pelo id — para que um timeout antigo não esconda o novo logo.
                        var isLogoLoadingError by remember(titleLogo) { mutableStateOf(false) }
                        var isLogoLoaded by remember(titleLogo) { mutableStateOf(false) }
                        var applyWhiteFilter by remember(titleLogo) { mutableStateOf(false) }

                        LaunchedEffect(titleLogo, currentLogoIndex) {
                            isLogoLoaded = false
                            isLogoLoadingError = false
                            applyWhiteFilter = false
                        }

                        val context = LocalContext.current
                        val logoRequest = remember(titleLogo) {
                            if (titleLogo.isNullOrEmpty()) null else {
                                ImageRequest.Builder(context)
                                    .data(titleLogo)
                                    .crossfade(true)
                                    // Não decodifique o PNG original (muitas logos têm milhares
                                    // de pixels). O AsyncImage usa o tamanho real do banner,
                                    // carrega muito mais rápido e evita o falso timeout.
                                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .allowHardware(false) // Mandatory to read pixels on CPU for palette extraction and prevent OpenGL leaks
                                    .listener(
                                        onSuccess = { _, result ->
                                            val drawable = result.drawable
                                            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
                                                val bmp = Bitmap.createBitmap(
                                                    if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 200,
                                                    if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100,
                                                    Bitmap.Config.ARGB_8888
                                                )
                                                val canvas = android.graphics.Canvas(bmp)
                                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                                drawable.draw(canvas)
                                                bmp
                                            }

                                            // Advanced pixel scanning to detect if the logo is predominantly black/dark grey
                                            var totalNonTransparent = 0
                                            var darkNonTransparent = 0
                                            var colorfulCount = 0

                                            val stepX = (bitmap.width / 15).coerceAtLeast(1)
                                            val stepY = (bitmap.height / 15).coerceAtLeast(1)

                                            for (x in 0 until bitmap.width step stepX) {
                                                for (y in 0 until bitmap.height step stepY) {
                                                    val pixel = bitmap.getPixel(x, y)
                                                    val alpha = android.graphics.Color.alpha(pixel)
                                                    if (alpha > 40) {
                                                        val r = android.graphics.Color.red(pixel)
                                                        val g = android.graphics.Color.green(pixel)
                                                        val b = android.graphics.Color.blue(pixel)

                                                        val max = maxOf(r, g, b)
                                                        val min = minOf(r, g, b)
                                                        val saturationDiff = max - min

                                                        totalNonTransparent++

                                                        if (saturationDiff < 40 && max < 75) {
                                                            darkNonTransparent++
                                                        } else if (saturationDiff > 45) {
                                                            colorfulCount++
                                                        }
                                                    }
                                                }
                                            }

                                            Palette.from(bitmap).generate { palette ->
                                                palette?.let { p ->
                                                    val dominantColorInt = p.getDominantColor(0xFFFFFFFF.toInt())
                                                    val dRed = android.graphics.Color.red(dominantColorInt)
                                                    val dGreen = android.graphics.Color.green(dominantColorInt)
                                                    val dBlue = android.graphics.Color.blue(dominantColorInt)
                                                    val dMax = maxOf(dRed, dGreen, dBlue)
                                                    val dMin = minOf(dRed, dGreen, dBlue)
                                                    val dSaturation = dMax - dMin

                                                    val isDominantBlack = dSaturation < 40 && dMax < 75
                                                    val isPixelScanBlack = if (totalNonTransparent > 0) {
                                                        val darkRatio = darkNonTransparent.toFloat() / totalNonTransparent
                                                        val colorfulRatio = colorfulCount.toFloat() / totalNonTransparent
                                                        darkRatio > 0.4f && colorfulRatio < 0.15f
                                                    } else {
                                                        false
                                                    }

                                                    applyWhiteFilter = isDominantBlack || isPixelScanBlack
                                                }
                                            }
                                        }
                                    )
                                    .build()
                            }
                        }

                        // Console Log check as requested: Log currentItem and check if 'logo' or 'titleLogoUrl' is present
                        LaunchedEffect(currentItem) {
                            android.util.Log.d("HeroBanner", "movie/series: id=${currentItem.id}, title='${currentItem.title}', logo='${currentItem.logo}', titleLogoUrl='${currentItem.titleLogoUrl}'")
                        }

                        Box(modifier = Modifier.padding(bottom = 12.dp)) {
                            // O texto só é mostrado quando não existe logo ou quando todas as
                            // URLs falharam de verdade. Durante o carregamento, o AsyncImage
                            // continua livre para desenhar assim que receber o bitmap.
                            if (titleLogo.isNullOrEmpty() || isLogoLoadingError) {
                                // Clean stylized Netflix-like movie/series text title (Plan B fallback / loading placeholder)
                                Text(
                                    text = currentItem.title,
                                    style = TextStyle(
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.SansSerif,
                                        color = Color.White,
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.85f),
                                            offset = Offset(2f, 4f),
                                            blurRadius = 8f
                                        )
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!titleLogo.isNullOrEmpty() && !isLogoLoadingError) {
                                AsyncImage(
                                    model = logoRequest ?: titleLogo,
                                    contentDescription = currentItem.title,
                                    colorFilter = if (applyWhiteFilter) ColorFilter.tint(Color.White) else null,
                                    modifier = Modifier
                                        .widthIn(max = 350.dp)
                                        .heightIn(max = 120.dp)
                                        .graphicsLayer {
                                            // Standard drop-shadow approximation via graphicsLayer elevation/shadow
                                            shadowElevation = 8f
                                        },
                                    contentScale = ContentScale.Fit,
                                    onState = { state ->
                                        if (state is AsyncImagePainter.State.Success) {
                                            isLogoLoaded = true
                                        }
                                        if (state is AsyncImagePainter.State.Error) {
                                            android.util.Log.e("HeroBanner", "Failed to load logo: $titleLogo for ${currentItem.title}", state.result.throwable)
                                            if (currentLogoIndex < titleLogoCandidates.lastIndex) {
                                                currentLogoIndex++
                                            } else {
                                                isLogoLoadingError = true
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // 2. Alinhamento de Informações (Estilo Netflix) - Ano • Gênero • Dados adicionais em texto corrido
                        val metadataParts = remember(currentItem, currentRealSeasonsCount, currentRealEpisodesCount) {
                            val parts = mutableListOf<String>()

                            // Year
                            currentItem.year?.let { y ->
                                val cleanYear = if (y.length > 4 && y.any { it.isDigit() }) {
                                    val match = Regex("\\d{4}").find(y)
                                    match?.value ?: y
                                } else y
                                if (cleanYear.isNotBlank()) parts.add(cleanYear)
                            }

                            // Genre
                            if (!currentItem.genre.isNullOrEmpty()) {
                                parts.add(currentItem.genre!!)
                            }

                            // Seasons & Episodes count if series
                            if (currentItem.type == "series") {
                                val seasons = currentRealSeasonsCount ?: currentItem.getSeriesSeasonsCount()
                                val episodes = currentRealEpisodesCount ?: currentItem.getSeriesEpisodesCount()
                                parts.add(if (seasons == 1) "1 Temporada" else "$seasons Temporadas")
                                parts.add(if (episodes == 1) "1 Episódio" else "$episodes Episódios")
                            }

                            // Classification Badge (e.g. TV-PG or 14+)
                            val ageBadge = currentItem.getClassificationBadge()
                            if (ageBadge.isNotEmpty()) {
                                parts.add(ageBadge)
                            }

                            // Rating
                            parts.add("★ ${currentItem.rating ?: "8.5"}")

                            parts
                        }

                        if (metadataParts.isNotEmpty()) {
                            Text(
                                text = metadataParts.joinToString("  •  "),
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        offset = Offset(1f, 1f),
                                        blurRadius = 3f
                                    )
                                ),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Description Synopsis
                        Text(
                            text = currentItem.description ?: "Assista ao vivo aos canais do seu servidor StreaTV IPTV, com reprodução instantânea e guias de programação integrados.",
                            style = TextStyle(
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            ),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 4. Aviso de Lançamento / Nova Temporada LOGO ABAIXO da descrição (Sinopse)
                        if (currentItem.type == "series" && currentItem.hasNewSeasonRelease()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "N O V A   T E M P O R A D A   D I S P O N Í V E L",
                                style = TextStyle(
                                    color = Color(0xFFE50914), // Netflix Red
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 3.sp, // alto tracking (espaçamento entre letras)
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        offset = Offset(1f, 1f),
                                        blurRadius = 3f
                                    )
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // Server Logo in the Top Right Corner (Watermark)
            StreaTVLogo(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                size = 28.dp,
                showText = true,
                animated = false
            )

            // Manual Navigation overlays (left/right icons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrev,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = if (isBannerFocused) 0.6f else 0.3f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = if (isBannerFocused) 0.3f else 0.1f),
                            shape = CircleShape
                        )
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Anterior", modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = onNext,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = if (isBannerFocused) 0.6f else 0.3f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = if (isBannerFocused) 0.3f else 0.1f),
                            shape = CircleShape
                        )
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Próximo", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// Favorite Section Layout
@Composable
fun FavoriteRowSection(
    viewModel: IPTVViewModel,
    onPlay: (PlaylistItem) -> Unit
) {
    val favorites by viewModel.getFavoritesFlow("movie").collectAsState(initial = emptyList())
    if (favorites.isNotEmpty()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Meus Favoritos",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val clickedIndex = remember(favorites, viewModel.lastClickedItemId) {
                favorites.indexOfFirst { it.id == viewModel.lastClickedItemId }
            }
            val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
            val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

            LazyRow(state = listState) {
                items(favorites) { item ->
                    MovieCard(item = item, onClick = { onPlay(item) })
                }
            }
        }
    }
}

// Live Channels Highlight (Canais ao vivo)
@Composable
fun LiveChannelsHighlights(
    channels: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit
) {
    if (channels.isNotEmpty()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Canais ao Vivo em Destaque",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow {
                items(channels.take(10)) { channel ->
                    LiveChannelCard(channel = channel, onClick = { onPlay(channel) })
                }
            }
        }
    }
}

// Popular Movies Highlights
@Composable
fun PopularMoviesHighlights(
    movies: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit
) {
    if (movies.isNotEmpty()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Filmes Recomendados para Você",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow {
                items(movies.take(10)) { movie ->
                    MovieCard(item = movie, onClick = { onPlay(movie) })
                }
            }
        }
    }
}

// Netflix-Style Search Screen
@Composable
fun NetflixSearchScreen(
    movies: List<PlaylistItem>,
    series: List<PlaylistItem>,
    channels: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    lastClickedItemId: Int? = null
) {
    val viewModel = LocalIPTVViewModel.current
    var selectedCategory by remember { mutableStateOf<String?>(viewModel?.selectedSearchCategory) }
    val categoryFocusRequesters = remember { List(8) { FocusRequester() } }

    LaunchedEffect(selectedCategory) {
        if (viewModel != null) {
            viewModel.selectedSearchCategory = selectedCategory
        }
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = viewModel?.searchGridScrollIndex ?: 0,
        initialFirstVisibleItemScrollOffset = viewModel?.searchGridScrollOffset ?: 0
    )

    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        if (viewModel != null) {
            viewModel.searchGridScrollIndex = gridState.firstVisibleItemIndex
            viewModel.searchGridScrollOffset = gridState.firstVisibleItemScrollOffset
        }
    }

    var isInitial by remember { mutableStateOf(true) }
    LaunchedEffect(searchQuery, selectedCategory) {
        if (viewModel?.lastClickedItemId != null) {
            return@LaunchedEffect
        }
        if (isInitial) {
            isInitial = false
        } else {
            try {
                gridState.scrollToItem(0)
            } catch (e: Exception) {}
        }
    }

    // Combine results and filter based on both text query and/or selected category
    val filteredItems = remember(movies, series, searchQuery, selectedCategory) {
        var list = (movies + series)
            .distinctBy { it.id }
            .filter { it.type != "series_episode" }

        if (selectedCategory != null) {
            list = list.filter { item ->
                val genreLower = (item.genre ?: "").lowercase()
                val groupLower = item.groupName.lowercase()
                val titleLower = item.title.lowercase()
                when (selectedCategory) {
                    "Comedies" -> "comé" in genreLower || "comedy" in genreLower || "comé" in groupLower || "comedy" in groupLower || "comé" in titleLower
                    "Action" -> "ação" in genreLower || "action" in genreLower || "ação" in groupLower || "action" in groupLower || "ação" in titleLower
                    "Sci-Fi" -> "ficção" in genreLower || "sci" in genreLower || "ficção" in groupLower || "sci" in groupLower || "ficção" in titleLower
                    "Horror" -> "terror" in genreLower || "horror" in genreLower || "terror" in groupLower || "horror" in groupLower || "terror" in titleLower
                    "Documentaries" -> "doc" in genreLower || "documentário" in genreLower || "doc" in groupLower || "documentário" in groupLower || "doc" in titleLower
                    "Anime" -> "anime" in genreLower || "animação" in genreLower || "anime" in groupLower || "animação" in groupLower || "anime" in titleLower
                    "Romance" -> "romance" in genreLower || "romance" in groupLower || "romance" in titleLower
                    else -> true
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.genre?.contains(searchQuery, ignoreCase = true) == true ||
                        it.groupName.contains(searchQuery, ignoreCase = true)
            }
        }

        list
    }

    val keyboardRows = listOf(
        listOf("␣", "⌫"),
        listOf("a", "b", "c", "d", "e", "f"),
        listOf("g", "h", "i", "j", "k", "l"),
        listOf("m", "n", "o", "p", "q", "r"),
        listOf("s", "t", "u", "v", "w", "x"),
        listOf("y", "z", "1", "2", "3", "4"),
        listOf("5", "6", "7", "8", "9", "0")
    )

    val categoriesList = listOf(
        "Comedies",
        "Action",
        "Sci-Fi",
        "Horror",
        "Documentaries",
        "Anime",
        "Romance"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left Column (Keyboard and Categories)
        val categoriesFocusRequester = remember { FocusRequester() }
        val keyboardBottomRowFocusRequester = remember { FocusRequester() }

        Column(
            modifier = Modifier
                .weight(0.3f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search text field display (Slightly smaller height: 36.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Busca",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (searchQuery.isEmpty()) "Buscar..." else searchQuery,
                        color = if (searchQuery.isEmpty()) Color.Gray else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpar",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                }
            }

            // Virtual Keyboard (Tighter sizing)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                keyboardRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        row.forEach { key ->
                            val isSpecialKey = key == "␣" || key == "⌫"
                            val weight = if (isSpecialKey) 3f else 1f
                            var isKeyFocused by remember { mutableStateOf(false) }

                            val keyModifier = Modifier
                                .weight(weight)
                                .height(26.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isKeyFocused) Color.White.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.08f)
                                )
                                .border(
                                    width = if (isKeyFocused) 1.5.dp else 0.dp,
                                    color = if (isKeyFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    if (key == "␣") {
                                        onSearchQueryChange(searchQuery + " ")
                                    } else if (key == "⌫") {
                                        if (searchQuery.isNotEmpty()) {
                                            onSearchQueryChange(searchQuery.dropLast(1))
                                        }
                                    } else {
                                        onSearchQueryChange(searchQuery + key)
                                    }
                                    // Clear category when manually typing
                                    selectedCategory = null
                                }
                                .onFocusChanged { isKeyFocused = it.isFocused }
                                .then(
                                    if (key == "5") Modifier.focusRequester(keyboardBottomRowFocusRequester) else Modifier
                                )

                            // If this is the bottom row of the keyboard, handle Key.DirectionDown to focus the categories list below
                            val isBottomRow = row == keyboardRows.last()
                            val finalModifier = if (isBottomRow) {
                                keyModifier.onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                        try {
                                            categoryFocusRequesters[0].requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    } else false
                                }
                            } else {
                                keyModifier
                            }

                            Box(
                                modifier = finalModifier.focusable(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "␣") {
                                    Text(
                                        text = "Espaço",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (key == "⌫") {
                                    Text(
                                        text = "Apagar",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Categories list (Styled as Vertical Scrollable Column)
            Text(
                text = "Categorias Populares",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Todas as Categorias" option in the vertical list
                val isAllSelected = selectedCategory == null
                var isAllFocused by remember { mutableStateOf(false) }
                val targetAllScale = if (isAllFocused) 1.04f else 1.00f
                val allScale by animateFloatAsState(
                    targetValue = targetAllScale,
                    animationSpec = tween(150, easing = LinearOutSlowInEasing),
                    label = "allCategoryScale"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .graphicsLayer {
                            scaleX = allScale
                            scaleY = allScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clickable { selectedCategory = null }
                        .onFocusChanged { isAllFocused = it.isFocused }
                        .focusRequester(categoryFocusRequesters[0])
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        try {
                                            keyboardBottomRowFocusRequester.requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        try {
                                            categoryFocusRequesters[1].requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .focusable()
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Todas as Categorias",
                        color = if (isAllFocused) Color.White else if (isAllSelected) Color(0xFFE50914) else Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                categoriesList.forEachIndexed { listIdx, categoryName ->
                    val categoryIndex = listIdx + 1
                    val isSelected = selectedCategory == categoryName
                    var isCatFocused by remember { mutableStateOf(false) }
                    val targetScale = if (isCatFocused) 1.04f else 1.00f
                    val catScale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = tween(150, easing = LinearOutSlowInEasing),
                        label = "categoryScale"
                    )
                    val label = when (categoryName) {
                        "Comedies" -> "Comédias"
                        "Action" -> "Ação"
                        "Sci-Fi" -> "Sci-Fi / Ficção"
                        "Horror" -> "Terror / Horror"
                        "Documentaries" -> "Documentários"
                        "Anime" -> "Anime / Animação"
                        "Romance" -> "Romance"
                        else -> categoryName
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .graphicsLayer {
                                scaleX = catScale
                                scaleY = catScale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .clickable { selectedCategory = categoryName }
                            .onFocusChanged { isCatFocused = it.isFocused }
                            .focusRequester(categoryFocusRequesters[categoryIndex])
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            try {
                                                categoryFocusRequesters[categoryIndex - 1].requestFocus()
                                                true
                                            } catch (e: Exception) {
                                                false
                                            }
                                        }
                                        Key.DirectionDown -> {
                                            if (categoryIndex < categoryFocusRequesters.lastIndex) {
                                                try {
                                                    categoryFocusRequesters[categoryIndex + 1].requestFocus()
                                                    true
                                                } catch (e: Exception) {
                                                    false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = label,
                            color = if (isCatFocused) Color.White else if (isSelected) Color(0xFFE50914) else Color.LightGray.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Right Column (Results) - Direct LazyVerticalGrid spanning full remaining space
        val titleHeader = when {
            selectedCategory != null -> {
                when (selectedCategory) {
                    "Comedies" -> "Recomendações: Comédias"
                    "Action" -> "Recomendações: Ação"
                    "Sci-Fi" -> "Recomendações: Sci-Fi / Ficção"
                    "Horror" -> "Recomendações: Terror / Horror"
                    "Documentaries" -> "Recomendações: Documentários"
                    "Anime" -> "Recomendações: Anime"
                    "Romance" -> "Recomendações: Romance"
                    else -> "Recomendações: $selectedCategory"
                }
            }
            searchQuery.isNotBlank() -> "Resultados para \"$searchQuery\""
            else -> "Your Search Recommendations"
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight()
        ) {
            // Title Header as a Spanning Item so it scrolls naturally and doesn't restrict grid height
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = titleHeader,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (filteredItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Vazio",
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Nenhum filme ou série encontrado.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    SearchPosterCard(
                        item = item,
                        onClick = { onPlay(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                    )
                }
            }
        }
    }
}

// Vertical Poster Card for Netflix Search Results Layout
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchPosterCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150, easing = LinearOutSlowInEasing),
        label = "searchPosterScale"
    )

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == item.id) {
            for (i in 1..20) {
                if (isFocused) {
                    viewModel.lastClickedItemId = null
                    break
                }
                try {
                    localFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore and retry
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    LaunchedEffect(item.id) {
        if (viewModel != null && item.type != "live") {
            viewModel.enrichItemOnDemand(item)
        }
    }

    Box(
        modifier = modifier
            .zIndex(if (isFocused) 10f else 1f)
            .focusRequester(localFocusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1F1F1F))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            var isLoadFailed by remember(item.id) { mutableStateOf(false) }
            val posterUrl = item.getEffectiveLogoUrl()?.takeIf { it.isNotEmpty() }
                ?: item.logoUrl?.takeIf { it.isNotEmpty() }
                ?: item.logo?.takeIf { it.isNotEmpty() }
                ?: item.backdropUrl?.takeIf { it.isNotEmpty() }
                ?: item.titleLogoUrl?.takeIf { it.isNotEmpty() }

            if (!posterUrl.isNullOrEmpty() && !isLoadFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(posterUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Error) {
                            isLoadFailed = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF2C2C2C), Color(0xFF141414))
                            )
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Show a small premium badge in the top corner if it is a series!
            if (item.type == "series") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE50914)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.copy(
                            lineHeight = 14.sp,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
                    )
                }
            }
        }
    }
}

// Category Row Component (Standard)
@Composable
fun CategoryRow(
    title: String,
    items: List<PlaylistItem>,
    isSelectedHighlight: Boolean,
    onPlay: (PlaylistItem) -> Unit,
    onItemFocused: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    onItemRendered: (PlaylistItem) -> Unit = {},
    showTitle: Boolean = true
) {
    // Add dynamic alpha scale to non-focused categories to focus beautifully on the highlighted one!
    val rowAlpha by animateFloatAsState(if (isSelectedHighlight) 1f else 0.4f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .padding(vertical = 8.dp)
    ) {
        if (showTitle) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    color = if (isSelectedHighlight) Color.White else Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
                if (isSelectedHighlight) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFFE50914), CircleShape)
                    )
                }
            }
        }

        val viewModel = LocalIPTVViewModel.current
        val clickedIndex = remember(items, viewModel?.lastClickedItemId) {
            items.indexOfFirst { it.id == viewModel?.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        LazyRow(state = listState) {
            itemsIndexed(items) { index, item ->
                MovieCard(
                    item = item,
                    onClick = { onPlay(item) },
                    onFocused = { onItemFocused(item) },
                    onItemRendered = onItemRendered,
                    modifier = if (index == 0 && firstItemFocusRequester != null) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

fun formatCleanCategoryTitle(categoryType: String, title: String): String {
    var cleaned = title.trim()
    val prefixes = listOf(
        "$categoryType |", "$categoryType -", "$categoryType /", "$categoryType :",
        "Filmes |", "Filmes -", "Filmes /", "Filmes :",
        "Series |", "Series -", "Series /", "Series :",
        "Séries |", "Séries -", "Séries /", "Séries :"
    )
    for (prefix in prefixes) {
        if (cleaned.startsWith(prefix, ignoreCase = true)) {
            cleaned = cleaned.substring(prefix.length).trim()
        }
    }
    if (cleaned.isEmpty() || cleaned.equals(categoryType, ignoreCase = true)) {
        return categoryType
    }
    return "$categoryType | $cleaned"
}

fun getItemAmbientColor(item: PlaylistItem?): Color {
    if (item == null) return Color(0xFF0F121A)
    val title = item.title.lowercase()
    return when {
        title.contains("dia d") || title.contains("yellow") || title.contains("ouro") || title.contains("gold") -> Color(0xFFC09000)
        title.contains("stranger") || title.contains("red") || title.contains("vermelho") || title.contains("batman") -> Color(0xFF8B0000)
        title.contains("avatar") || title.contains("blue") || title.contains("azul") || title.contains("sea") -> Color(0xFF1E3F66)
        title.contains("matrix") || title.contains("green") || title.contains("verde") || title.contains("hulk") -> Color(0xFF1B4D3E)
        title.contains("barbie") || title.contains("pink") || title.contains("rosa") -> Color(0xFFC2185B)
        title.contains("dune") || title.contains("duna") || title.contains("sand") || title.contains("orange") -> Color(0xFFB85E00)
        title.contains("heartstopper") || title.contains("love") || title.contains("purple") || title.contains("roxo") -> Color(0xFF6A1B9A)
        else -> {
            val hash = item.id.hashCode() xor (item.title.hashCode() * 31)
            val hue = (kotlin.math.abs(hash) % 360).toFloat()
            Color.hsv(hue = hue, saturation = 0.70f, value = 0.55f)
        }
    }
}

// Movies & Series Row Component with 2 Widescreen Cards per Page and Synopsis underneath
@Composable
fun MoviesSeriesCategoryRow(
    categoryType: String,
    title: String,
    items: List<PlaylistItem>,
    isSelectedHighlight: Boolean,
    onPlay: (PlaylistItem) -> Unit,
    onItemFocused: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    onItemRendered: (PlaylistItem) -> Unit = {}
) {
    val viewModel = LocalIPTVViewModel.current
    val rowAlpha by animateFloatAsState(if (isSelectedHighlight) 1f else 0.5f, label = "rowAlpha")
    val coroutineScope = rememberCoroutineScope()

    var currentFocusedItem by remember { mutableStateOf<PlaylistItem?>(items.firstOrNull()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .padding(vertical = 8.dp)
    ) {
        // Category Title Header with clean, non-duplicated title and symmetric margin
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 28.dp, bottom = 12.dp)
        ) {
            Text(
                text = formatCleanCategoryTitle(categoryType, title),
                fontSize = 20.sp,
                color = if (isSelectedHighlight) Color.White else Color.LightGray,
                fontWeight = FontWeight.Bold
            )
            if (isSelectedHighlight) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFE50914), CircleShape)
                )
            }
        }

        // 2-Card Row Layout with symmetric horizontal padding (28.dp start & end)
        val clickedIndex = remember(items, viewModel?.lastClickedItemId) {
            items.indexOfFirst { it.id == viewModel?.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalMargin = 28.dp
            val spacingBetweenCards = 18.dp
            val availableWidth = maxWidth - (horizontalMargin * 2) - spacingBetweenCards
            val cardWidth = availableWidth / 2
            val cardHeight = (cardWidth * (9f / 16f)).coerceIn(180.dp, 260.dp)

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = horizontalMargin),
                horizontalArrangement = Arrangement.spacedBy(spacingBetweenCards),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(items) { index, item ->
                    MovieSpotlightCard(
                        categoryType = categoryType,
                        item = item,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        onClick = { onPlay(item) },
                        onFocused = {
                            currentFocusedItem = item
                            onItemFocused(item)
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                        onItemRendered = onItemRendered,
                        modifier = if (index == 0 && firstItemFocusRequester != null) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Metadata & Synopsis Section directly below the 2 cards
        val focused = currentFocusedItem
        if (focused != null) {
            val isSerie = categoryType == "Series" || categoryType == "Séries" || focused.type == "series"
            val typeStr = if (isSerie) "Série" else "Filme"
            val genreStr = focused.genre?.takeIf { it.isNotBlank() } ?: "Entretenimento"
            val yearStr = focused.year?.takeIf { it.isNotBlank() } ?: "2025"
            val ratingStr = focused.rating?.takeIf { it.isNotBlank() } ?: "8.5"

            val seasonsCount = focused.numSeasons ?: 1
            val episodesCount = focused.numEpisodes ?: 12

            val metadataText = if (isSerie) {
                "$typeStr • $genreStr • $yearStr • $seasonsCount Temporadas • $episodesCount Episódios • ★ $ratingStr"
            } else {
                "$typeStr • $genreStr • $yearStr • ★ $ratingStr"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            ) {
                // Line 1: Metadata chips / dots
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = metadataText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                // Line 2: Synopsis / Description
                val rawDescription = focused.description?.takeIf { it.isNotBlank() && it.length > 5 }
                    ?: "Acompanhe esta incrível produção repleta de momentos inesquecíveis, grandes reviravoltas e muita emoção."

                Text(
                    text = rawDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth(0.95f)
                )
            }
        }
    }
}

// Netflix-Style Brand Badge (Floating logo lockup without solid background)
@Composable
fun NetflixStyleBrandBadge(
    categoryType: String,
    itemType: String?,
    modifier: Modifier = Modifier
) {
    val isSerie = categoryType == "Series" || categoryType == "Séries" || itemType == "series"
    val redColor = Color(0xFFE50914)
    val initial = if (isSerie) "S" else "F"
    val label = if (isSerie) "S E R I E S" else "F I L M E S"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = initial,
            style = TextStyle(
                color = redColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.9f),
                    offset = Offset(1f, 2f),
                    blurRadius = 6f
                )
            )
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.9f),
                    offset = Offset(1f, 2f),
                    blurRadius = 6f
                )
            )
        )
    }
}

// Spotlight Widescreen Card with Focus Ring & Badges
@Composable
fun MovieSpotlightCard(
    categoryType: String,
    item: PlaylistItem,
    cardWidth: Dp,
    cardHeight: Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    onItemRendered: (PlaylistItem) -> Unit = {}
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }

    var isFocused by remember { mutableStateOf(false) }
    val isFocusedState = remember { derivedStateOf { isFocused } }

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == item.id) {
            localFocusRequester.requestFocus()
            viewModel.lastClickedItemId = null
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    LaunchedEffect(item.id) {
        onItemRendered(item)
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocusedState.value) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "spotlightCardScale"
    )

    Box(
        modifier = modifier
            .focusRequester(localFocusRequester)
            .width(cardWidth)
            .height(cardHeight)
            .padding(4.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .border(
                width = if (isFocusedState.value) 3.dp else 1.dp,
                color = if (isFocusedState.value) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val effectiveBackdrop = item.getEffectiveBackdropUrl()?.takeIf { it.isNotEmpty() }
                    ?: item.backdropUrl?.takeIf { it.isNotEmpty() }
                    ?: item.logoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.logo?.takeIf { it.isNotEmpty() }
                    ?: item.getEffectiveLogoUrl()

                if (!effectiveBackdrop.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val cardRequest = remember(effectiveBackdrop) {
                        ImageRequest.Builder(context)
                            .data(effectiveBackdrop)
                            .crossfade(true)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(false)
                            .build()
                    }
                    AsyncImage(
                        model = cardRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Top left badge: Netflix-style floating brand logo
                NetflixStyleBrandBadge(
                    categoryType = categoryType,
                    itemType = item.type,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )

                // Title or Title Logo at bottom (clean layout)
                val titleLogo = item.titleLogoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.getEffectiveTitleLogoUrl()

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    if (!titleLogo.isNullOrEmpty()) {
                        val context = LocalContext.current
                        val logoRequest = remember(titleLogo) {
                            ImageRequest.Builder(context)
                                .data(titleLogo)
                                .crossfade(true)
                                .bitmapConfig(Bitmap.Config.ARGB_8888)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(false)
                                .build()
                        }
                        AsyncImage(
                            model = logoRequest,
                            contentDescription = item.title,
                            modifier = Modifier
                                .width(150.dp)
                                .height(42.dp)
                        )
                    } else {
                        Text(
                            text = item.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// Live Channel Card (EPG Integration) with Focus Zoom/Jump Effect
@Composable
fun LiveChannelCard(
    channel: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }

    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == channel.id) {
            localFocusRequester.requestFocus()
            viewModel.lastClickedItemId = null
        }
    }

    // Dynamically retrieve context EPG schedules!
    val programs = remember(channel.title) { EPGGenerator.generateEPGForChannel(channel.title) }
    val currentProgram = programs.getOrNull(1) // Pick current show

    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Card(
        modifier = modifier
            .focusRequester(localFocusRequester)
            .width(180.dp)
            .padding(6.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.small
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212)
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color.Black)
            ) {
                if (!channel.logoUrl.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val channelRequest = remember(channel.logoUrl) {
                        ImageRequest.Builder(context)
                            .data(channel.logoUrl)
                            .crossfade(true)
                            .size(width = 360, height = 180) // Proportional safe size for LiveChannelCard
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = channelRequest,
                        contentDescription = channel.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = channel.title,
                        tint = Color.DarkGray,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                }

                // Live Badge Overlays
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Red, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            // EPG Details
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = channel.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Show current EPG title
                currentProgram?.let { prog ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Agora: ${prog.title}",
                        fontSize = 9.sp,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Program Timeline progress bar
                    LinearProgressIndicator(
                        progress = { prog.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color(0xFFE50914),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

// VOD Card (Movie or Series) with Horizontal (Deitado) Layout, containing backdrop poster and overlaid title logo
@Composable
fun MovieCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    onItemRendered: (PlaylistItem) -> Unit = {}
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }

    var isFocused by remember { mutableStateOf(false) }
    val isFocusedState = remember { derivedStateOf { isFocused } }

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == item.id) {
            localFocusRequester.requestFocus()
            viewModel.lastClickedItemId = null
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    LaunchedEffect(item.id) {
        onItemRendered(item)
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocusedState.value) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "movieCardScale"
    )

    Box(
        modifier = modifier
            .focusRequester(localFocusRequester)
            .width(156.dp)
            .height(88.dp)
            .padding(4.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .border(
                width = if (isFocusedState.value) 2.dp else 1.dp,
                color = if (isFocusedState.value) Color.White else Color.White.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.small
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = MaterialTheme.shapes.small
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val effectiveBackdrop = item.getEffectiveBackdropUrl()?.takeIf { it.isNotEmpty() }
                    ?: item.backdropUrl?.takeIf { it.isNotEmpty() }
                    ?: item.logoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.logo?.takeIf { it.isNotEmpty() }
                    ?: item.getEffectiveLogoUrl()

                if (!effectiveBackdrop.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val cardRequest = remember(effectiveBackdrop) {
                        ImageRequest.Builder(context)
                            .data(effectiveBackdrop)
                            .crossfade(true)
                            .size(Size.ORIGINAL) // Set to Size.ORIGINAL for highest rendering fidelity
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(false) // Disable hardware bitmaps to prevent TV rendering/transparency failures
                            .build()
                    }
                    AsyncImage(
                        model = cardRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Dark elegant scrim overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Overlaid Title Logo or Title Text
                val titleLogo = item.titleLogoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.getEffectiveTitleLogoUrl()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    if (!titleLogo.isNullOrEmpty()) {
                        val context = LocalContext.current
                        val logoRequest = remember(titleLogo) {
                            ImageRequest.Builder(context)
                                .data(titleLogo)
                                .crossfade(true)
                                .size(width = 156, height = 52)
                                .bitmapConfig(Bitmap.Config.ARGB_8888)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(false)
                                .build()
                        }
                        AsyncImage(
                            model = logoRequest,
                            contentDescription = item.title,
                            modifier = Modifier
                                .width(90.dp)
                                .height(26.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column {
                            Text(
                                text = item.cleanTitle ?: item.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.groupName.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.groupName,
                                    color = Color.LightGray.copy(alpha = 0.7f),
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Continue Watching Section Layout
@Composable
fun ContinueWatchingRowSection(
    viewModel: IPTVViewModel,
    onPlay: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    val continueWatching by viewModel.continueWatching.collectAsState(initial = emptyList())
    if (continueWatching.isNotEmpty()) {
        Column {
            Text(
                text = "Continue Assistindo",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            val clickedIndex = remember(continueWatching, viewModel.lastClickedItemId) {
                continueWatching.indexOfFirst { it.id == viewModel.lastClickedItemId }
            }
            val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
            val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(continueWatching) { index, item ->
                    val cardFocusRequester = if (index == 0 && firstItemFocusRequester != null) {
                        firstItemFocusRequester
                    } else {
                        remember { FocusRequester() }
                    }
                    ContinueWatchingCard(
                        item = item,
                        viewModel = viewModel,
                        onClick = { onPlay(item) },
                        focusRequester = cardFocusRequester
                    )
                }
            }
        }
    }
}

// Continue Watching Card with Horizontal (Deitado) Layout, containing a Vertical Poster on the left, logo/details on the right, and remaining progress time.
@Composable
fun ContinueWatchingCard(
    item: PlaylistItem,
    viewModel: IPTVViewModel,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    val isFocusedState = remember { derivedStateOf { isFocused } }
    var progressFraction by remember { mutableStateOf(0f) }
    var elapsedText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (viewModel.lastClickedItemId == item.id) {
            focusRequester.requestFocus()
            viewModel.lastClickedItemId = null
        }
    }

    LaunchedEffect(item.id) {
        val history = viewModel.getWatchProgress(item.id)
        if (history != null && history.duration > 0) {
            progressFraction = history.position.toFloat() / history.duration.toFloat()
            val secs = history.position / 1000
            val mins = secs / 60
            val hours = mins / 60
            elapsedText = if (hours > 0) {
                "${hours}h ${mins % 60}m"
            } else {
                String.format("%02d:%02d", mins, secs % 60)
            }
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocusedState.value) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "continueWatchingCardScale"
    )

    Card(
        modifier = Modifier
            .focusRequester(focusRequester)
            .width(260.dp)
            .height(135.dp)
            .padding(4.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .border(
                width = if (isFocusedState.value) 2.5.dp else 1.dp,
                color = if (isFocusedState.value) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Left portion: Vertical Poster
                Box(
                    modifier = Modifier
                        .width(85.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                ) {
                    val effectiveLogo = item.logoUrl?.takeIf { it.isNotEmpty() }
                        ?: item.logo?.takeIf { it.isNotEmpty() }
                        ?: item.getEffectiveLogoUrl()

                    if (!effectiveLogo.isNullOrEmpty()) {
                        val context = LocalContext.current
                        val posterRequest = remember(effectiveLogo) {
                            ImageRequest.Builder(context)
                                .data(effectiveLogo)
                                .crossfade(true)
                                .size(Size.ORIGINAL)
                                .bitmapConfig(Bitmap.Config.ARGB_8888)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(false)
                                .build()
                        }
                        AsyncImage(
                            model = posterRequest,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF222222)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Right portion: Details, Logo, and Pause timestamp
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        val titleLogo = item.titleLogoUrl?.takeIf { it.isNotEmpty() }
                            ?: item.getEffectiveTitleLogoUrl()

                        if (!titleLogo.isNullOrEmpty()) {
                            val context = LocalContext.current
                            val logoRequest = remember(titleLogo) {
                                ImageRequest.Builder(context)
                                    .data(titleLogo)
                                    .crossfade(true)
                                    .size(width = 160, height = 50)
                                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            AsyncImage(
                                model = logoRequest,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = item.cleanTitle ?: item.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.groupName,
                            color = Color.Gray,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (elapsedText.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFFE50914),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Parou em: $elapsedText",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Red Progress Bar along the bottom of the card
            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFFE50914),
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}

// ==========================================
// TMDB.ORG STYLE COMPONENT IMPLEMENTATIONS
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TMDBWelcomeSection(
    onSearch: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isButtonFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF032541), Color(0xFF01b4e4))
                )
            )
            .padding(28.dp)
    ) {
        Column {
            Text(
                text = "Bem-Vindo(a).",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Text(
                text = "Milhões de Filmes, Séries e Pessoas para Descobrir. Explore já.",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                fontSize = 16.sp
            )

            // Search input field with search button inside
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .onFocusChanged { isSearchFocused = it.isFocused },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.Black,
                        fontSize = 14.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar por um Filme, Série ou Pessoa...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )

                // Search button
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF1de9b6), Color(0xFF00b0ff))
                            )
                        )
                        .clickable { onSearch(searchQuery) }
                        .onFocusChanged { isButtonFocused = it.isFocused }
                        .border(
                            width = if (isButtonFocused) 2.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Buscar",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrendingSection(
    trendingItems: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    var selectedPeriod by remember { mutableStateOf("Hoje") }
    val hojeFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header Row with Title + Pill Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "Tendências",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(16.dp))

            // Toggle pill selector with dynamic remote control focus
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .border(1.dp, Color(0xFF032541), CircleShape)
                    .background(Color(0xFF051d2f), CircleShape)
                    .padding(2.dp)
                    .onFocusChanged {
                        if (it.isFocused) {
                            hojeFocusRequester.requestFocus()
                        }
                    }
                    .focusable(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hoje pill
                var isHojeFocused by remember { mutableStateOf(false) }
                val isHoje = selectedPeriod == "Hoje"
                Box(
                    modifier = Modifier
                        .focusRequester(hojeFocusRequester)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            brush = if (isHoje) Brush.horizontalGradient(listOf(Color(0xFFC0FECF), Color(0xFF1ED5A9)))
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                            shape = CircleShape
                        )
                        .background(
                            color = if (!isHoje && isHojeFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                            shape = CircleShape
                        )
                        .border(
                            width = if (isHojeFocused) 1.dp else 0.dp,
                            color = if (isHojeFocused) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .onFocusChanged {
                            isHojeFocused = it.isFocused
                            if (it.isFocused) {
                                selectedPeriod = "Hoje"
                            }
                        }
                        .focusable()
                        .clickable { selectedPeriod = "Hoje" }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Hoje",
                        color = if (isHoje) Color(0xFF032541) else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Nesta Semana pill
                var isSemanaFocused by remember { mutableStateOf(false) }
                val isSemana = selectedPeriod == "Nesta Semana"
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            brush = if (isSemana) Brush.horizontalGradient(listOf(Color(0xFFC0FECF), Color(0xFF1ED5A9)))
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                            shape = CircleShape
                        )
                        .background(
                            color = if (!isSemana && isSemanaFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                            shape = CircleShape
                        )
                        .border(
                            width = if (isSemanaFocused) 1.dp else 0.dp,
                            color = if (isSemanaFocused) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .onFocusChanged {
                            isSemanaFocused = it.isFocused
                            if (it.isFocused) {
                                selectedPeriod = "Nesta Semana"
                            }
                        }
                        .focusable()
                        .clickable { selectedPeriod = "Nesta Semana" }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nesta Semana",
                        color = if (isSemana) Color(0xFF032541) else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val viewModel = LocalIPTVViewModel.current
        val clickedIndex = remember(trendingItems, viewModel?.lastClickedItemId) {
            trendingItems.indexOfFirst { it.id == viewModel?.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        // LazyRow of movie posters
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sort or filter slightly based on period selection for realism!
            val sortedItems = if (selectedPeriod == "Hoje") trendingItems else trendingItems.reversed()
            itemsIndexed(sortedItems) { index, item ->
                TMDBPosterCard(
                    item = item,
                    onClick = { onPlay(item) },
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstItemFocusRequester != null) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        try {
                                            hojeFocusRequester.requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (downFocusRequester != null) {
                                            try {
                                                downFocusRequester.requestFocus()
                                                true
                                            } catch (e: Exception) {
                                                false
                                            }
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LatestTrailersSection(
    trailerItems: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    var selectedTrailerTab by remember { mutableStateOf("Popular") }
    val tabs = listOf("Popular", "Streaming", "Na TV", "Para Alugar", "Nos Cinemas")
    val popularTabFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header
        Column(
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Últimos Trailers",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Toggle pill selectors row with dynamic remote control focus
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused) {
                            popularTabFocusRequester.requestFocus()
                        }
                    }
                    .focusable()
            ) {
                items(tabs) { tab ->
                    val isSelected = selectedTrailerTab == tab
                    var isTabFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .run {
                                if (tab == "Popular") focusRequester(popularTabFocusRequester) else this
                            }
                            .height(26.dp)
                            .clip(CircleShape)
                            .background(
                                brush = if (isSelected) Brush.horizontalGradient(listOf(Color(0xFFC0FECF), Color(0xFF1ED5A9)))
                                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                shape = CircleShape
                            )
                            .background(
                                color = if (!isSelected) {
                                    if (isTabFocused) Color.White.copy(alpha = 0.15f) else Color(0xFF0F172A)
                                } else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = if (isTabFocused) Color.White else if (isSelected) Color.Transparent else Color(0xFF032541),
                                shape = CircleShape
                            )
                            .onFocusChanged {
                                isTabFocused = it.isFocused
                                if (it.isFocused) {
                                    selectedTrailerTab = tab
                                }
                            }
                            .focusable()
                            .clickable { selectedTrailerTab = tab }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color(0xFF032541) else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val viewModel = LocalIPTVViewModel.current
        val clickedIndex = remember(trailerItems, viewModel?.lastClickedItemId) {
            trailerItems.indexOfFirst { it.id == viewModel?.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        // LazyRow of trailers
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val itemsToRender = when (selectedTrailerTab) {
                "Popular" -> trailerItems
                "Streaming" -> trailerItems.shuffled()
                else -> trailerItems.reversed()
            }
            itemsIndexed(itemsToRender) { index, item ->
                TMDBTrailerCard(
                    item = item,
                    onClick = { onPlay(item) },
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstItemFocusRequester != null) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        try {
                                            popularTabFocusRequester.requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (downFocusRequester != null) {
                                            try {
                                                downFocusRequester.requestFocus()
                                                true
                                            } catch (e: Exception) {
                                                false
                                            }
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }
        }
    }
}

@Composable
fun TMDBTrailerCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }

    var isFocused by remember { mutableStateOf(false) }
    val isFocusedState = remember { derivedStateOf { isFocused } }

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == item.id) {
            localFocusRequester.requestFocus()
            viewModel.lastClickedItemId = null
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocusedState.value) 1.03f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "trailerCardScale"
    )

    Column(
        modifier = modifier
            .focusRequester(localFocusRequester)
            .width(220.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .border(
                    width = if (isFocusedState.value) 2.dp else 1.dp,
                    color = if (isFocusedState.value) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val effectiveBackdrop = item.getEffectiveBackdropUrl()
                if (!effectiveBackdrop.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val trailerRequest = remember(effectiveBackdrop) {
                        ImageRequest.Builder(context)
                            .data(effectiveBackdrop)
                            .crossfade(true)
                            .size(width = 440, height = 248) // Proportional safe size for trailer card
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = trailerRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF032541), Color(0xFF1A1A1A))
                                )
                            )
                    )
                }

                // Centered Semi-Translucent Play Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.75f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF032541),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val titleLogo = item.getEffectiveTitleLogoUrl()
        if (!titleLogo.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AsyncImage(
                    model = titleLogo,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Text(
                text = item.cleanTitle ?: item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = item.description ?: "Trailer Oficial",
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MostPopularSection(
    popularItems: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    var selectedPopularTab by remember { mutableStateOf("Streaming") }
    val tabs = listOf("Streaming", "Na TV", "Para Alugar", "Nos Cinemas")
    val popularSectionTabFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "Os Mais Populares",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(16.dp))

            // Toggle pill selector with dynamic remote control focus
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .border(1.dp, Color(0xFF032541), CircleShape)
                    .background(Color(0xFF051d2f), CircleShape)
                    .padding(2.dp)
                    .onFocusChanged {
                        if (it.isFocused) {
                            popularSectionTabFocusRequester.requestFocus()
                        }
                    }
                    .focusable(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedPopularTab == tab
                    var isTabFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .run {
                                if (tab == "Streaming") focusRequester(popularSectionTabFocusRequester) else this
                            }
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                brush = if (isSelected) Brush.horizontalGradient(listOf(Color(0xFFC0FECF), Color(0xFF1ED5A9)))
                                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                shape = CircleShape
                            )
                            .background(
                                color = if (!isSelected && isTabFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(
                                width = if (isTabFocused) 1.dp else 0.dp,
                                color = if (isTabFocused) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .onFocusChanged {
                                isTabFocused = it.isFocused
                                if (it.isFocused) {
                                    selectedPopularTab = tab
                                }
                            }
                            .focusable()
                            .clickable { selectedPopularTab = tab }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color(0xFF032541) else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val viewModel = LocalIPTVViewModel.current
        val clickedIndex = remember(popularItems, viewModel?.lastClickedItemId) {
            popularItems.indexOfFirst { it.id == viewModel?.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        // LazyRow of movie posters
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val itemsToRender = when (selectedPopularTab) {
                "Streaming" -> popularItems
                "Na TV" -> popularItems.shuffled()
                else -> popularItems.reversed()
            }
            itemsIndexed(itemsToRender) { index, item ->
                TMDBPosterCard(
                    item = item,
                    onClick = { onPlay(item) },
                    modifier = if (index == 0 && firstItemFocusRequester != null) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TMDBPosterCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }

    var isFocused by remember { mutableStateOf(false) }
    val isFocusedState = remember { derivedStateOf { isFocused } }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val localDensity = androidx.compose.ui.platform.LocalDensity.current
    var cardSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    LaunchedEffect(isFocused, cardSize) {
        if (isFocused && cardSize.height > 0) {
            try {
                val extraPadding = with(localDensity) { 24.dp.toPx() }
                bringIntoViewRequester.bringIntoView(
                    androidx.compose.ui.geometry.Rect(
                        left = -extraPadding,
                        top = 0f,
                        right = cardSize.width.toFloat() + extraPadding,
                        bottom = cardSize.height.toFloat() + extraPadding
                    )
                )
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == item.id) {
            localFocusRequester.requestFocus()
            viewModel.lastClickedItemId = null
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocusedState.value) 1.03f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "posterCardScale"
    )

    Column(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onSizeChanged { cardSize = it }
            .focusRequester(localFocusRequester)
            .width(115.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .border(
                    width = if (isFocusedState.value) 2.dp else 1.dp,
                    color = if (isFocusedState.value) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val effectiveLogo = item.logoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.logo?.takeIf { it.isNotEmpty() }
                    ?: item.titleLogoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.getEffectiveLogoUrl()

                if (!effectiveLogo.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val posterRequest = remember(effectiveLogo) {
                        ImageRequest.Builder(context)
                            .data(effectiveLogo)
                            .crossfade(true)
                            .size(Size.ORIGINAL) // Set to Size.ORIGINAL for highest rendering fidelity
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(false) // Disable hardware bitmaps to prevent TV rendering/transparency failures
                            .build()
                    }
                    AsyncImage(
                        model = posterRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF032541), Color(0xFF1A1A1A))
                                )
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val titleLogo = item.getEffectiveTitleLogoUrl()
                        if (!titleLogo.isNullOrEmpty()) {
                            val context = LocalContext.current
                            val logoRequestSmall = remember(titleLogo) {
                                ImageRequest.Builder(context)
                                    .data(titleLogo)
                                    .crossfade(true)
                                    .size(width = 300, height = 100) // Downscale to prevent glGetError
                                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            AsyncImage(
                                model = logoRequestSmall,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = item.cleanTitle ?: item.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val titleLogo = item.getEffectiveTitleLogoUrl()
        if (!titleLogo.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val context = LocalContext.current
                val logoRequestTiny = remember(titleLogo) {
                    ImageRequest.Builder(context)
                        .data(titleLogo)
                        .crossfade(true)
                        .size(width = 240, height = 48) // Downscale to prevent glGetError
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = logoRequestTiny,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Text(
                text = item.cleanTitle ?: item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = item.year ?: "9 de julho de 2026",
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

// ==========================================
// TMDB.ORG STATIC FALLBACK DATA
// ==========================================

val defaultTrending = listOf(
    PlaylistItem(id = 9901, title = "Obsessão", logoUrl = "https://image.tmdb.org/t/p/w500/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg", year = "14 de maio de 2026", description = "Uma presença obsessiva e aterrorizante que ameaça a sanidade de uma jovem herdeira.", rating = "8.2", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9902, title = "Uma Casa na Pradaria", logoUrl = "https://image.tmdb.org/t/p/w500/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/fBieUo3SdItUrXZE16YxbpjwXIe.jpg", year = "9 de julho de 2026", description = "Uma família busca recomeçar a vida em uma pacata região rural, mas forças misteriosas começam a afetar a plantação.", rating = "7.9", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9903, title = "The Furious", logoUrl = "https://image.tmdb.org/t/p/w500/k334z7zxOJlJvOsOwyRF6HClCvi.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/1AVF2fAevpfi2HP6AEpptG1kg8R.jpg", year = "10 de junho de 2026", description = "Um piloto de fuga lendário é forçado a voltar à ativa para salvar seu irmão de uma gangue.", rating = "8.7", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9904, title = "Moana", logoUrl = "https://image.tmdb.org/t/p/w500/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/c6BPbkO5Npt1OdwttAxCFo06wtH.jpg", year = "9 de julho de 2026", description = "Uma jovem navegadora embarca em uma ousada missão marítima para salvar seu povo.", rating = "9.0", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9915, title = "Silo", logoUrl = "https://image.tmdb.org/t/p/w500/u7vS96H96oI4UIs3pLI85gSIs4u.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/q7776Z9Yg58P6N8v7HkWK6fOunY.jpg", year = "4 de maio de 2023", description = "Em um futuro degradado e tóxico, uma comunidade existe em um gigantesco silo subterrâneo que se estende por centenas de andares abaixo da terra.", rating = "8.3", groupName = "Tendências", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9905, title = "A Morte do Demônio: Em Chamas", logoUrl = "https://image.tmdb.org/t/p/w500/fteLdvfRnltfLjAEnsl5E3vImnW.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg", year = "7 de julho de 2026", description = "O despertar de uma força demoníaca ancestral liberta espíritos malignos.", rating = "8.5", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9916, title = "Trunfo", logoUrl = "https://image.tmdb.org/t/p/w500/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg", year = "10 de julho de 2026", description = "Um drama de alta espionagem e intriga política onde um agente secreto joga seu último trunfo para desmascarar uma conspiração global.", rating = "8.0", groupName = "Tendências", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9906, title = "A Odisseia", logoUrl = "https://image.tmdb.org/t/p/w500/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/m3Pom6pbD51bBv3syz8NMHda3fz.jpg", year = "16 de julho de 2026", description = "Uma jornada épica inspirada no clássico grego.", rating = "8.9", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9907, title = "Sem Nada a Perder", logoUrl = "https://image.tmdb.org/t/p/w500/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/6tByIjKn3VLEVHhfpiNNWrnSIOM.jpg", year = "8 de julho de 2026", description = "Dois detetives obstinados entram em um jogo perigoso de gato e rato.", rating = "8.1", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9908, title = "Backrooms", logoUrl = "https://image.tmdb.org/t/p/w500/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/mCpwRayjXMFzKHbjbzc5JRKfq1O.jpg", year = "28 de maio de 2026", description = "Um labirinto infinito de salas amarelas vazias habitadas por uma entidade misteriosa.", rating = "8.3", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php")
)

val defaultTrailers = listOf(
    PlaylistItem(id = 9911, title = "O Fim da Rua", logoUrl = "https://image.tmdb.org/t/p/w500/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.jpg", year = "Trailer Oficial Dublado", description = "Trailer Oficial Dublado", rating = "7.8", groupName = "Últimos Trailers", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9912, title = "A Morte do Demônio: Em Chamas", logoUrl = "https://image.tmdb.org/t/p/w500/fteLdvfRnltfLjAEnsl5E3vImnW.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg", year = "Teaser de Anúncio da Data", description = "Teaser de Anúncio da Data", rating = "8.2", groupName = "Últimos Trailers", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9913, title = "Hope", logoUrl = "https://image.tmdb.org/t/p/w500/7WOBeoaXUNGzbm79Umio2CcH941.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/9zgnuwIK6JAc6giVsscq9H82li1.jpg", year = "Official US Trailer [Subtitled]", description = "Official US Trailer [Subtitled]", rating = "7.5", groupName = "Últimos Trailers", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9914, title = "Jackass: Best and Last", logoUrl = "https://image.tmdb.org/t/p/w500/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/zqd39GO0GdO5TaO8HxnkmWwu2p8.jpg", year = "Big Red Rocket", description = "Big Red Rocket", rating = "8.0", groupName = "Últimos Trailers", type = "movie", url = "http://streatv.elementfx.com/get.php")
)

val defaultPopular = listOf(
    PlaylistItem(id = 9921, title = "A Casa do Dragão", logoUrl = "https://image.tmdb.org/t/p/w500/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg", year = "21 de agosto de 2022", description = "A história da Casa Targaryen, ambientada 200 anos antes dos eventos de Game of Thrones.", rating = "9.1", groupName = "Os Mais Populares", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9922, title = "NCIS: Investigação Naval", logoUrl = "https://image.tmdb.org/t/p/w500/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/nn3SuLTO4hum8yAxaY4ql8h6kRk.jpg", year = "14 de maio de 2026", description = "Casos criminais envolvendo pessoal da Marinha americana.", rating = "8.4", groupName = "Os Mais Populares", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9923, title = "Na Zona Cinzenta", logoUrl = "https://image.tmdb.org/t/p/w500/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/qcIKxhqGMIj8uujsSoSMZWr8QqU.jpg", year = "14 de maio de 2026", description = "Agentes especiais operam nas fronteiras da legalidade internacional.", rating = "8.0", groupName = "Os Mais Populares", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9924, title = "O Limite do Prazer", logoUrl = "https://image.tmdb.org/t/p/w500/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/rHnANzYUmV3WZw3n0yWOLiR3pen.jpg", year = "4 de setembro de 2025", description = "Um romance tórrido e perigoso entre um escritor intrigado e uma misteriosa milionária.", rating = "7.8", groupName = "Os Mais Populares", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9925, title = "Obsessão", logoUrl = "https://image.tmdb.org/t/p/w500/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg", year = "14 de maio de 2026", description = "Presença obsessiva e aterrorizante que ameaça a sanidade de uma jovem herdeira.", rating = "8.2", groupName = "Os Mais Populares", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9926, title = "Lei & Ordem", logoUrl = "https://image.tmdb.org/t/p/w500/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/tc7canPSAn2X14hYi6Rl3gZm1o4.jpg", year = "13 de setembro de 1990", description = "Casos criminais sob a ótica dos detetives policiais.", rating = "8.8", groupName = "Os Mais Populares", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9927, title = "Rancho Dutton", logoUrl = "https://image.tmdb.org/t/p/w500/aJVroxZZKReiSlASpODQ23qrdGa.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/wh5agdl3b7fqC7mSjsCkHx4fMAs.jpg", year = "15 de outubro de 2026", description = "A luta diária de uma família proprietária de terras em Montana.", rating = "8.9", groupName = "Os Mais Populares", type = "series", url = "http://streatv.elementfx.com/get.php")
)

fun PlaylistItem.getSeriesSeasonsCount(): Int {
    return when {
        title.contains("Yellowstone", ignoreCase = true) || title.contains("Rancho", ignoreCase = true) -> 5
        title.contains("Grey", ignoreCase = true) || title.contains("Cinzenta", ignoreCase = true) -> 20
        title.contains("NCIS", ignoreCase = true) -> 21
        title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> 23
        title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> 2
        else -> 3
    }
}

fun PlaylistItem.getSeriesEpisodesCount(): Int {
    return when {
        title.contains("Yellowstone", ignoreCase = true) || title.contains("Rancho", ignoreCase = true) -> 47
        title.contains("Grey", ignoreCase = true) || title.contains("Cinzenta", ignoreCase = true) -> 430
        title.contains("NCIS", ignoreCase = true) -> 460
        title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> 540
        title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> 20
        else -> 30
    }
}

fun PlaylistItem.hasNewSeasonRelease(): Boolean {
    return title.contains("Yellowstone", ignoreCase = true) ||
            title.contains("Rancho", ignoreCase = true) ||
            title.contains("Grey", ignoreCase = true) ||
            title.contains("Cinzenta", ignoreCase = true) ||
            title.contains("NCIS", ignoreCase = true) ||
            title.contains("Dragão", ignoreCase = true) ||
            title.contains("Dragon", ignoreCase = true)
}

fun PlaylistItem.getEffectiveTitleLogoUrl(): String? {
    if (!titleLogoUrl.isNullOrEmpty()) return titleLogoUrl
    if (!logo.isNullOrEmpty()) return logo

    return when {
        title.contains("Passageiro do Mal", ignoreCase = true) || title.contains("Passenger", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/2wdMrwQHQ28q69IMvqmc3SUAL8W.png"
        title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/iJLKPeerez9GGa9kwtYsu6rsSla.png"
        title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rpNau5FSkTtAVCVORJpqUmSXAHJ.png"
        title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/msFWTNrFwfjsHKnMxDUexR9uofh.png"
        title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/megWWuC8tSODx90i0gP0fVM1wVo.png"
        title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/4jzFpQg7D0AB5GQq3lp8k4HTYxE.png"
        title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/jpLOFTHacWoxCTCLRePVlLHxrbo.png"
        title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/3pzH42BKQ0z30fM9DPwSewvWYLS.png"
        title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/oHP5aXQ4gg15t9EsBhgKENeCKdG.png"
        title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/8VDfGY16827yyWH0R8itxLtfgK1.png"
        title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/iEYvlw4yqSp9Ck6Iz90V31QSD7D.png"
        title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/zdzXhbQgjOQzrQ6Yq5kWepYw7MJ.png"
        title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9w0aK6Vs1YqSp13EhrlZVQ4TJ1u.png"
        title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/gAwgHNn9bgNefFEzv2sY6Q2BRVo.png"
        title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/vIprMXDcEruwpW4aL5PMTQrkljb.png"
        title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/lrK7KGhdytsZGAggPU2N6fPMv4y.png"
        title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rj1CuaIo5BYr9XxlLztZMnWP3YT.png"
        title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wQ7FFixqLXD2aOqtoiawyz7IbVx.png"
        else -> null
    }
}

fun PlaylistItem.getEffectiveLogoUrl(): String? {
    if (title.contains("Ardente Vingança", ignoreCase = true)) {
        return "https://image.tmdb.org/t/p/w500/9qrWHR8GUJOO95jHeG0jDTTF1m7.jpg"
    }
    val url = logoUrl
    if (url.isNullOrEmpty()) {
        return when {
            title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg"
            title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg"
            title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/k334z7zxOJlJvOsOwyRF6HClCvi.jpg"
            title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg"
            title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/fteLdvfRnltfLjAEnsl5E3vImnW.jpg"
            title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg"
            title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg"
            title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg"
            title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg"
            title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/7WOBeoaXUNGzbm79Umio2CcH941.jpg"
            title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg"
            title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg"
            title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg"
            title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg"
            title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg"
            title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg"
            title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/aJVroxZZKReiSlASpODQ23qrdGa.jpg"
            else -> null
        }
    }

    val isBroken = url.contains("7v9vqu972mQQCHVgrmvD") ||
            url.contains("49W6xr693Y4Y9569668") ||
            url.contains("czembWB60b7Lg6uC6g6gD") ||
            url.contains("or06gK6Fx9gX6E8UuHIn9fR88Z6") ||
            url.contains("nBNZ9tPhv9vqu972mQQCHVgrmvD")

    if (isBroken) {
        return when {
            title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg"
            title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg"
            title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/k334z7zxOJlJvOsOwyRF6HClCvi.jpg"
            title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg"
            title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/fteLdvfRnltfLjAEnsl5E3vImnW.jpg"
            title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg"
            title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg"
            title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg"
            title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg"
            title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/7WOBeoaXUNGzbm79Umio2CcH941.jpg"
            title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg"
            title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg"
            title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg"
            title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg"
            title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg"
            title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg"
            title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/w500/aJVroxZZKReiSlASpODQ23qrdGa.jpg"
            else -> url
        }
    }
    return url
}

fun PlaylistItem.getEffectiveBackdropUrl(): String? {
    val url = backdropUrl
    if (url.isNullOrEmpty()) {
        return when {
            title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg"
            title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fBieUo3SdItUrXZE16YxbpjwXIe.jpg"
            title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/1AVF2fAevpfi2HP6AEpptG1kg8R.jpg"
            title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/c6BPbkO5Npt1OdwttAxCFo06wtH.jpg"
            title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg"
            title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/m3Pom6pbD51bBv3syz8NMHda3fz.jpg"
            title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/6tByIjKn3VLEVHhfpiNNWrnSIOM.jpg"
            title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mCpwRayjXMFzKHbjbzc5JRKfq1O.jpg"
            title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.jpg"
            title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9zgnuwIK6JAc6giVsscq9H82li1.jpg"
            title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/zqd39GO0GdO5TaO8HxnkmWwu2p8.jpg"
            title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg"
            title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/nn3SuLTO4hum8yAxaY4ql8h6kRk.jpg"
            title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/qcIKxhqGMIj8uujsSoSMZWr8QqU.jpg"
            title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rHnANzYUmV3WZw3n0yWOLiR3pen.jpg"
            title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/tc7canPSAn2X14hYi6Rl3gZm1o4.jpg"
            title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wh5agdl3b7fqC7mSjsCkHx4fMAs.jpg"
            else -> null
        }
    }

    val isBroken = url.contains("9gp6Y08oIe9NaV7uXvAtOTo2yG0") ||
            url.contains("56vDgc1Uv9D9CHv4gN4EXS6fXmB") ||
            url.contains("lzWH0v0N7vIFvSBu7z0ST0as76K") ||
            url.contains("7Ry78r46S7L3I5C4N6O9X8Y7Z6W") ||
            url.contains("rAiWbyqUz9Z967S2g9E5QDQDMD0")

    if (isBroken) {
        return when {
            title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg"
            title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fBieUo3SdItUrXZE16YxbpjwXIe.jpg"
            title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/1AVF2fAevpfi2HP6AEpptG1kg8R.jpg"
            title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/c6BPbkO5Npt1OdwttAxCFo06wtH.jpg"
            title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg"
            title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/m3Pom6pbD51bBv3syz8NMHda3fz.jpg"
            title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/6tByIjKn3VLEVHhfpiNNWrnSIOM.jpg"
            title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mCpwRayjXMFzKHbjbzc5JRKfq1O.jpg"
            title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.jpg"
            title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9zgnuwIK6JAc6giVsscq9H82li1.jpg"
            title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/zqd39GO0GdO5TaO8HxnkmWwu2p8.jpg"
            title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg"
            title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/nn3SuLTO4hum8yAxaY4ql8h6kRk.jpg"
            title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/qcIKxhqGMIj8uujsSoSMZWr8QqU.jpg"
            title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rHnANzYUmV3WZw3n0yWOLiR3pen.jpg"
            title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/tc7canPSAn2X14hYi6Rl3gZm1o4.jpg"
            title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wh5agdl3b7fqC7mSjsCkHx4fMAs.jpg"
            else -> url
        }
    }
    return url
}

fun PlaylistItem.getBestBackgroundImageUrl(): String? {
    return getEffectiveBackdropUrl()?.takeIf { it.isNotBlank() }
        ?: backdropUrl?.takeIf { it.isNotBlank() }
        ?: logoUrl?.takeIf { it.isNotBlank() }
        ?: logo?.takeIf { it.isNotBlank() }
        ?: getEffectiveLogoUrl()?.takeIf { it.isNotBlank() }
}

/**
 * Custom Modifier for Android TV to handle D-pad focus, scaling, borders,
 * and elevation transitions smoothly.
 */
fun Modifier.tvCardFocus(
    scale: Float = 1.03f,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    onFocusChange: (Boolean) -> Unit = {}
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) scale else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "tvCardFocusScale"
    )

    val animatedBorderColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f)
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val elevation = if (isFocused) 8.dp else 0.dp

    this
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .shadow(elevation = elevation, shape = shape)
        .border(width = borderWidth, color = animatedBorderColor, shape = shape)
        .onFocusChanged {
            isFocused = it.isFocused
            onFocusChange(it.isFocused)
        }
        .focusable(interactionSource = interactionSource)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChannelsDualPaneLayout(
    categories: List<String>,
    groupedChannels: Map<String, List<PlaylistItem>>,
    onPlay: (PlaylistItem) -> Unit,
    viewModel: IPTVViewModel,
    focusedCategoryIndex: Int,
    onCategoryChanged: (Int) -> Unit,
    contentFocusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, bottom = 16.dp)
    ) {
        // Left Column: Categories list (Width: 260.dp)
        val categoriesListState = rememberLazyListState(initialFirstVisibleItemIndex = if (focusedCategoryIndex in categories.indices) focusedCategoryIndex else 0)

        LaunchedEffect(focusedCategoryIndex) {
            if (categories.isNotEmpty() && focusedCategoryIndex in categories.indices) {
                categoriesListState.animateScrollToItem(focusedCategoryIndex)
            }
        }

        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Categorias",
                fontSize = 15.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
            )

            LazyColumn(
                state = categoriesListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 6.dp, end = 6.dp)
            ) {
                itemsIndexed(categories) { idx, category ->
                    CategoryListItem(
                        title = category,
                        isSelected = (idx == focusedCategoryIndex),
                        onFocused = {
                            onCategoryChanged(idx)
                        },
                        focusRequester = if (idx == focusedCategoryIndex) contentFocusRequester else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Vertical divider
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        // Right Column: Channels Grid
        val activeCategory = categories.getOrNull(focusedCategoryIndex) ?: ""
        val channelsInActiveCategory = remember(groupedChannels, activeCategory) {
            groupedChannels[activeCategory] ?: emptyList()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 20.dp)
        ) {
            if (activeCategory.isNotEmpty()) {
                Text(
                    text = "$activeCategory • ${channelsInActiveCategory.size} Canais",
                    fontSize = 15.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (channelsInActiveCategory.isNotEmpty()) {
                    val gridState = rememberLazyGridState()

                    val clickedIndex = remember(channelsInActiveCategory, viewModel.lastClickedItemId) {
                        channelsInActiveCategory.indexOfFirst { it.id == viewModel.lastClickedItemId }
                    }
                    LaunchedEffect(clickedIndex) {
                        if (clickedIndex >= 0) {
                            gridState.scrollToItem(clickedIndex)
                        }
                    }

                    LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 140.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp, start = 8.dp, end = 8.dp)
                    ) {
                        items(channelsInActiveCategory, key = { it.id }) { channel ->
                            ChannelGridCard(
                                channel = channel,
                                onClick = { onPlay(channel) },
                                viewModel = viewModel,
                                onBackToCategories = {
                                    try {
                                        contentFocusRequester.requestFocus()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
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
                            text = "Nenhum canal nesta categoria.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Selecione uma categoria para listar os canais.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryListItem(
    title: String,
    isSelected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "categoryItemScale"
    )

    val backgroundColor = when {
        isFocused -> Color(0xFFE50914).copy(alpha = 0.2f)
        isSelected -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused -> Color.White
        isSelected -> Color.White
        else -> Color.LightGray
    }

    Surface(
        modifier = modifier
            .zIndex(if (isFocused) 2f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onFocused() },
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = if (isFocused) BorderStroke(1.5.dp, Color(0xFFE50914)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFFE50914), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChannelGridCard(
    channel: PlaylistItem,
    onClick: () -> Unit,
    viewModel: IPTVViewModel,
    onBackToCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel.lastClickedItemId == channel.id) {
            focusRequester.requestFocus()
            viewModel.lastClickedItemId = null
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "channelCardScale"
    )

    Column(
        modifier = modifier
            .width(150.dp)
            .zIndex(if (isFocused) 2f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Back || keyEvent.key == Key.Escape)) {
                    onBackToCategories()
                    true
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = BorderStroke(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val effectiveLogo = channel.getEffectiveLogoUrl()?.takeIf { it.isNotEmpty() }
                    ?: channel.logoUrl?.takeIf { it.isNotEmpty() }
                    ?: channel.logo?.takeIf { it.isNotEmpty() }

                if (!effectiveLogo.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val logoRequest = remember(effectiveLogo) {
                        ImageRequest.Builder(context)
                            .data(effectiveLogo)
                            .crossfade(true)
                            .size(Size.ORIGINAL)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(false)
                            .build()
                    }
                    AsyncImage(
                        model = logoRequest,
                        contentDescription = channel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Channel Icon",
                            tint = Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = channel.cleanTitle ?: channel.title,
            color = if (isFocused) Color.White else Color.LightGray,
            fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )
    }
}

