package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.database.WatchHistoryDao
import com.example.data.model.FavoriteItem
import com.example.data.model.PlaylistItem
import com.example.data.model.UserProfile
import com.example.data.model.WatchHistory
import com.example.data.model.CastMember
import com.example.data.model.Episode
import com.example.data.model.Season
import com.example.data.util.M3UParser
import com.example.data.util.cleanSeriesName
import com.example.data.util.isForbiddenTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class IPTVRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val profileDao = db.userProfileDao()
    private val playlistDao = db.playlistItemDao()
    private val watchHistoryDao = db.watchHistoryDao()

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("streatv_preferences", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val tmdbSemaphore = Semaphore(3)

    // Profiles Flow
    val allProfiles: Flow<List<UserProfile>> = profileDao.getAllProfiles()

    // Playlist Items Flows
    val allItems: Flow<List<PlaylistItem>> = playlistDao.getAllItems()
    val bannerItems: Flow<List<PlaylistItem>> = playlistDao.getBannerItems()

    val trendingItemsFlow: Flow<List<PlaylistItem>> = playlistDao.getItemsByGroupName("Tendências")
    val popularItemsFlow: Flow<List<PlaylistItem>> = playlistDao.getItemsByGroupName("Os Mais Populares")
    val trailerItemsFlow: Flow<List<PlaylistItem>> = playlistDao.getItemsByGroupName("Últimos Trailers")

    suspend fun getItemsCount(): Int = playlistDao.getItemsCount()

    suspend fun getItemById(id: Int): PlaylistItem? = playlistDao.getItemById(id)

    fun getItemsByType(type: String): Flow<List<PlaylistItem>> =
        playlistDao.getItemsByType(type)

    fun getCategoriesByType(type: String): Flow<List<String>> =
        playlistDao.getCategoriesByType(type).map { list ->
            val orderKey = when (type) {
                "movie" -> "movie_categories_order"
                "series", "series_episode" -> "series_categories_order"
                else -> null
            }
            if (orderKey != null) {
                val jsonStr = sharedPrefs.getString(orderKey, null)
                if (!jsonStr.isNullOrEmpty()) {
                    try {
                        val orderList = mutableListOf<String>()
                        val jsonArray = JSONArray(jsonStr)
                        for (i in 0 until jsonArray.length()) {
                            orderList.add(jsonArray.getString(i))
                        }
                        val orderMap = orderList.mapIndexed { index, name -> name to index }.toMap()
                        list.sortedWith(
                            compareBy<String> { orderMap[it] ?: 999999 }
                                .thenBy { it }
                        )
                    } catch (e: Exception) {
                        Log.e("IPTVRepository", "Error parsing categories order JSON", e)
                        list
                    }
                } else {
                    list
                }
            } else {
                list
            }
        }

    fun getItemsByTypeAndCategory(type: String, category: String): Flow<List<PlaylistItem>> =
        playlistDao.getItemsByTypeAndCategory(type, category)

    fun getFavorites(profileId: Int, type: String): Flow<List<PlaylistItem>> =
        playlistDao.getFavoriteItems(profileId, type)

    fun getContinueWatching(profileId: Int): Flow<List<PlaylistItem>> =
        watchHistoryDao.getContinueWatching(profileId)

    suspend fun saveWatchProgress(profileId: Int, itemId: Int, position: Long, duration: Long) {
        val watch = WatchHistory(
            profileId = profileId,
            itemId = itemId,
            position = position,
            duration = duration,
            lastWatched = System.currentTimeMillis()
        )
        watchHistoryDao.insertWatchHistory(watch)
    }

    suspend fun getWatchProgress(profileId: Int, itemId: Int): WatchHistory? {
        return watchHistoryDao.getWatchHistoryItem(profileId, itemId)
    }

    suspend fun deleteWatchProgress(profileId: Int, itemId: Int) {
        watchHistoryDao.deleteWatchHistory(profileId, itemId)
    }

    suspend fun saveLastWatchedEpisode(profileId: Int, seriesId: Int, episodeTitle: String, playUrl: String) = withContext(Dispatchers.IO) {
        sharedPrefs.edit()
            .putString("last_ep_title_${profileId}_${seriesId}", episodeTitle)
            .putString("last_ep_url_${profileId}_${seriesId}", playUrl)
            .apply()
    }

    suspend fun getLastWatchedEpisode(profileId: Int, seriesId: Int): Pair<String, String>? = withContext(Dispatchers.IO) {
        val title = sharedPrefs.getString("last_ep_title_${profileId}_${seriesId}", null)
        val url = sharedPrefs.getString("last_ep_url_${profileId}_${seriesId}", null)
        if (title != null && url != null) {
            Pair(title, url)
        } else {
            null
        }
    }

    // User Credentials Handling
    fun saveCredentials(serverUrl: String, username: String, password: String) {
        sharedPrefs.edit()
            .putString("saved_server_url", serverUrl)
            .putString("saved_username", username)
            .putString("saved_password", password)
            .putBoolean("has_logged_in", true)
            .apply()
    }

    fun getSavedCredentials(): Pair<String?, String?> {
        val u = sharedPrefs.getString("saved_username", null)
        val p = sharedPrefs.getString("saved_password", null)
        return Pair(u, p)
    }

    private fun cleanServerUrl(url: String): String {
        var cleaned = url.trim()
        if (cleaned.isEmpty()) return ""
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
            cleaned = "http://$cleaned"
        }
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        val endingsToRemove = listOf(
            "/player_api.php", "/get.php", "/xmltv.php",
            "player_api.php", "get.php", "xmltv.php"
        )
        for (ending in endingsToRemove) {
            if (cleaned.endsWith(ending)) {
                cleaned = cleaned.substring(0, cleaned.length - ending.length)
            }
        }
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        return cleaned
    }

    fun getSavedServerUrl(): String {
        val raw = sharedPrefs.getString("saved_server_url", "http://streatv.elementfx.com") ?: "http://streatv.elementfx.com"
        return cleanServerUrl(raw)
    }

    fun hasLoggedIn(): Boolean {
        return sharedPrefs.getBoolean("has_logged_in", false)
    }

    suspend fun syncNewItemsOnStartup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val serverUrl = getSavedServerUrl()
            val creds = getSavedCredentials()
            val username = creds.first
            val password = creds.second
            if (serverUrl.isEmpty() || username.isNullOrBlank() || password.isNullOrBlank()) {
                return@withContext false
            }

            var formattedServer = serverUrl.trim()
            if (!formattedServer.startsWith("http://") && !formattedServer.startsWith("https://")) {
                formattedServer = "http://$formattedServer"
            }
            if (formattedServer.endsWith("/")) {
                formattedServer = formattedServer.substring(0, formattedServer.length - 1)
            }

            Log.d("IPTVRepository", "syncNewItemsOnStartup: Starting fast JSON-based sync on startup")

            val fetchedItems = mutableListOf<PlaylistItem>()

            // 1. Fetch Series Categories
            val seriesCategoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_series_categories"
            val seriesCategoryMap = mutableMapOf<String, String>()
            try {
                val req = Request.Builder().url(seriesCategoriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("[")) {
                            val jsonArray = JSONArray(bodyStr)
                            val seriesCategoryOrderList = mutableListOf<String>()
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catId = obj.optString("category_id")
                                val catName = obj.optString("category_name")
                                if (catId.isNotEmpty() && catName.isNotEmpty()) {
                                    seriesCategoryMap[catId] = catName
                                    seriesCategoryOrderList.add(catName)
                                }
                            }
                            if (seriesCategoryOrderList.isNotEmpty()) {
                                val json = JSONArray(seriesCategoryOrderList).toString()
                                sharedPrefs.edit().putString("series_categories_order", json).apply()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching series categories in fast sync", e)
            }

            // 2. Fetch Series
            val seriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_series"
            try {
                val req = Request.Builder().url(seriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("[")) {
                            val jsonArray = JSONArray(bodyStr)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name")
                                val seriesIdStr = obj.optString("series_id")
                                val seriesId = seriesIdStr.toIntOrNull() ?: 0
                                val finalId = if (seriesId > 0) seriesId + 10000000 else 0
                                val cover = obj.optString("cover")
                                val catId = obj.optString("category_id")
                                val plot = obj.optString("plot")
                                val rating = obj.optString("rating")
                                val releaseDate = obj.optString("releaseDate")
                                val genre = obj.optString("genre")

                                val groupName = seriesCategoryMap[catId] ?: "Outras Séries"

                                val seriesItem = PlaylistItem(
                                    id = finalId,
                                    title = name,
                                    url = "$formattedServer/series/$username/$password/$seriesIdStr.mp4",
                                    logoUrl = cover,
                                    groupName = groupName,
                                    type = "series",
                                    description = if (plot.isNullOrEmpty() || plot == "null") null else plot,
                                    rating = if (rating.isNullOrEmpty() || rating == "null") null else rating,
                                    year = if (releaseDate.isNullOrEmpty() || releaseDate == "null") null else releaseDate,
                                    genre = if (genre.isNullOrEmpty() || genre == "null") null else genre
                                )
                                val withClean = seriesItem.copy(cleanTitle = seriesItem.cleanSeriesName())
                                if (!isForbiddenTitle(withClean.title, withClean.cleanTitle)) {
                                    fetchedItems.add(withClean)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching series in fast sync", e)
            }

            // 3. Fetch VOD Categories
            val vodCategoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_vod_categories"
            val vodCategoryMap = mutableMapOf<String, String>()
            try {
                val req = Request.Builder().url(vodCategoriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("[")) {
                            val jsonArray = JSONArray(bodyStr)
                            val vodCategoryOrderList = mutableListOf<String>()
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catId = obj.optString("category_id")
                                val catName = obj.optString("category_name")
                                if (catId.isNotEmpty() && catName.isNotEmpty()) {
                                    vodCategoryMap[catId] = catName
                                    vodCategoryOrderList.add(catName)
                                }
                            }
                            if (vodCategoryOrderList.isNotEmpty()) {
                                val json = JSONArray(vodCategoryOrderList).toString()
                                sharedPrefs.edit().putString("movie_categories_order", json).apply()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching VOD categories in fast sync", e)
            }

            // 4. Fetch VOD Streams (Movies)
            val vodStreamsUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_vod_streams"
            try {
                val req = Request.Builder().url(vodStreamsUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("[")) {
                            val jsonArray = JSONArray(bodyStr)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name")
                                val streamIdStr = obj.optString("stream_id")
                                val streamId = streamIdStr.toIntOrNull() ?: 0
                                val cover = obj.optString("stream_icon")
                                val catId = obj.optString("category_id")
                                val rating = obj.optString("rating")
                                val releaseDate = obj.optString("added")
                                val containerExtension = obj.optString("container_extension", "mp4")
                                if (containerExtension.lowercase() != "mp4") {
                                    continue
                                }

                                val groupName = vodCategoryMap[catId] ?: "Outros Filmes"

                                val movieItem = PlaylistItem(
                                    id = streamId,
                                    title = name,
                                    url = "$formattedServer/movie/$username/$password/$streamIdStr.$containerExtension",
                                    logoUrl = cover,
                                    groupName = groupName,
                                    type = "movie",
                                    rating = if (rating.isNullOrEmpty() || rating == "null") null else rating,
                                    year = if (releaseDate.isNullOrEmpty() || releaseDate == "null") null else releaseDate,
                                    cleanTitle = name
                                )
                                fetchedItems.add(movieItem)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching VOD streams in fast sync", e)
            }

            // 5. Query existing URLs from the DB
            val existingUrls = playlistDao.getAllUrls().toSet()

            // 6. Filter only new items
            val newItems = fetchedItems.filter { it.url !in existingUrls }

            if (newItems.isNotEmpty()) {
                Log.d("IPTVRepository", "syncNewItemsOnStartup: Found ${newItems.size} new items. Inserting into DB...")
                newItems.chunked(500).forEach { chunk ->
                    playlistDao.insertItems(chunk)
                }
            } else {
                Log.d("IPTVRepository", "syncNewItemsOnStartup: No new items found on the server.")
            }

            true
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error during fast syncNewItemsOnStartup", e)
            false
        }
    }

    fun logout() {
        sharedPrefs.edit().clear().apply()
    }

    // Profile Actions
    suspend fun createProfile(name: String, colorHex: String, avatarIconName: String = "avatar_default"): Long = withContext(Dispatchers.IO) {
        profileDao.insertProfile(UserProfile(name = name, avatarColorHex = colorHex, avatarIconName = avatarIconName))
    }

    suspend fun deleteProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        profileDao.deleteProfile(profile)
    }

    // Playlist Actions
    suspend fun isFavorite(profileId: Int, itemId: Int): Boolean = withContext(Dispatchers.IO) {
        playlistDao.isFavorite(profileId, itemId)
    }

    suspend fun toggleFavorite(profileId: Int, itemId: Int) = withContext(Dispatchers.IO) {
        if (playlistDao.isFavorite(profileId, itemId)) {
            playlistDao.deleteFavorite(profileId, itemId)
        } else {
            playlistDao.insertFavorite(FavoriteItem(profileId, itemId))
        }
    }

    // Download & Stream Parser
    suspend fun loginAndDownloadPlaylist(
        serverUrl: String,
        username: String,
        password: String,
        isSilentSync: Boolean = false,
        onProgress: (String, Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Conectando ao servidor...", 0.1f)
            var formattedServer = serverUrl.trim()
            if (!formattedServer.startsWith("http://") && !formattedServer.startsWith("https://")) {
                formattedServer = "http://$formattedServer"
            }
            if (formattedServer.endsWith("/")) {
                formattedServer = formattedServer.substring(0, formattedServer.length - 1)
            }

            val url = "$formattedServer/get.php?username=$username&password=$password&type=m3u_plus&output=ts"
            Log.d("IPTVRepository", "Attempting login with URL: $url")
            val request = Request.Builder().url(url).build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                onProgress("Servidor retornou erro: ${response.code}", 0f)
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()

            onProgress("Baixando lista de canais, filmes e séries...", 0.3f)

            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            val parsedItems = mutableListOf<PlaylistItem>()
            var extinfLine: String? = null
            var line = reader.readLine()

            // Check if it starts with EXTM3U
            if (line == null || !line.trim().startsWith("#EXTM3U")) {
                Log.e("IPTVRepository", "Playlist invalid format: does not start with #EXTM3U")
                return@withContext false
            }

            // Stream and parse line by line
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    extinfLine = trimmed
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    if (extinfLine != null) {
                        // Create item
                        val item = parseItemLine(extinfLine, trimmed)
                        parsedItems.add(item)
                        extinfLine = null
                    }
                }
                line = reader.readLine()
            }

            onProgress("Lista baixada! Importando séries e categorias do painel...", 0.75f)

            // 1. Fetch Series Categories
            val categoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_series_categories"
            Log.d("IPTVRepository", "Fetching series categories from: $categoriesUrl")
            val categoriesRequest = Request.Builder().url(categoriesUrl).build()
            val categoryMap = mutableMapOf<String, String>()
            val categoryOrderList = mutableListOf<String>()

            try {
                client.newCall(categoriesRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("[")) {
                            val jsonArray = JSONArray(bodyStr)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catId = obj.optString("category_id")
                                val catName = obj.optString("category_name")
                                if (catId.isNotEmpty() && catName.isNotEmpty()) {
                                    categoryMap[catId] = catName
                                    categoryOrderList.add(catName)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching series categories", e)
            }

            if (categoryOrderList.isNotEmpty()) {
                val json = JSONArray(categoryOrderList).toString()
                sharedPrefs.edit().putString("series_categories_order", json).apply()
            }

            // Fetch and save VOD Categories for ordering
            val vodCategoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_vod_categories"
            val vodCategoryOrderList = mutableListOf<String>()
            try {
                val req = Request.Builder().url(vodCategoriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("[")) {
                            val jsonArray = JSONArray(bodyStr)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catName = obj.optString("category_name")
                                if (catName.isNotEmpty()) {
                                    vodCategoryOrderList.add(catName)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching VOD categories in login", e)
            }
            if (vodCategoryOrderList.isNotEmpty()) {
                val json = JSONArray(vodCategoryOrderList).toString()
                sharedPrefs.edit().putString("movie_categories_order", json).apply()
            }

            // 2. Fetch Series
            val seriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_series"
            Log.d("IPTVRepository", "Fetching series from: $seriesUrl")
            val seriesRequest = Request.Builder().url(seriesUrl).build()
            val apiSeriesList = mutableListOf<PlaylistItem>()

            try {
                client.newCall(seriesRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("[")) {
                            val jsonArray = JSONArray(bodyStr)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name")
                                val seriesId = obj.optString("series_id")
                                val cover = obj.optString("cover")
                                val catId = obj.optString("category_id")
                                val plot = obj.optString("plot")
                                val rating = obj.optString("rating")
                                val releaseDate = obj.optString("releaseDate")
                                val genre = obj.optString("genre")

                                val groupName = categoryMap[catId] ?: "Outras Séries"

                                val seriesIdInt = seriesId.toIntOrNull() ?: 0
                                val finalId = if (seriesIdInt > 0) seriesIdInt + 10000000 else 0
                                val seriesItem = PlaylistItem(
                                    id = finalId,
                                    title = name,
                                    url = "$formattedServer/series/$username/$password/$seriesId.mp4",
                                    logoUrl = cover,
                                    groupName = groupName,
                                    type = "series",
                                    description = if (plot.isNullOrEmpty() || plot == "null") null else plot,
                                    rating = if (rating.isNullOrEmpty() || rating == "null") null else rating,
                                    year = if (releaseDate.isNullOrEmpty() || releaseDate == "null") null else releaseDate,
                                    genre = if (genre.isNullOrEmpty() || genre == "null") null else genre
                                )
                                if (!isForbiddenTitle(seriesItem.title, seriesItem.cleanSeriesName())) {
                                    apiSeriesList.add(seriesItem)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching series", e)
            }

            // Sort series to match the exact panel categories order, then their original order in that category (ascending)
            val categoryOrderMap = categoryOrderList.mapIndexed { index, name -> name to index }.toMap()
            val sortedSeriesList = apiSeriesList.sortedWith(
                compareBy<PlaylistItem> { categoryOrderMap[it.groupName] ?: 999999 }
                    .thenBy { apiSeriesList.indexOf(it) }
            )
            parsedItems.addAll(sortedSeriesList)

            onProgress("Lista baixada! Salvando ${parsedItems.size} canais no aplicativo...", 0.8f)

            val fullyParsed = parsedItems.map { item ->
                if (item.type == "series" || item.type == "series_episode" || item.type == "movie") {
                    item.copy(cleanTitle = item.cleanSeriesName())
                } else {
                    item.copy(cleanTitle = item.title)
                }
            }
            if (isSilentSync) {
                val existingUrls = playlistDao.getAllUrls().toSet()
                val newItems = fullyParsed.filter { it.url !in existingUrls }
                if (newItems.isNotEmpty()) {
                    Log.d("IPTVRepository", "syncNewItemsSilently: Inserting ${newItems.size} new items into DB")
                    newItems.chunked(500).forEach { chunk ->
                        playlistDao.insertItems(chunk)
                    }
                } else {
                    Log.d("IPTVRepository", "syncNewItemsSilently: No new items found to insert")
                }
            } else {
                playlistDao.replaceAll(fullyParsed)
            }
            onProgress("Lista salva com sucesso!", 0.95f)

            // Trigger background pre-fetch immediately
            launch(Dispatchers.IO) {
                try {
                    startBackgroundMetadataPrefetch()
                } catch (e: Exception) {
                    Log.e("IPTVRepository", "Failed background pre-fetch right after processing", e)
                }
            }

            // Generate some Mock TMDB Items if none exist (or the list is empty/small) to ensure the banners look spectacular!
            populateFallbackVodsIfNeeded()

            saveCredentials(serverUrl, username, password)
            onProgress("Pronto!", 1.0f)
            return@withContext true
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error syncing playlist", e)
            onProgress("Falha na sincronização: ${e.localizedMessage}", 0f)
            return@withContext false
        }
    }

    private fun extractIdFromUrl(url: String): Int? {
        try {
            val lastSlash = url.lastIndexOf('/')
            if (lastSlash == -1) return null
            var segment = url.substring(lastSlash + 1)
            val lastDot = segment.lastIndexOf('.')
            if (lastDot != -1) {
                segment = segment.substring(0, lastDot)
            }
            val digits = segment.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                return digits.toIntOrNull()
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error extracting ID from URL: $url", e)
        }
        return null
    }

    private fun parseItemLine(extinf: String, url: String): PlaylistItem {
        val logoUrl = extractAttribute(extinf, "tvg-logo") ?: extractAttribute(extinf, "logo")
        var groupName = extractAttribute(extinf, "group-title") ?: "Geral"

        // Clean group name
        groupName = groupName.replace("\"", "").trim()

        val lastComma = extinf.lastIndexOf(',')
        var title = if (lastComma != -1 && lastComma < extinf.length - 1) {
            extinf.substring(lastComma + 1).trim()
        } else {
            extractAttribute(extinf, "tvg-name") ?: "Sem Nome"
        }

        title = title.replace("\"", "").trim()

        val groupUpper = groupName.uppercase()
        val urlUpper = url.uppercase()

        // Smarter classification: check for series indicators, movie indicators, and video extensions
        val hasSeriesIndicator = urlUpper.contains("/SERIES/") ||
                urlUpper.contains("/SERIE/") ||
                urlUpper.contains("/EPISODES/") ||
                urlUpper.contains("/EPISODE/") ||
                groupUpper.contains("SÉRIE") ||
                groupUpper.contains("SERIES") ||
                groupUpper.contains("SÉR.") ||
                groupUpper.contains("SERIE") ||
                groupUpper.contains("DORAMA") ||
                groupUpper.contains("NOVELA") ||
                groupUpper.contains("ANIME") ||
                groupUpper.contains("EPISODIO") ||
                groupUpper.contains("TEMPORADA") ||
                groupUpper.contains("EPISÓDIO") ||
                title.uppercase().contains("S0") ||
                title.uppercase().contains("S1") ||
                title.uppercase().contains("E0") ||
                title.uppercase().contains("E1") ||
                title.uppercase().contains("EP.") ||
                title.uppercase().contains("EPISÓDIO")

        val hasMovieIndicator = !hasSeriesIndicator && (
                urlUpper.contains("/MOVIE/") ||
                urlUpper.contains("/MOVIES/") ||
                groupUpper.contains("FILME") ||
                groupUpper.contains("MOVIE") ||
                groupUpper.contains("VOD") ||
                groupUpper.contains("CINEMA") ||
                groupUpper.contains("LANÇAMENTOS") ||
                groupUpper.contains("ANIMACAO") ||
                groupUpper.contains("DOCUMENTARIOS")
        )

        val isVodFile = urlUpper.contains(".MP4") || urlUpper.contains(".MKV") || urlUpper.contains(".AVI") || urlUpper.contains(".M4V") || urlUpper.contains(".FLV") || urlUpper.contains(".WEBM") || urlUpper.contains(".MOV")
        val isLiveStream = urlUpper.contains(".M3U8") || urlUpper.contains("/LIVE/")
        val isSeriesPath = urlUpper.contains("/SERIES/") || urlUpper.contains("/SERIE/") || urlUpper.contains("/EPISODES/") || urlUpper.contains("/EPISODE/")
        val isMoviePath = urlUpper.contains("/MOVIE/") || urlUpper.contains("/MOVIES/")

        val isLiveGroup = groupUpper.contains("CANAIS") ||
                groupUpper.contains("CANAL") ||
                groupUpper.contains("LIVE") ||
                groupUpper.contains("TV") ||
                groupUpper.contains("AO VIVO") ||
                groupUpper.contains("TELEVISÃO") ||
                groupUpper.contains("TELEVISAO") ||
                groupUpper.contains("NOTICIAS") ||
                groupUpper.contains("ESPORTES") ||
                groupUpper.contains("NEWS") ||
                groupUpper.contains("SPORTS") ||
                groupUpper.contains("24/7") ||
                groupUpper.contains("24H") ||
                groupUpper.contains("24 HORAS") ||
                groupUpper.contains("RADIOS") ||
                groupUpper.contains("RÁDIOS") ||
                groupUpper.contains("EPG") ||
                groupUpper.contains("PREMIERE") ||
                groupUpper.contains("TELECINE") ||
                groupUpper.contains("HBO") ||
                groupUpper.contains("GLOBOPLAY") ||
                groupUpper.contains("BBB") ||
                groupUpper.contains("DAZN") ||
                groupUpper.contains("ESPN") ||
                groupUpper.contains("COMBATE") ||
                groupUpper.contains("SPORTV")

        val type = when {
            isSeriesPath -> if (isVodFile) "series_episode" else "series"
            (isMoviePath || (isVodFile && !isLiveStream && !isLiveGroup)) -> "movie"
            else -> "live"
        }

        val extractedId = extractIdFromUrl(url)
        val finalId = when (type) {
            "movie" -> extractedId ?: 0
            "series", "series_episode" -> if (extractedId != null) extractedId + 10000000 else 0
            "live" -> 0
            else -> 0
        }

        return PlaylistItem(
            id = finalId,
            title = title,
            url = url,
            logoUrl = logoUrl,
            groupName = groupName,
            type = type
        )
    }

    private fun extractAttribute(line: String, key: String): String? {
        val pattern = "$key=\""
        val startIdx = line.indexOf(pattern)
        if (startIdx == -1) return null
        val valueStart = startIdx + pattern.length
        val endIdx = line.indexOf('"', valueStart)
        if (endIdx == -1) return null
        return line.substring(valueStart, endIdx)
    }

    private fun cleanTitleForSearch(title: String): String {
        var clean = PlaylistItem(title = title, url = "", groupName = "", type = "series").cleanSeriesName()
        clean = clean.replace(searchPatternsCombined, "")
        clean = clean.replace(spacesRegex, " ").trim()
        clean = clean.replace(dashEndRegex, "").trim()
        clean = clean.replace(hyphenEndRegex, "").trim()
        clean = clean.replace(colonEndRegex, "").trim()
        return if (clean.isEmpty()) title else clean
    }

    /**
     * A resposta de imagens do TMDB pode trazer códigos de idioma simples ("pt")
     * ou regionais ("pt-BR"). Esta ordem garante a preferência brasileira sem
     * impedir um fallback quando a obra não possuir arte localizada no Brasil.
     */
    private fun logoLanguagePriority(language: String): Int = when (language.lowercase()) {
        "pt-br" -> 0
        "pt" -> 1
        "pt-pt" -> 2
        "en-us" -> 3
        "en" -> 4
        "" -> 5
        else -> 6
    }

    private fun findPreferredTmdbLogo(
        mediaType: String,
        tmdbId: Int,
        apiKey: String
    ): String? {
        val imagesUrl =
            "https://api.themoviedb.org/3/$mediaType/$tmdbId/images" +
                    "?api_key=$apiKey&include_image_language=pt-BR,pt-PT,pt,en-US,en,null"

        client.newCall(Request.Builder().url(imagesUrl).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("IPTVRepository", "TMDB images failed for id=$tmdbId: HTTP ${response.code}")
                return null
            }

            val logos = JSONObject(response.body?.string().orEmpty()).optJSONArray("logos") ?: return null
            var selectedPath: String? = null
            var selectedLanguage = ""
            var selectedPriority = Int.MAX_VALUE

            for (index in 0 until logos.length()) {
                val logo = logos.optJSONObject(index) ?: continue
                val path = logo.optString("file_path").takeIf { it.isNotBlank() } ?: continue
                val language = logo.optString("iso_639_1").orEmpty()
                val priority = logoLanguagePriority(language)

                if (priority < selectedPriority) {
                    selectedPath = path
                    selectedLanguage = language
                    selectedPriority = priority
                }
            }

            if (selectedPath != null) {
                Log.d(
                    "IPTVRepository",
                    "TMDB logo selected for id=$tmdbId: language=${selectedLanguage.ifBlank { "null" }}, priority=$selectedPriority"
                )
                return "https://image.tmdb.org/t/p/original$selectedPath"
            }
            Log.w("IPTVRepository", "TMDB returned no usable logo for id=$tmdbId")
            return null
        }
    }

    private fun selectBestTmdbResult(results: org.json.JSONArray, queryTitle: String): org.json.JSONObject? {
        if (results.length() == 0) return null

        fun normalize(s: String): String {
            val unaccented = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            return unaccented.lowercase().trim()
        }

        fun stripPunctuation(s: String): String {
            return normalize(s).replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ").trim()
        }

        val normTarget = normalize(queryTitle)
        val targetClean = stripPunctuation(queryTitle)
        if (targetClean.isEmpty()) return null

        val targetWords = targetClean.split(" ").filter { it.isNotEmpty() }

        // 1. Exact match on normalized title / original title
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val name = normalize(obj.optString("name", obj.optString("title", "")))
            val origName = normalize(obj.optString("original_name", obj.optString("original_title", "")))
            if (name == normTarget || origName == normTarget) {
                return obj
            }
        }

        // 2. Exact match ignoring punctuation
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val nameClean = stripPunctuation(obj.optString("name", obj.optString("title", "")))
            val origClean = stripPunctuation(obj.optString("original_name", obj.optString("original_title", "")))
            if (nameClean == targetClean || origClean == targetClean) {
                return obj
            }
        }

        // 3. Strict rule for single-word / short titles (<= 6 chars or 1 word, e.g. "James", "Silo", "Halo", "Loki"):
        // Do NOT match multi-word titles like "James West", "King James", "Loki: Season 1" unless exact word match
        if (targetWords.size == 1 && targetClean.length <= 6) {
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val nameClean = stripPunctuation(obj.optString("name", obj.optString("title", "")))
                val origClean = stripPunctuation(obj.optString("original_name", obj.optString("original_title", "")))

                val nameWords = nameClean.split(" ").filter { it.isNotEmpty() }
                val origWords = origClean.split(" ").filter { it.isNotEmpty() }

                if (nameWords.size == 1 && nameWords[0] == targetClean) return obj
                if (origWords.size == 1 && origWords[0] == targetClean) return obj
            }
            // If no exact single-word match found for a short single-word target, return null to avoid false positives!
            return null
        }

        // 4. Multi-word partial matching: target words must all be contained in result name/origName
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val nameClean = stripPunctuation(obj.optString("name", obj.optString("title", "")))
            val origClean = stripPunctuation(obj.optString("original_name", obj.optString("original_title", "")))

            val nameWords = nameClean.split(" ").filter { it.isNotEmpty() }
            val origWords = origClean.split(" ").filter { it.isNotEmpty() }

            val matchesName = targetWords.all { tw -> nameWords.contains(tw) }
            val matchesOrig = targetWords.all { tw -> origWords.contains(tw) }

            if (matchesName || matchesOrig) {
                val wordDiff = Math.abs(nameWords.size - targetWords.size)
                val origWordDiff = Math.abs(origWords.size - targetWords.size)
                if (wordDiff <= 3 || origWordDiff <= 3) {
                    return obj
                }
            }
        }

        return null
    }

    suspend fun enrichItemWithTMDB(item: PlaylistItem): PlaylistItem = withContext(Dispatchers.IO) {
        tmdbSemaphore.withPermit {
            kotlinx.coroutines.delay(100)
            val tmdbApiKey = "355b405bb005248e382a8b400ebab70b"
            try {
                val cleanTitle = cleanTitleForSearch(item.cleanTitle ?: item.title)
                Log.d("IPTVRepository", "Searching TMDB for: $cleanTitle (original: ${item.title})")
                val isSeries = item.type == "series" || item.type == "series_episode"
                val searchType = if (isSeries) "tv" else "movie"
                val url = "https://api.themoviedb.org/3/search/$searchType?api_key=$tmdbApiKey&query=${java.net.URLEncoder.encode(cleanTitle, "UTF-8")}&language=pt-BR"

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: ""
                    val results = JSONObject(jsonStr).optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val firstResult = selectBestTmdbResult(results, cleanTitle) ?: return@withPermit item
                        val id = firstResult.optInt("id")
                        val posterPath = firstResult.optString("poster_path", "")
                        val backdropPath = firstResult.optString("backdrop_path", "")
                        var description = firstResult.optString("overview", item.description ?: "")
                        var rating = firstResult.optDouble("vote_average", 0.0)

                        val dateField = if (isSeries) "first_air_date" else "release_date"
                        val dateStr = firstResult.optString(dateField, "")
                        var year = if (dateStr.length >= 4) dateStr.substring(0, 4) else ""

                        var genreStr = item.genre
                        var numSeasons: Int? = item.numSeasons
                        var numEpisodes: Int? = item.numEpisodes

                        // Query TMDB full details endpoint for genres, seasons count, episode count
                        try {
                            val detailsUrl = "https://api.themoviedb.org/3/$searchType/$id?api_key=$tmdbApiKey&language=pt-BR"
                            val detailsReq = Request.Builder().url(detailsUrl).build()
                            val detailsResp = client.newCall(detailsReq).execute()
                            if (detailsResp.isSuccessful) {
                                val detailsJsonStr = detailsResp.body?.string() ?: ""
                                val detailsObj = JSONObject(detailsJsonStr)
                                val overview = detailsObj.optString("overview", "")
                                if (overview.isNotEmpty()) {
                                    description = overview
                                }
                                val voteAvg = detailsObj.optDouble("vote_average", rating)
                                if (voteAvg > 0) {
                                    rating = voteAvg
                                }
                                val dStr = detailsObj.optString(dateField, dateStr)
                                if (dStr.length >= 4) {
                                    year = dStr.substring(0, 4)
                                }
                                val genresArr = detailsObj.optJSONArray("genres")
                                if (genresArr != null && genresArr.length() > 0) {
                                    val genreNames = mutableListOf<String>()
                                    for (g in 0 until genresArr.length()) {
                                        val gObj = genresArr.optJSONObject(g)
                                        if (gObj != null) {
                                            val gName = gObj.optString("name", "")
                                            if (gName.isNotEmpty()) genreNames.add(gName)
                                        }
                                    }
                                    if (genreNames.isNotEmpty()) {
                                        genreStr = genreNames.joinToString(", ")
                                    }
                                }
                                if (isSeries) {
                                    val seasonsCount = detailsObj.optInt("number_of_seasons", 0)
                                    val episodesCount = detailsObj.optInt("number_of_episodes", 0)
                                    if (seasonsCount > 0) numSeasons = seasonsCount
                                    if (episodesCount > 0) numEpisodes = episodesCount
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("IPTVRepository", "Error fetching TMDB full details for $searchType $id", e)
                        }

                        val posterUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else item.logoUrl
                        val backdropUrl = if (backdropPath.isNotEmpty()) "https://image.tmdb.org/t/p/original$backdropPath" else item.backdropUrl

                        // Nunca preserve um logo antigo como prioridade: ele pode ser en-US.
                        // A API de imagens abaixo é a fonte de verdade para o logo localizado.
                        var logoUrl: String? = null
                        try {
                            logoUrl = findPreferredTmdbLogo(searchType, id, tmdbApiKey)
                        } catch (e: Exception) {
                            Log.e("IPTVRepository", "Error fetching TMDB logos for $id", e)
                        }

                        if (logoUrl.isNullOrEmpty()) {
                            Log.d("IPTVRepository", "No TMDB logo found for ${item.title}, falling back to Gemini")
                            try {
                                val geminiEnriched = enrichItemWithGemini(item)
                                logoUrl = geminiEnriched.logo ?: geminiEnriched.titleLogoUrl
                            } catch (e: Exception) {
                                Log.e("IPTVRepository", "Gemini logo fallback failed for ${item.title}", e)
                            }
                        }

                        val enriched = item.copy(
                            type = if (isSeries) "series" else item.type,
                            logoUrl = posterUrl,
                            backdropUrl = backdropUrl,
                            titleLogoUrl = logoUrl,
                            logo = logoUrl,
                            description = description,
                            genre = genreStr ?: item.genre,
                            year = if (year.isNotEmpty()) year else item.year,
                            rating = if (rating > 0) String.format(java.util.Locale.US, "%.1f", rating) else item.rating,
                            numSeasons = numSeasons,
                            numEpisodes = numEpisodes
                        )
                        playlistDao.insertAll(listOf(enriched))
                        return@withPermit enriched
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "TMDB enrichment failed for ${item.title}", e)
            }
            enrichItemWithGemini(item)
        }
    }

    // Real-time Gemini metadata enrichment for movies and series
    suspend fun enrichItemWithGemini(item: PlaylistItem): PlaylistItem = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("IPTVRepository", "Gemini API Key missing, returning default item")
            return@withContext item
        }

        try {
            val prompt = """
                Você é um enriquecedor de metadados do TMDB. Dado o seguinte título de filme ou série: '${item.title}' (categoria: '${item.groupName}'), retorne um objeto JSON estritamente no seguinte formato:
                {
                  "posterUrl": "https://image.tmdb.org/t/p/w500/...jpg",
                  "backdropUrl": "https://image.tmdb.org/t/p/original/...jpg",
                  "titleLogoUrl": "https://image.tmdb.org/t/p/original/...png",
                  "description": "Sinopse do filme...",
                  "year": "2024",
                  "genre": "Ação / Aventura",
                  "rating": "8.5"
                }
                Requisitos críticos:
                1. Use imagens válidas e reais de posters/backdrops do TMDB. Se não encontrar uma imagem TMDB real exata, use imagens em alta resolução do Unsplash de temas semelhantes.
                2. Para "titleLogoUrl", use um link de imagem real de logo transparente (clearlogo ou hdclearart do TMDB), ou um link de banner de alta qualidade.
                3. Não inclua NENHUM text adicional, nem mesmo blocos de código ```json ou marcações markdown. Apenas a string de JSON bruta.
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                put("contents", org.json.JSONArray().put(JSONObject().apply {
                    put("parts", org.json.JSONArray().put(JSONObject().apply {
                        put("text", prompt)
                    }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBodyStr = response.body?.string() ?: ""
                val rootJson = JSONObject(responseBodyStr)
                val candidates = rootJson.getJSONArray("candidates")
                val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                val rawJsonStr = parts.getJSONObject(0).getString("text").trim()

                val resultJson = JSONObject(rawJsonStr)

                val enrichedItem = item.copy(
                    logoUrl = resultJson.optString("posterUrl", item.logoUrl),
                    backdropUrl = resultJson.optString("backdropUrl", item.backdropUrl),
                    titleLogoUrl = resultJson.optString("titleLogoUrl", item.titleLogoUrl),
                    logo = resultJson.optString("logo", resultJson.optString("titleLogoUrl", item.logo)),
                    description = resultJson.optString("description", item.description),
                    year = resultJson.optString("year", item.year),
                    genre = resultJson.optString("genre", item.genre),
                    rating = resultJson.optString("rating", item.rating)
                )

                // Cache back into Room
                playlistDao.insertAll(listOf(enrichedItem))
                return@withContext enrichedItem
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Gemini Enrichment failed for ${item.title}", e)
        }
        return@withContext item
    }

    // Populate standard high quality cinematic content to guarantee it looks 100% like TMDB out of the box
    suspend fun populateFallbackVodsIfNeeded() {
        val featuredItems = listOf(
            // 1. TENDÊNCIAS
            PlaylistItem(
                title = "Obsessão",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg",
                titleLogoUrl = null,
                description = "Uma jovem mulher herda uma antiga mansão isolada, apenas para descobrir que o local esconde segredos obscuros e uma presença obsessiva e aterrorizante que ameaça sua sanidade.",
                year = "14 de maio de 2026",
                genre = "Drama / Suspense",
                rating = "8.2",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Uma Casa na Pradaria",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/fBieUo3SdItUrXZE16YxbpjwXIe.jpg",
                titleLogoUrl = null,
                description = "Uma família busca recomeçar a vida em uma pacata região rural, mas forças misteriosas começam a afetar a plantação e o comportamento dos moradores.",
                year = "9 de julho de 2026",
                genre = "Drama / Mistério",
                rating = "7.9",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "The Furious",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/k334z7zxOJlJvOsOwyRF6HClCvi.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/1AVF2fAevpfi2HP6AEpptG1kg8R.jpg",
                titleLogoUrl = null,
                description = "Um piloto de fuga lendário é forçado a voltar à ativa para salvar seu irmão de uma gangue de criminosos internacionais em alta velocidade.",
                year = "10 de junho de 2026",
                genre = "Ação / Policial",
                rating = "8.7",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Moana",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/c6BPbkO5Npt1OdwttAxCFo06wtH.jpg",
                titleLogoUrl = null,
                description = "Uma jovem navegadora embarca em uma ousada missão marítima para salvar seu povo e descobrir sua verdadeira identidade lendária.",
                year = "9 de julho de 2026",
                genre = "Animação / Aventura",
                rating = "9.0",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Silo",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/u7vS96H96oI4UIs3pLI85gSIs4u.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/q7776Z9Yg58P6N8v7HkWK6fOunY.jpg",
                titleLogoUrl = null,
                description = "Em um futuro degradado e tóxico, uma comunidade existe em um gigantesco silo subterrâneo que se estende por centenas de andares abaixo da terra.",
                year = "4 de maio de 2023",
                genre = "Ficção Científica / Drama",
                rating = "8.3",
                groupName = "Tendências",
                type = "series"
            ),
            PlaylistItem(
                title = "A Morte do Demônio: Em Chamas",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/fteLdvfRnltfLjAEnsl5E3vImnW.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg",
                titleLogoUrl = null,
                description = "O despertar de uma força demoníaca ancestral em uma floresta remota liberta espíritos malignos que possuem os corpos dos vivos em um pesadelo implacável.",
                year = "7 de julho de 2026",
                genre = "Terror / Sobrenatural",
                rating = "8.5",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Trunfo",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg",
                titleLogoUrl = null,
                description = "Um drama de alta espionagem e intriga política onde um agente secreto joga seu último trunfo para desmascarar uma conspiração global.",
                year = "10 de julho de 2026",
                genre = "Drama / Suspense",
                rating = "8.0",
                groupName = "Tendências",
                type = "series"
            ),
            PlaylistItem(
                title = "A Odisseia",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/m3Pom6pbD51bBv3syz8NMHda3fz.jpg",
                titleLogoUrl = null,
                description = "Uma jornada épica inspirada no clássico grego, onde um guerreiro enfrenta monstros mitológicos e a fúria dos deuses para retornar ao seu lar.",
                year = "16 de julho de 2026",
                genre = "Épico / Fantasia",
                rating = "8.9",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Sem Nada a Perder",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/6tByIjKn3VLEVHhfpiNNWrnSIOM.jpg",
                titleLogoUrl = null,
                description = "Dois detetives obstinados entram em um jogo perigoso de gato e rato com um criminoso genial que planeja o maior assalto da história.",
                year = "8 de julho de 2026",
                genre = "Policial / Suspense",
                rating = "8.1",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Backrooms",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/mCpwRayjXMFzKHbjbzc5JRKfq1O.jpg",
                titleLogoUrl = null,
                description = "Um jovem cientista acidentalmente escorrega para fora da realidade e acorda em um labirinto infinito de salas amarelas vazias habitadas por uma entidade misteriosa.",
                year = "28 de maio de 2026",
                genre = "Terror / Sci-Fi",
                rating = "8.3",
                groupName = "Tendências",
                type = "movie"
            ),

            // 2. ÚLTIMOS TRAILERS
            PlaylistItem(
                title = "O Fim da Rua",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.jpg",
                titleLogoUrl = null,
                description = "Trailer Oficial Dublado",
                year = "Trailer Oficial Dublado",
                genre = "Ação / Suspense",
                rating = "7.8",
                groupName = "Últimos Trailers",
                type = "movie"
            ),
            PlaylistItem(
                title = "A Morte do Demônio: Em Chamas",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/fteLdvfRnltfLjAEnsl5E3vImnW.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg",
                titleLogoUrl = null,
                description = "Teaser de Anúncio da Data",
                year = "Teaser de Anúncio da Data",
                genre = "Terror / Sobrenatural",
                rating = "8.2",
                groupName = "Últimos Trailers",
                type = "movie"
            ),
            PlaylistItem(
                title = "Hope",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/7WOBeoaXUNGzbm79Umio2CcH941.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/9zgnuwIK6JAc6giVsscq9H82li1.jpg",
                titleLogoUrl = null,
                description = "Official US Trailer [Subtitled]",
                year = "Official US Trailer [Subtitled]",
                genre = "Drama / Sci-Fi",
                rating = "7.5",
                groupName = "Últimos Trailers",
                type = "movie"
            ),
            PlaylistItem(
                title = "Jackass: Best and Last",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/zqd39GO0GdO5TaO8HxnkmWwu2p8.jpg",
                titleLogoUrl = null,
                description = "Big Red Rocket",
                year = "Big Red Rocket",
                genre = "Comédia / Reality",
                rating = "8.0",
                groupName = "Últimos Trailers",
                type = "movie"
            ),

            // 3. OS MAIS POPULARES
            PlaylistItem(
                title = "A Casa do Dragão",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg",
                titleLogoUrl = null,
                description = "A história da Casa Targaryen, ambientada 200 anos antes dos eventos de Game of Thrones.",
                year = "21 de agosto de 2022",
                genre = "Aventura / Drama",
                rating = "9.1",
                groupName = "Os Mais Populares",
                type = "series"
            ),
            PlaylistItem(
                title = "NCIS: Investigação Naval",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/nn3SuLTO4hum8yAxaY4ql8h6kRk.jpg",
                titleLogoUrl = null,
                description = "Casos criminais envolvendo pessoal da Marinha americana investigados por agentes especiais.",
                year = "14 de maio de 2026",
                genre = "Policial / Ação",
                rating = "8.4",
                groupName = "Os Mais Populares",
                type = "series"
            ),
            PlaylistItem(
                title = "Na Zona Cinzenta",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/qcIKxhqGMIj8uujsSoSMZWr8QqU.jpg",
                titleLogoUrl = null,
                description = "Agentes especiais operam nas fronteiras da legalidade internacional para combater terroristas.",
                year = "14 de maio de 2026",
                genre = "Ação / Suspense",
                rating = "8.0",
                groupName = "Os Mais Populares",
                type = "movie"
            ),
            PlaylistItem(
                title = "O Limite do Prazer",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/rHnANzYUmV3WZw3n0yWOLiR3pen.jpg",
                titleLogoUrl = null,
                description = "Um romance tórrido e perigoso entre um escritor intrigado e uma misteriosa milionária.",
                year = "4 de setembro de 2025",
                genre = "Drama / Romance",
                rating = "7.8",
                groupName = "Os Mais Populares",
                type = "movie"
            ),
            PlaylistItem(
                title = "Obsessão",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg",
                titleLogoUrl = null,
                description = "Presença obsessiva e aterrorizante que ameaça a sanidade de uma jovem herdeira.",
                year = "14 de maio de 2026",
                genre = "Drama / Suspense",
                rating = "8.2",
                groupName = "Os Mais Populares",
                type = "movie"
            ),
            PlaylistItem(
                title = "Lei & Ordem",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/tc7canPSAn2X14hYi6Rl3gZm1o4.jpg",
                titleLogoUrl = null,
                description = "Casos criminais sob a ótica dos detetives policiais e dos promotores de justiça de Nova York.",
                year = "13 de setembro de 1990",
                genre = "Drama / Policial",
                rating = "8.8",
                groupName = "Os Mais Populares",
                type = "series"
            ),
            PlaylistItem(
                title = "Rancho Dutton",
                url = "http://streatv.elementfx.com/get.php",
                logoUrl = "https://image.tmdb.org/t/p/w500/aJVroxZZKReiSlASpODQ23qrdGa.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/wh5agdl3b7fqC7mSjsCkHx4fMAs.jpg",
                titleLogoUrl = null,
                description = "A luta diária de uma família proprietária de terras em Montana contra construtoras e o governo.",
                year = "15 de outubro de 2026",
                genre = "Drama / Faroeste",
                rating = "8.9",
                groupName = "Os Mais Populares",
                type = "series"
            )
        )

        // Since we want these items to ALWAYS be available for our TMDB categories, we check and insert them
        playlistDao.insertAll(featuredItems)

        // Asynchronously enrich fallback items from TMDB in parallel so they display real high-res posters and backdrops!
        try {
            kotlinx.coroutines.coroutineScope {
                featuredItems.forEach { item ->
                    launch(Dispatchers.IO) {
                        try {
                            enrichItemWithTMDB(item)
                        } catch (e: Exception) {
                            Log.e("IPTVRepository", "Error background enriching fallback item ${item.title}", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error in coroutine scope for fallback enrichment", e)
        }

        // Real-time update from TMDB API to fetch latest releases!
        try {
            fetchRealtimeTMDBContent()
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error performing real-time TMDB content refresh", e)
        }

        try {
            fixLiveChannelTypesInDatabase()
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fixing live channel types", e)
        }
    }

    suspend fun fixLiveChannelTypesInDatabase() = withContext(Dispatchers.IO) {
        try {
            playlistDao.fixLiveChannelTypes()
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fixing live channel types in DB", e)
        }
    }

    suspend fun fetchRealtimeTMDBContent() = withContext(Dispatchers.IO) {
        val tmdbApiKey = "355b405bb005248e382a8b400ebab70b"
        val listToInsert = mutableListOf<PlaylistItem>()

        // 1. Trending (All: Movies & TV) -> "Tendências"
        val trendingList = mutableListOf<PlaylistItem>()
        try {
            val url = "https://api.themoviedb.org/3/trending/all/day?api_key=$tmdbApiKey&language=pt-BR"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val mediaType = item.optString("media_type", "movie")
                                if (mediaType != "movie" && mediaType != "tv") continue

                                val isMovie = mediaType == "movie"
                                val title = if (isMovie) {
                                    item.optString("title", item.optString("original_title", "Filme TMDB"))
                                } else {
                                    item.optString("name", item.optString("original_name", "Série TMDB"))
                                }

                                val posterPath = item.optString("poster_path", "")
                                val backdropPath = item.optString("backdrop_path", "")
                                val overview = item.optString("overview", "")
                                val vote = item.optDouble("vote_average", 0.0)
                                val date = if (isMovie) item.optString("release_date", "") else item.optString("first_air_date", "")
                                val yearVal = if (date.length >= 4) date.substring(0, 4) else ""

                                val logoUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else ""
                                val backdropUrl = if (backdropPath.isNotEmpty()) "https://image.tmdb.org/t/p/original$backdropPath" else ""

                                trendingList.add(
                                    PlaylistItem(
                                        title = title,
                                        url = "http://streatv.elementfx.com/get.php",
                                        logoUrl = logoUrl,
                                        backdropUrl = backdropUrl,
                                        titleLogoUrl = null,
                                        description = overview,
                                        year = yearVal,
                                        genre = if (isMovie) "Filme / Tendência" else "Série / Tendência",
                                        rating = String.format(java.util.Locale.US, "%.1f", vote),
                                        groupName = "Tendências",
                                        type = if (isMovie) "movie" else "series"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching trending from TMDB", e)
        }

        // 2. Upcoming Movies -> "Últimos Trailers"
        val upcomingList = mutableListOf<PlaylistItem>()
        try {
            val url = "https://api.themoviedb.org/3/movie/upcoming?api_key=$tmdbApiKey&language=pt-BR"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val movie = results.getJSONObject(i)
                                val title = movie.optString("title", movie.optString("original_title", "Trailer TMDB"))
                                val posterPath = movie.optString("poster_path", "")
                                val backdropPath = movie.optString("backdrop_path", "")
                                val overview = movie.optString("overview", "")
                                val vote = movie.optDouble("vote_average", 0.0)
                                val date = movie.optString("release_date", "")
                                val yearVal = if (date.length >= 4) date.substring(0, 4) else "Upcoming"

                                val logoUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else ""
                                val backdropUrl = if (backdropPath.isNotEmpty()) "https://image.tmdb.org/t/p/original$backdropPath" else ""

                                upcomingList.add(
                                    PlaylistItem(
                                        title = title,
                                        url = "http://streatv.elementfx.com/get.php",
                                        logoUrl = logoUrl,
                                        backdropUrl = backdropUrl,
                                        titleLogoUrl = null,
                                        description = overview,
                                        year = yearVal,
                                        genre = "Último Trailer",
                                        rating = String.format(java.util.Locale.US, "%.1f", vote),
                                        groupName = "Últimos Trailers",
                                        type = "movie"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching upcoming trailers from TMDB", e)
        }

        // 3. Popular Movies & TV Shows -> "Os Mais Populares"
        val popularList = mutableListOf<PlaylistItem>()
        val popularMovies = mutableListOf<PlaylistItem>()
        val popularTV = mutableListOf<PlaylistItem>()

        // 3a. Popular Movies
        try {
            val url = "https://api.themoviedb.org/3/movie/popular?api_key=$tmdbApiKey&language=pt-BR"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val movie = results.getJSONObject(i)
                                val title = movie.optString("title", movie.optString("original_title", "Filme TMDB"))
                                val posterPath = movie.optString("poster_path", "")
                                val backdropPath = movie.optString("backdrop_path", "")
                                val overview = movie.optString("overview", "")
                                val vote = movie.optDouble("vote_average", 0.0)
                                val date = movie.optString("release_date", "")
                                val yearVal = if (date.length >= 4) date.substring(0, 4) else ""

                                val logoUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else ""
                                val backdropUrl = if (backdropPath.isNotEmpty()) "https://image.tmdb.org/t/p/original$backdropPath" else ""

                                popularMovies.add(
                                    PlaylistItem(
                                        title = title,
                                        url = "http://streatv.elementfx.com/get.php",
                                        logoUrl = logoUrl,
                                        backdropUrl = backdropUrl,
                                        titleLogoUrl = null,
                                        description = overview,
                                        year = yearVal,
                                        genre = "Filme Popular",
                                        rating = String.format(java.util.Locale.US, "%.1f", vote),
                                        groupName = "Os Mais Populares",
                                        type = "movie"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching popular movies from TMDB", e)
        }

        // 3b. Popular TV Shows
        try {
            val url = "https://api.themoviedb.org/3/tv/popular?api_key=$tmdbApiKey&language=pt-BR"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val tv = results.getJSONObject(i)
                                val title = tv.optString("name", tv.optString("original_name", "Série TMDB"))
                                val posterPath = tv.optString("poster_path", "")
                                val backdropPath = tv.optString("backdrop_path", "")
                                val overview = tv.optString("overview", "")
                                val vote = tv.optDouble("vote_average", 0.0)
                                val date = tv.optString("first_air_date", "")
                                val yearVal = if (date.length >= 4) date.substring(0, 4) else ""

                                val logoUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else ""
                                val backdropUrl = if (backdropPath.isNotEmpty()) "https://image.tmdb.org/t/p/original$backdropPath" else ""

                                popularTV.add(
                                    PlaylistItem(
                                        title = title,
                                        url = "http://streatv.elementfx.com/get.php",
                                        logoUrl = logoUrl,
                                        backdropUrl = backdropUrl,
                                        titleLogoUrl = null,
                                        description = overview,
                                        year = yearVal,
                                        genre = "Série Popular",
                                        rating = String.format(java.util.Locale.US, "%.1f", vote),
                                        groupName = "Os Mais Populares",
                                        type = "series"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching popular series from TMDB", e)
        }

        // Interleave popular movies and series to create a balanced popularList
        val maxPopularLen = maxOf(popularMovies.size, popularTV.size)
        for (i in 0 until maxPopularLen) {
            if (i < popularMovies.size) {
                popularList.add(popularMovies[i])
            }
            if (i < popularTV.size) {
                popularList.add(popularTV[i])
            }
        }

        // Combine them all in a single list to insert
        listToInsert.addAll(trendingList)
        listToInsert.addAll(upcomingList)
        listToInsert.addAll(popularList)

        // Only clear and write if we successfully fetched at least some lists to avoid leaving it blank!
        if (trendingList.isNotEmpty() || upcomingList.isNotEmpty() || popularList.isNotEmpty()) {
            playlistDao.replaceMainSectionsItems(listToInsert)
            Log.d("IPTVRepository", "Successfully fetched, cleared, and inserted ${listToInsert.size} new real-time TMDB items in exact order!")
        } else {
            Log.w("IPTVRepository", "Could not fetch any items from TMDB API; keeping existing cached items.")
        }
    }

    suspend fun syncMainSectionsMetadata() = withContext(Dispatchers.IO) {
        try {
            Log.d("IPTVRepository", "Starting asynchronous background metadata re-fetch for main sections...")
            val items = playlistDao.getMainSectionsItems()
            if (items.isEmpty()) {
                Log.d("IPTVRepository", "No items found in main sections for metadata sync.")
                return@withContext
            }

            // Partition to prioritize "texto puro" (items currently without a title logo)
            val (withoutLogo, withLogo) = items.partition { it.titleLogoUrl.isNullOrEmpty() && it.logo.isNullOrEmpty() }

            Log.d("IPTVRepository", "Sync partitioned: ${withoutLogo.size} items without logo, ${withLogo.size} items with logo.")

            // 1. Process those without logo first
            withoutLogo.forEach { item ->
                try {
                    enrichItemWithTMDB(item)
                } catch (e: Exception) {
                    Log.e("IPTVRepository", "Error background sync for ${item.title}", e)
                }
            }

            // 2. Refresh items with logos to guarantee fresh data
            withLogo.forEach { item ->
                try {
                    enrichItemWithTMDB(item)
                } catch (e: Exception) {
                    Log.e("IPTVRepository", "Error background sync refresh for ${item.title}", e)
                }
            }

            Log.d("IPTVRepository", "Asynchronous metadata sync for main sections completed successfully.")
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Failed to run syncMainSectionsMetadata", e)
        }
    }

    suspend fun getRealCastFromTMDB(item: PlaylistItem): List<CastMember> = withContext(Dispatchers.IO) {
        val tmdbApiKey = "355b405bb005248e382a8b400ebab70b"
        val cleanTitle = cleanTitleForSearch(item.cleanTitle ?: item.title)
        val isSeries = item.type == "series" || item.type == "series_episode"
        val searchType = if (isSeries) "tv" else "movie"

        try {
            // Search TMDB to find the ID
            val searchUrl = "https://api.themoviedb.org/3/search/$searchType?api_key=$tmdbApiKey&query=${java.net.URLEncoder.encode(cleanTitle, "UTF-8")}&language=pt-BR"
            val searchRequest = Request.Builder().url(searchUrl).build()
            client.newCall(searchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val results = JSONObject(body).optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val firstResult = selectBestTmdbResult(results, cleanTitle) ?: return@use
                        val tmdbId = firstResult.optInt("id")

                        // Fetch credits for this tmdbId
                        val creditsUrl = "https://api.themoviedb.org/3/$searchType/$tmdbId/credits?api_key=$tmdbApiKey&language=pt-BR"
                        val creditsRequest = Request.Builder().url(creditsUrl).build()
                        client.newCall(creditsRequest).execute().use { credResponse ->
                            if (credResponse.isSuccessful) {
                                val credBody = credResponse.body?.string() ?: ""
                                val castArray = JSONObject(credBody).optJSONArray("cast")
                                if (castArray != null) {
                                    val castList = mutableListOf<CastMember>()
                                    val count = minOf(castArray.length(), 15)
                                    for (i in 0 until count) {
                                        val member = castArray.getJSONObject(i)
                                        val name = member.optString("name", "")
                                        val character = member.optString("character", "")
                                        val profilePath = member.optString("profile_path", "")
                                        val photoUrl = if (profilePath.isNotEmpty()) {
                                            "https://image.tmdb.org/t/p/w185$profilePath"
                                        } else {
                                            "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80"
                                        }
                                        castList.add(CastMember(name = name, character = character, photoUrl = photoUrl))
                                    }
                                    if (castList.isNotEmpty()) {
                                        return@withContext castList
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error getting real cast from TMDB for ${item.title}", e)
        }

        // Procedural Fallback of 4 prominent actors
        return@withContext listOf(
            CastMember("Christian Bale", "Protagonista", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Margot Robbie", "Co-Protagonista", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80"),
            CastMember("Cillian Murphy", "Mentor", "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Florence Pugh", "Rival", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80")
        )
    }

    suspend fun getRealSeasonsFromTMDB(item: PlaylistItem): List<Season> = withContext(Dispatchers.IO) {
        if (item.type != "series" && item.type != "series_episode") return@withContext emptyList()
        val tmdbApiKey = "355b405bb005248e382a8b400ebab70b"
        val cleanTitle = cleanTitleForSearch(item.cleanTitle ?: item.title)

        // 1. Resolve real series ID for Xtream URL mappings
        val realSeries = verifyAndGetRealPlaylistItem(item)
        val seriesId = if (realSeries != null && realSeries.url.contains("/series/")) {
            try {
                realSeries.url.substringAfterLast("/").substringBefore(".")
            } catch (e: Exception) {
                null
            }
        } else if (item.url.contains("/series/")) {
            try {
                item.url.substringAfterLast("/").substringBefore(".")
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val xtreamEpisodes = if (seriesId != null) {
            getXtreamEpisodesMap(seriesId)
        } else {
            emptyMap()
        }

        try {
            // Search TMDB to find the ID
            val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$tmdbApiKey&query=${java.net.URLEncoder.encode(cleanTitle, "UTF-8")}&language=pt-BR"
            val searchRequest = Request.Builder().url(searchUrl).build()
            client.newCall(searchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val results = JSONObject(body).optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val firstResult = selectBestTmdbResult(results, cleanTitle) ?: return@use
                        val tmdbId = firstResult.optInt("id")

                        // Fetch TV Details to get the list of seasons
                        val detailsUrl = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$tmdbApiKey&language=pt-BR"
                        val detailsRequest = Request.Builder().url(detailsUrl).build()
                        client.newCall(detailsRequest).execute().use { detResponse ->
                            if (detResponse.isSuccessful) {
                                val detBody = detResponse.body?.string() ?: ""
                                val seasonsArray = JSONObject(detBody).optJSONArray("seasons")
                                if (seasonsArray != null) {
                                    val seasonsList = mutableListOf<Season>()
                                    for (s in 0 until seasonsArray.length()) {
                                        val seasonObj = seasonsArray.getJSONObject(s)
                                        val seasonNumber = seasonObj.optInt("season_number", -1)
                                        val seasonName = seasonObj.optString("name", "Temporada $seasonNumber")
                                        if (seasonNumber <= 0 && seasonsArray.length() > 1) {
                                            continue // Skip specials / season 0
                                        }

                                        try {
                                            val seasonDetailsUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$tmdbApiKey&language=pt-BR"
                                            val seasonDetailsRequest = Request.Builder().url(seasonDetailsUrl).build()
                                            client.newCall(seasonDetailsRequest).execute().use { sDetResponse ->
                                                if (sDetResponse.isSuccessful) {
                                                    val sDetBody = sDetResponse.body?.string() ?: ""
                                                    val episodesArray = JSONObject(sDetBody).optJSONArray("episodes")
                                                    if (episodesArray != null) {
                                                        val episodesList = mutableListOf<Episode>()
                                                        for (e in 0 until episodesArray.length()) {
                                                            val epObj = episodesArray.getJSONObject(e)
                                                            val epName = epObj.optString("name", "Episódio ${e + 1}")
                                                            val epOverview = epObj.optString("overview", "Sem sinopse disponível.")
                                                            val runtime = epObj.optInt("runtime", 45)
                                                            val stillPath = epObj.optString("still_path", "")

                                                            val airDateStr = epObj.optString("air_date", "").let { if (it.equals("null", ignoreCase = true)) "" else it }
                                                            var isReleased = true
                                                            var formattedDate: String? = null
                                                            if (airDateStr.isNotEmpty()) {
                                                                try {
                                                                    val sdfTmdb = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                                    val dateObj = sdfTmdb.parse(airDateStr)
                                                                    if (dateObj != null) {
                                                                        val sdfApp = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                                                        formattedDate = sdfApp.format(dateObj)
                                                                        isReleased = dateObj.time <= System.currentTimeMillis()
                                                                    }
                                                                } catch (ex: Exception) {
                                                                    Log.e("IPTVRepository", "Error parsing date: $airDateStr", ex)
                                                                }
                                                            }

                                                            val thumbUrl = if (stillPath.isNotEmpty()) {
                                                                "https://image.tmdb.org/t/p/w300$stillPath"
                                                            } else {
                                                                "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=300&q=80"
                                                            }

                                                            val playUrl = xtreamEpisodes[Pair(seasonNumber, e + 1)]
                                                            episodesList.add(
                                                                Episode(
                                                                    title = epName,
                                                                    duration = "$runtime min",
                                                                    synopsis = epOverview,
                                                                    thumbnailUrl = thumbUrl,
                                                                    releaseDate = if (isReleased) null else formattedDate,
                                                                    isReleased = isReleased,
                                                                    playUrl = playUrl
                                                                )
                                                            )
                                                        }
                                                        if (episodesList.isNotEmpty()) {
                                                            seasonsList.add(Season(name = seasonName, episodes = episodesList))
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("IPTVRepository", "Error fetching season $seasonNumber for ${item.title}", e)
                                        }
                                    }
                                    if (seasonsList.isNotEmpty()) {
                                        return@withContext seasonsList
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error getting real seasons from TMDB for ${item.title}", e)
        }

        // Procedural Fallback if TMDB request fails
        return@withContext generateFallbackSeasons(item, xtreamEpisodes)
    }

    private fun generateFallbackSeasons(item: PlaylistItem, xtreamEpisodes: Map<Pair<Int, Int>, String>): List<Season> {
        val totalSeasons = if (item.id % 2 == 0) 3 else 2
        return List(totalSeasons) { sIdx ->
            val seasonNum = sIdx + 1
            val totalEp = 12
            Season(
                name = "Temporada $seasonNum",
                episodes = List(totalEp) { eIdx ->
                    val epNum = eIdx + 1
                    val playUrl = xtreamEpisodes[Pair(seasonNum, epNum)]
                    Episode(
                        title = "Episódio $epNum: Mistérios Revelados",
                        duration = "45 min",
                        synopsis = "As consequências das escolhas passadas vêm à tona neste capítulo eletrizante de ${item.title}.",
                        thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=300&q=80",
                        releaseDate = null,
                        isReleased = true,
                        playUrl = playUrl
                    )
                }
            )
        }
    }

    suspend fun findRealEpisodeItem(seriesTitle: String, seasonName: String, episodeIndex: Int, episodeTitle: String): PlaylistItem? = withContext(Dispatchers.IO) {
        var allSeriesItems = playlistDao.getItemsByType("series_episode").firstOrNull() ?: emptyList()
        if (allSeriesItems.isEmpty()) {
            allSeriesItems = playlistDao.getItemsByType("series").firstOrNull() ?: emptyList()
        }
        if (allSeriesItems.isEmpty()) return@withContext null

        val cleanSeriesTitle = cleanTitleForSearch(seriesTitle).lowercase()
        val seasonNumMatch = digitsRegex.find(seasonName)
        val seasonNum = seasonNumMatch?.value?.toIntOrNull() ?: 1
        val epNum = episodeIndex + 1

        // Formato de padrões comuns em arquivos de listas IPTV
        val s0e0Pattern = "s%02de%02d".format(seasonNum, epNum) // s01e01
        val s_e_Pattern = "s%de%d".format(seasonNum, epNum)     // s1e1
        val s_space_e_Pattern = "s%d e%d".format(seasonNum, epNum) // s1 e1
        val s0_space_e0_Pattern = "s%02d e%02d".format(seasonNum, epNum) // s01 e01
        val crossPattern = "%dx%02d".format(seasonNum, epNum) // 1x01
        val crossPatternSimple = "%dx%d".format(seasonNum, epNum) // 1x1
        val epWordPattern = "ep %d".format(epNum) // ep 1
        val epWordZeroPattern = "ep %02d".format(epNum) // ep 01
        val epFullWordPattern = "episódio %d".format(epNum) // episódio 1
        val epFullWordZeroPattern = "episódio %02d".format(epNum) // episódio 01

        val candidate = allSeriesItems.firstOrNull { item ->
            val itemTitleLower = item.title.lowercase()
            val containsSeriesName = itemTitleLower.contains(cleanSeriesTitle) ||
                    cleanSeriesTitle.contains(itemTitleLower) ||
                    cleanTitleForSearch(item.title).lowercase().contains(cleanSeriesTitle)

            containsSeriesName && (
                    itemTitleLower.contains(s0e0Pattern) ||
                            itemTitleLower.contains(s_e_Pattern) ||
                            itemTitleLower.contains(s_space_e_Pattern) ||
                            itemTitleLower.contains(s0_space_e0_Pattern) ||
                            itemTitleLower.contains(crossPattern) ||
                            itemTitleLower.contains(crossPatternSimple) ||
                            itemTitleLower.contains("e%02d".format(epNum)) ||
                            (itemTitleLower.contains(epWordPattern) && itemTitleLower.contains("temporada $seasonNum")) ||
                            (itemTitleLower.contains(epWordZeroPattern) && itemTitleLower.contains("temporada $seasonNum")) ||
                            (itemTitleLower.contains(epFullWordPattern) && itemTitleLower.contains("temporada $seasonNum")) ||
                            (itemTitleLower.contains(epFullWordZeroPattern) && itemTitleLower.contains("temporada $seasonNum"))
                    )
        }

        if (candidate != null) {
            Log.d("IPTVRepository", "Found synchronized panel episode item: ${candidate.title} -> ${candidate.url}")
            return@withContext candidate
        }

        val looseCandidate = allSeriesItems.firstOrNull { item ->
            val itemTitleLower = item.title.lowercase()
            val containsSeriesName = itemTitleLower.contains(cleanSeriesTitle) ||
                    cleanSeriesTitle.contains(itemTitleLower)

            containsSeriesName && (
                    itemTitleLower.contains(s0e0Pattern) ||
                            itemTitleLower.contains(s_e_Pattern) ||
                            itemTitleLower.contains(crossPattern) ||
                            itemTitleLower.contains("e%02d".format(epNum)) ||
                            itemTitleLower.contains("ep %d".format(epNum)) ||
                            itemTitleLower.contains("ep %02d".format(epNum)) ||
                            itemTitleLower.contains("ep%d".format(epNum)) ||
                            itemTitleLower.contains("ep%02d".format(epNum))
                    )
        }

        if (looseCandidate != null) {
            Log.d("IPTVRepository", "Found loose synchronized panel episode item: ${looseCandidate.title} -> ${looseCandidate.url}")
            return@withContext looseCandidate
        }

        return@withContext null
    }

    private fun normalizeString(input: String): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        val withoutAccents = normalized.replace(accentRegex, "")
        return withoutAccents.lowercase()
            .replace(specialCharsRegex, " ") // replace special chars with spaces
            .replace(spacesRegex, " ")         // normalize spaces
            .trim()
    }

    private fun getSequelNumbers(s: String): Set<String> {
        return digitsRegex.findAll(s.lowercase())
            .map { it.value }
            .filter { it.length < 4 } // Ignore 4-digit years
            .toSet()
    }

    private fun sequelNumbersMatch(title1: String, title2: String): Boolean {
        val seq1 = getSequelNumbers(title1)
        val seq2 = getSequelNumbers(title2)
        return seq1 == seq2
    }

    private fun cleanIptvTitle(title: String): String {
        val temp = normalizeString(title)
        return temp.replace(keywordsRegex, "").replace(spacesRegex, " ").trim()
    }

    private fun titlesMatchFuzzy(title1: String, title2: String): Boolean {
        val t1 = cleanIptvTitle(title1)
        val t2 = cleanIptvTitle(title2)
        if (t1.isEmpty() || t2.isEmpty()) return false
        if (t1 == t2 || t1.contains(t2) || t2.contains(t1)) {
            return sequelNumbersMatch(title1, title2)
        }

        val stopwords = setOf(
            "filme", "filmes", "movie", "movies", "serie", "series", "season", "temporada",
            "legendado", "dublado", "completo", "hd", "fhd", "4k", "sd", "trailer", "teaser", "oficial",
            "o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "em", "para"
        )

        // Word overlap checking - we must include single-character digits (like "4" or "5") so sequel suffixes are not ignored
        val words1 = t1.split(" ").filter { (it.length >= 2 || it.any { c -> c.isDigit() }) && !stopwords.contains(it) }.toSet()
        val words2 = t2.split(" ").filter { (it.length >= 2 || it.any { c -> c.isDigit() }) && !stopwords.contains(it) }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return false

        val intersection = words1.intersect(words2)
        val minSize = minOf(words1.size, words2.size)
        // If 75% or more of the words in the shorter title match the longer title
        return (intersection.size.toFloat() / minSize.toFloat() >= 0.75f) && sequelNumbersMatch(title1, title2)
    }

    suspend fun getXtreamEpisodesMap(seriesId: String): Map<Pair<Int, Int>, String> = withContext(Dispatchers.IO) {
        val resultMap = mutableMapOf<Pair<Int, Int>, String>()
        val creds = getSavedCredentials()
        val username = creds.first ?: return@withContext emptyMap()
        val password = creds.second ?: return@withContext emptyMap()
        val rawServer = getSavedServerUrl()
        var serverUrl = rawServer.trim()
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrl = "http://$serverUrl"
        }
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length - 1)
        }

        val url = "$serverUrl/player_api.php?username=$username&password=$password&action=get_series_info&series_id=$seriesId"
        Log.d("IPTVRepository", "Fetching Xtream series info from: $url")
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.trim().startsWith("{")) {
                        val jsonObject = JSONObject(bodyStr)
                        val episodesObj = jsonObject.optJSONObject("episodes")
                        if (episodesObj != null) {
                            val keys = episodesObj.keys()
                            while (keys.hasNext()) {
                                val seasonKey = keys.next()
                                val seasonNum = seasonKey.toIntOrNull() ?: continue
                                val epArray = episodesObj.optJSONArray(seasonKey) ?: continue
                                for (i in 0 until epArray.length()) {
                                    val epObj = epArray.getJSONObject(i)
                                    val epId = epObj.optString("id")
                                    val epNumStr = epObj.optString("episode_num")
                                    val epNum = epNumStr.substringBefore(".").toIntOrNull() ?: (i + 1)
                                    val containerExt = epObj.optString("container_extension", "mp4")

                                    if (epId.isNotEmpty()) {
                                        val playUrl = "$serverUrl/series/$username/$password/$epId.$containerExt"
                                        resultMap[Pair(seasonNum, epNum)] = playUrl
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching/parsing Xtream episodes", e)
        }
        return@withContext resultMap
    }

    suspend fun verifyAndGetRealPlaylistItem(item: PlaylistItem): PlaylistItem? = withContext(Dispatchers.IO) {
        if (item.url != "http://streatv.elementfx.com/get.php" && item.url.isNotBlank()) {
            return@withContext item
        }

        val cleanTitle = cleanTitleForSearch(item.title).lowercase()
        if (cleanTitle.isBlank()) return@withContext null

        val allItems = playlistDao.getAllItems().firstOrNull() ?: emptyList()

        // 1. Try fuzzy match first
        val match = allItems.firstOrNull { dbItem ->
            dbItem.url != "http://streatv.elementfx.com/get.php" &&
                    dbItem.url.isNotBlank() &&
                    dbItem.type == item.type &&
                    titlesMatchFuzzy(dbItem.title, item.title) &&
                    sequelNumbersMatch(dbItem.title, item.title)
        }
        if (match != null) return@withContext match

        // 2. Fallback to substring matching
        val matchFallback = allItems.firstOrNull { dbItem ->
            dbItem.url != "http://streatv.elementfx.com/get.php" &&
                    dbItem.url.isNotBlank() &&
                    dbItem.type == item.type &&
                    sequelNumbersMatch(dbItem.title, item.title) &&
                    (cleanTitleForSearch(dbItem.title).lowercase() == cleanTitle ||
                            dbItem.title.lowercase().contains(cleanTitle) ||
                            cleanTitle.contains(dbItem.title.lowercase()))
        }
        return@withContext matchFallback
    }

    suspend fun startBackgroundMetadataPrefetch() = withContext(Dispatchers.IO) {
        Log.d("IPTVRepository", "Starting background metadata pre-fetch and cache routine")
        try {
            val allItems = playlistDao.getAllItems().firstOrNull() ?: return@withContext
            val pendingEnrichment = allItems.filter { item ->
                (item.type == "series" || item.type == "movie") &&
                        (item.backdropUrl.isNullOrEmpty() || item.description.isNullOrEmpty())
            }.take(50)

            Log.d("IPTVRepository", "Found ${pendingEnrichment.size} items pending TMDB background pre-fetch enrichment")
            pendingEnrichment.forEach { item ->
                try {
                    enrichItemWithTMDB(item)
                    kotlinx.coroutines.delay(200) // Polite delay to stay within rate limits
                } catch (e: Exception) {
                    Log.e("IPTVRepository", "Failed background TMDB enrichment for ${item.title}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error in background metadata prefetch", e)
        }
    }

    companion object {
        private val accentRegex = Regex("\\p{M}")
        private val specialCharsRegex = Regex("[^a-z0-9\\s]")
        private val spacesRegex = Regex("\\s+")
        private val keywordsRegex = Regex("\\b(?:dublado|legendado|dual|audio|multiaudio|4k|fhd|hdtv|hd|sd|uhd|hevc|h264|h265|x264|x265|completo|temporada|s0|s1|s2|s3|s4|s5|ep|episodio|capitulo|mkv|mp4|avi)\\b")

        private val searchPatternsCombined = Regex("\\[.*?\\]|\\(.*?\\)|(?i)1080p|(?i)720p|(?i)2160p|(?i)4k|(?i)fhd|(?i)hd|(?i)sd|(?i)uhd|(?i)dublado|(?i)legendado|(?i)dual|(?i)audio|(?i)h264|(?i)h265|(?i)hevc|(?i)x264|(?i)s\\d{1,2}e\\d{1,2}|(?i)s\\d{1,2} e\\d{1,2}|(?i)temporada|(?i)ep\\s?\\d{1,3}|(?i)episodio\\s?\\d{1,3}")
        private val dashEndRegex = Regex(" - $")
        private val hyphenEndRegex = Regex("-$")
        private val colonEndRegex = Regex(" :$")
        private val digitsRegex = Regex("\\d+")
    }
}
