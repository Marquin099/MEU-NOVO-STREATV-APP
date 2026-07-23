package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PlaylistItem
import com.example.data.model.UserProfile
import com.example.data.model.CastMember
import com.example.data.model.Episode
import com.example.data.model.Season
import com.example.data.repository.IPTVRepository
import com.example.data.util.cleanSeriesName
import com.example.data.util.groupSeriesList
import com.example.data.util.isForbiddenTitle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed class Screen {
    object Splash : Screen()
    object Login : Screen()
    object ProfileSelection : Screen()
    object Main : Screen()
    data class Player(val item: PlaylistItem) : Screen()
    data class Detail(val item: PlaylistItem, val autoPlay: Boolean = false) : Screen()
}

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String, val progress: Float) : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

class IPTVViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IPTVRepository(application)
    private val attemptedEnrichments = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    // Screen State Machine
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Sync / Login State
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Profiles Flow
    val profiles: StateFlow<List<UserProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProfile = MutableStateFlow<UserProfile?>(null)
    val selectedProfile: StateFlow<UserProfile?> = _selectedProfile.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueWatching: StateFlow<List<PlaylistItem>> = _selectedProfile
        .flatMapLatest { profile ->
            if (profile != null) {
                repository.getContinueWatching(profile.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Navigation Tab: "Inicio", "Canais", "Filmes", "Series"
    private val _selectedTab = MutableStateFlow("Inicio")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    // Playlist Content Flows (Filtered dynamically)
    val allItems: StateFlow<List<PlaylistItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search Query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Search Mode Active State
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
    }

    // TMDB Banner Slideshow State
    private val _bannerIndex = MutableStateFlow(0)
    val bannerIndex: StateFlow<Int> = _bannerIndex.asStateFlow()

    private var bannerJob: Job? = null

    // TMDB Curated Banner List (Populates dynamically from Tendências category)
    val bannerItems: StateFlow<List<PlaylistItem>> = repository.bannerItems
        .map { list ->
            val uniqueList = list.distinctBy { it.title.trim().lowercase() }
            uniqueList.forEach { item ->
                if (item.backdropUrl.isNullOrEmpty() || (item.titleLogoUrl.isNullOrEmpty() && item.logo.isNullOrEmpty())) {
                    enrichItemOnDemand(item)
                }
            }
            uniqueList
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Helper to detect live TV channels or live category groups
    private fun isLiveCategory(group: String): Boolean {
        val upper = group.uppercase()
        return upper.contains("CANAIS") ||
                upper.contains("LIVE") ||
                upper.contains("TV ") ||
                upper.contains(" TV") ||
                upper.contains("TELEVISÃO") ||
                upper.contains("TELEVISAO") ||
                upper.contains("EPG") ||
                upper.contains("NOTICIAS") ||
                upper.contains("ESPORTES") ||
                upper.contains("AO VIVO") ||
                upper.contains("NEWS") ||
                upper.contains("SPORTS") ||
                upper.contains("RADIOS") ||
                upper.contains("RÁDIOS") ||
                upper.contains("24H") ||
                upper.contains("24/7") ||
                upper.contains("TELECINE") ||
                upper.contains("HBO") ||
                upper.contains("GLOBOPLAY") ||
                upper.contains("NETFLIX") ||
                upper.contains("24 HORAS") ||
                upper.contains("CAMPEONATO") ||
                upper.contains("FUTEBOL") ||
                upper.contains("PREMIERE") ||
                upper.contains("ESPORTE") ||
                upper.contains("BRASILEIRÃO") ||
                upper.contains("BRASILEIRAO") ||
                upper.contains("COPA")
    }

    // Live Channels Flow
    val channels: StateFlow<List<PlaylistItem>> = repository.getItemsByType("live")
        .map { list ->
            list.filter { item ->
                val urlLower = item.url.lowercase().trim()
                val isVodExtension = urlLower.contains(".mp4") ||
                        urlLower.contains(".mkv") ||
                        urlLower.contains(".avi") ||
                        urlLower.contains(".mov") ||
                        urlLower.contains(".wmv") ||
                        urlLower.contains(".mpg") ||
                        urlLower.contains(".mpeg") ||
                        urlLower.contains(".m4v") ||
                        urlLower.contains(".webm") ||
                        urlLower.contains(".flv") ||
                        urlLower.contains(".f4v") ||
                        urlLower.contains(".mp3") ||
                        urlLower.contains("/movie/") ||
                        urlLower.contains("/series/")

                !isVodExtension
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groupedChannels: StateFlow<Map<String, List<PlaylistItem>>> = channels
        .map { list -> list.groupBy { it.groupName } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Movies Flow (Only show provider's films by filtering out TMDB-specific group names and live channels/categories)
    val movies: StateFlow<List<PlaylistItem>> = repository.getItemsByType("movie")
        .map { list ->
            list.filter { item ->
                item.groupName !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers")
            }.sortedByDescending { it.id }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groupedMovies: StateFlow<Map<String, List<PlaylistItem>>> = movies
        .map { list -> list.groupBy { it.groupName } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Series Flow (Strictly query type = 'series', filter out ghost titles and groupName exclusions)
    val series: StateFlow<List<PlaylistItem>> = repository.getItemsByType("series")
        .map { list ->
            val grouped = groupSeriesList(list)
            grouped.filter { item ->
                item.type == "series" &&
                item.groupName !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers") &&
                !isForbiddenTitle(item.title, item.cleanTitle)
            }
                .distinctBy { it.groupName.trim().lowercase() + ":::" + (it.cleanTitle ?: it.title).trim().lowercase() }
                .sortedByDescending { it.id }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groupedSeries: StateFlow<Map<String, List<PlaylistItem>>> = series
        .map { list -> list.groupBy { it.groupName } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val filteredMovies: StateFlow<List<PlaylistItem>> = combine(movies, searchQuery) { list, query ->
        if (query.isBlank()) list else {
            list.filter { it.title.contains(query, ignoreCase = true) || it.genre?.contains(query, ignoreCase = true) == true }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSeries: StateFlow<List<PlaylistItem>> = combine(series, searchQuery) { list, query ->
        val valid = list.filter { item ->
            item.type == "series" && !isForbiddenTitle(item.title, item.cleanTitle)
        }
        if (query.isBlank()) valid else {
            valid.filter { it.title.contains(query, ignoreCase = true) || it.genre?.contains(query, ignoreCase = true) == true }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredChannels: StateFlow<List<PlaylistItem>> = combine(channels, searchQuery) { list, query ->
        if (query.isBlank()) list else {
            list.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // TMDB Sections for Home Screen (Inicio)
    val trendingItems: StateFlow<List<PlaylistItem>> = repository.trendingItemsFlow
        .map { list -> list.distinctBy { it.title.trim().lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val popularItems: StateFlow<List<PlaylistItem>> = repository.popularItemsFlow
        .map { list -> list.distinctBy { it.title.trim().lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trailerItems: StateFlow<List<PlaylistItem>> = repository.trailerItemsFlow
        .map { list -> list.distinctBy { it.title.trim().lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic Category headers based on current tab type
    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<String>> = _selectedTab
        .flatMapLatest { tab ->
            if (tab == "Series") {
                repository.getCategoriesByType("series").map { list ->
                    list.distinct().filter { category ->
                        category !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers")
                    }
                }
            } else {
                val type = when (tab) {
                    "Canais" -> "live"
                    "Filmes" -> "movie"
                    else -> ""
                }
                if (tab == "Canais") {
                    groupedChannels.map { map ->
                        map.keys.filter { it.isNotBlank() }.toList()
                    }
                } else if (type.isEmpty()) {
                    flowOf(listOf("Favoritos", "Recentes", "Destaques"))
                } else {
                    repository.getCategoriesByType(type).map { list ->
                        if (tab == "Filmes") {
                            list.filter { category ->
                                category !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers")
                            }
                        } else {
                            list
                        }
                    }
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Start background auto banner updates every 5 seconds
        startBannerTimer()

        // Guarantee TMDB curated VODs are populated and fetch fresh real-time TMDB content
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.populateFallbackVodsIfNeeded()
                repository.fetchRealtimeTMDBContent()
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Failed to populate and fetch TMDB content", e)
            }
        }

        // Trigger background TMDB validation/sync for curated sections on launch to get fresh logos
        viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(3000)
                repository.syncMainSectionsMetadata()
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Failed to sync main sections metadata", e)
            }
        }
    }

    fun syncNewContentOnStartup(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                if (repository.hasLoggedIn()) {
                    repository.syncNewItemsOnStartup()
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Erro na sincronização inicial", e)
            } finally {
                onComplete() // Executa o callback para liberar a tela mesmo com erro/sem internet
            }
        }
    }

    fun hasLoggedIn(): Boolean = repository.hasLoggedIn()

    // Banner Autoplay management
    fun startBannerTimer(customTotal: Int = 0) {
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                val totalBanners = if (customTotal > 0) customTotal else bannerItems.value.size
                if (totalBanners > 0) {
                    _bannerIndex.value = (_bannerIndex.value + 1) % totalBanners
                }
            }
        }
    }

    fun nextBanner(customTotal: Int = 0) {
        val totalBanners = if (customTotal > 0) customTotal else bannerItems.value.size
        if (totalBanners > 0) {
            _bannerIndex.value = (_bannerIndex.value + 1) % totalBanners
            startBannerTimer(totalBanners) // Reset rotation clock on manual click
        }
    }

    fun prevBanner(customTotal: Int = 0) {
        val totalBanners = if (customTotal > 0) customTotal else bannerItems.value.size
        if (totalBanners > 0) {
            _bannerIndex.value = if (_bannerIndex.value - 1 < 0) totalBanners - 1 else _bannerIndex.value - 1
            startBannerTimer(totalBanners) // Reset rotation clock on manual click
        }
    }

    fun getSavedServerUrl(): String {
        return repository.getSavedServerUrl()
    }

    fun getSavedCredentials(): Pair<String?, String?> {
        return repository.getSavedCredentials()
    }

    // Login logic
    fun performLogin(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Syncing("Conectando ao servidor...", 0.1f)
                val success = withContext(Dispatchers.IO) {
                    repository.loginAndDownloadPlaylist(serverUrl, username, password) { msg, progress ->
                        _syncState.value = SyncState.Syncing(msg, progress)
                    }
                }
                if (success) {
                    _syncState.value = SyncState.Success
                    _currentScreen.value = Screen.ProfileSelection
                } else {
                    _syncState.value = SyncState.Error("Falha na conexão. Verifique se a URL do servidor, o usuário e a senha estão corretos e se o servidor está ativo.")
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error in performLogin", e)
                _syncState.value = SyncState.Error("Erro inesperado: ${e.localizedMessage}")
            }
        }
    }

    // Profile handling
    fun selectProfile(profile: UserProfile) {
        _selectedProfile.value = profile
        _currentScreen.value = Screen.Main
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.startBackgroundMetadataPrefetch()
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Failed starting background metadata prefetch on profile selection", e)
            }
        }
    }

    fun addNewProfile(name: String, avatarIconName: String = "avatar_default") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Generate a beautiful, high-contrast random pastel background color for user avatars
                val colors = listOf("#E50914", "#1E3C72", "#1E824C", "#6C3483", "#D35400", "#16A085")
                val selectedColor = colors.random()
                repository.createProfile(name, selectedColor, avatarIconName)
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error in addNewProfile", e)
            }
        }
    }

    fun removeProfile(profile: UserProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteProfile(profile)
                if (_selectedProfile.value?.id == profile.id) {
                    _selectedProfile.value = null
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error in removeProfile", e)
            }
        }
    }

    // Favorites Handler
    fun toggleFavorite(itemId: Int) {
        val currentProf = _selectedProfile.value ?: return
        viewModelScope.launch {
            repository.toggleFavorite(currentProf.id, itemId)
        }
    }

    var focusedCategoryIndex: Int = 0

    fun setTab(tab: String) {
        if (_selectedTab.value != tab) {
            _selectedTab.value = tab
            focusedCategoryIndex = 0
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private var previousScreen: Screen? = null
    var mainScrollPosition: Int = 0
    var searchGridScrollIndex: Int = 0
    var searchGridScrollOffset: Int = 0
    var selectedSearchCategory: String? = null
    var lastClickedItemId: Int? = null
    var redirectDetailOnPlayerExit: PlaylistItem? = null

    fun navigateTo(screen: Screen) {
        val current = _currentScreen.value
        if (screen != Screen.Splash && screen != Screen.Login && screen != Screen.ProfileSelection && screen != current) {
            previousScreen = current
        }
        _currentScreen.value = screen
    }

    fun navigateBack() {
        val current = _currentScreen.value
        val redirectItem = redirectDetailOnPlayerExit
        if (current is Screen.Detail) {
            redirectDetailOnPlayerExit = null
            val prev = previousScreen
            if (prev != null && prev !is Screen.Detail) {
                _currentScreen.value = prev
                previousScreen = null
            } else {
                _currentScreen.value = Screen.Main
            }
        } else if (redirectItem != null) {
            redirectDetailOnPlayerExit = null
            previousScreen = Screen.Main
            _currentScreen.value = Screen.Detail(redirectItem)
        } else {
            val prev = previousScreen
            if (prev != null) {
                _currentScreen.value = prev
                previousScreen = null
            } else {
                _currentScreen.value = Screen.Main
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _selectedProfile.value = null
            _currentScreen.value = Screen.Login
            _syncState.value = SyncState.Idle
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            val creds = repository.getSavedCredentials()
            val serverUrl = repository.getSavedServerUrl()
            if (creds.first != null && creds.second != null) {
                performLogin(serverUrl, creds.first!!, creds.second!!)
            }
        }
    }

    suspend fun getItemById(id: Int): PlaylistItem? {
        return repository.getItemById(id)
    }

    suspend fun enrichItemWithTMDB(item: PlaylistItem): PlaylistItem {
        return repository.enrichItemWithTMDB(item)
    }

    // Async TMDB details loading when selecting/viewing details of any film/series
    fun enrichItemOnDemand(item: PlaylistItem) {
        if (!attemptedEnrichments.add(item.id)) {
            return
        }
        viewModelScope.launch {
            try {
                repository.enrichItemWithTMDB(item)
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Failed to enrich item ${item.title}", e)
            }
        }
    }

    fun saveWatchProgress(itemId: Int, position: Long, duration: Long) {
        val currentProf = _selectedProfile.value ?: return
        viewModelScope.launch {
            repository.saveWatchProgress(currentProf.id, itemId, position, duration)
        }
    }

    suspend fun getWatchProgress(itemId: Int): com.example.data.model.WatchHistory? {
        val currentProf = _selectedProfile.value ?: return null
        return repository.getWatchProgress(currentProf.id, itemId)
    }

    fun deleteWatchProgress(itemId: Int) {
        val currentProf = _selectedProfile.value ?: return
        viewModelScope.launch {
            repository.deleteWatchProgress(currentProf.id, itemId)
        }
    }

    fun saveLastWatchedEpisode(seriesId: Int, episodeTitle: String, playUrl: String) {
        val currentProf = _selectedProfile.value ?: return
        viewModelScope.launch {
            repository.saveLastWatchedEpisode(currentProf.id, seriesId, episodeTitle, playUrl)
        }
    }

    suspend fun getLastWatchedEpisode(seriesId: Int): Pair<String, String>? {
        val currentProf = _selectedProfile.value ?: return null
        return repository.getLastWatchedEpisode(currentProf.id, seriesId)
    }

    // Helper to fetch favorite items of active profile
    fun getFavoritesFlow(type: String): Flow<List<PlaylistItem>> {
        val currentProf = _selectedProfile.value ?: return flowOf(emptyList())
        return repository.getFavorites(currentProf.id, type)
            .flowOn(Dispatchers.IO)
    }

    // Get specific category items dynamically filtered and grouped for Series or Movies
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCategoryItems(type: String, categoryName: String): Flow<List<PlaylistItem>> {
        if (type == "series") {
            return repository.getItemsByTypeAndCategory("series", categoryName)
                .map { list ->
                    val grouped = groupSeriesList(list)
                    grouped.filter { item ->
                        item.type == "series" && !isForbiddenTitle(item.title, item.cleanTitle)
                    }
                        .distinctBy { (it.cleanTitle ?: it.title).trim().lowercase() }
                        .sortedByDescending { it.id }
                }
                .flowOn(Dispatchers.Default)
        } else if (type == "movie") {
            return repository.getItemsByTypeAndCategory(type, categoryName)
                .map { list -> list.distinctBy { it.title.trim().lowercase() }.sortedByDescending { it.id } }
                .flowOn(Dispatchers.IO)
        } else {
            return repository.getItemsByTypeAndCategory(type, categoryName)
                .map { list -> list.sortedBy { it.id } }
                .flowOn(Dispatchers.IO)
        }
    }

    suspend fun getRealCast(item: PlaylistItem): List<CastMember> {
        return repository.getRealCastFromTMDB(item)
    }

    suspend fun getRealSeasons(item: PlaylistItem): List<Season> {
        return repository.getRealSeasonsFromTMDB(item)
    }

    suspend fun findRealEpisodeItem(seriesTitle: String, seasonName: String, episodeIndex: Int, episodeTitle: String): PlaylistItem? {
        return repository.findRealEpisodeItem(seriesTitle, seasonName, episodeIndex, episodeTitle)
    }

    suspend fun verifyAndGetRealPlaylistItem(item: PlaylistItem): PlaylistItem? {
        return repository.verifyAndGetRealPlaylistItem(item)
    }
}
