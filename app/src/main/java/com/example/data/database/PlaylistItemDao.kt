package com.example.data.database

import androidx.room.*
import com.example.data.model.PlaylistItem
import com.example.data.model.FavoriteItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistItemDao {
    @Query("SELECT * FROM playlist_items")
    fun getAllItems(): Flow<List<PlaylistItem>>

    @Query("""
        SELECT * FROM playlist_items 
        WHERE type = :type 
        ORDER BY 
            CASE WHEN :type = 'live' THEN id ELSE -id END ASC
    """)
    fun getItemsByType(type: String): Flow<List<PlaylistItem>>

    @Query("SELECT COUNT(*) FROM playlist_items")
    suspend fun getItemsCount(): Int

    @Query("SELECT url FROM playlist_items")
    suspend fun getAllUrls(): List<String>

    @Query("SELECT title FROM playlist_items WHERE title IN (:titles)")
    suspend fun getExistingTitles(titles: List<String>): List<String>

    @Query("""
        SELECT * FROM playlist_items 
        WHERE type IN ('movie', 'series')
        ORDER BY CASE WHEN (groupName LIKE '%Tendências%' OR groupName LIKE '%Tendencias%') THEN 0 ELSE 1 END, id DESC
        LIMIT 10
    """)
    fun getBannerItems(): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): PlaylistItem?

    @Query("SELECT * FROM playlist_items WHERE groupName = :groupName ORDER BY id ASC")
    fun getItemsByGroupName(groupName: String): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE groupName IN ('Tendências', 'Tendencias', 'Os Mais Populares', 'Últimos Trailers') ORDER BY id ASC")
    suspend fun getMainSectionsItems(): List<PlaylistItem>

    @Query("DELETE FROM playlist_items WHERE groupName IN ('Tendências', 'Tendencias', 'Os Mais Populares', 'Últimos Trailers')")
    suspend fun clearMainSectionsItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlaylistItem>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistItem>)

    @Query("DELETE FROM playlist_items")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<PlaylistItem>) {
        clearAll()
        items.chunked(500).forEach { chunk ->
            insertAll(chunk)
        }
    }

    @Transaction
    suspend fun replaceMainSectionsItems(items: List<PlaylistItem>) {
        clearMainSectionsItems()
        insertAll(items)
    }

    @Query("DELETE FROM playlist_items WHERE type = :type")
    suspend fun clearAllByType(type: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteItem)

    @Query("DELETE FROM favorite_items WHERE profileId = :profileId AND itemId = :itemId")
    suspend fun deleteFavorite(profileId: Int, itemId: Int)

    @Query("""
        SELECT p.* FROM playlist_items p 
        INNER JOIN favorite_items f ON p.id = f.itemId 
        WHERE f.profileId = :profileId AND p.type = :type
    """)
    fun getFavoriteItems(profileId: Int, type: String): Flow<List<PlaylistItem>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_items WHERE profileId = :profileId AND itemId = :itemId)")
    suspend fun isFavorite(profileId: Int, itemId: Int): Boolean

    @Query("""
        SELECT groupName FROM playlist_items 
        WHERE type = :type 
        GROUP BY groupName 
        ORDER BY 
            CASE WHEN :type = 'movie' THEN -MIN(id) ELSE MIN(id) END ASC
    """)
    fun getCategoriesByType(type: String): Flow<List<String>>

    @Query("""
        SELECT * FROM playlist_items 
        WHERE type = :type AND groupName = :groupName 
        ORDER BY 
            CASE WHEN :type = 'live' THEN id ELSE -id END ASC
    """)
    fun getItemsByTypeAndCategory(type: String, groupName: String): Flow<List<PlaylistItem>>

    @Query("""
        UPDATE playlist_items 
        SET type = 'live' 
        WHERE type = 'movie' 
          AND url NOT LIKE '%.mp4%' 
          AND url NOT LIKE '%.mkv%' 
          AND url NOT LIKE '%.avi%' 
          AND url NOT LIKE '%.m4v%' 
          AND url NOT LIKE '%.mov%' 
          AND url NOT LIKE '%.flv%' 
          AND url NOT LIKE '%.webm%' 
          AND url NOT LIKE '%/movie/%' 
          AND url NOT LIKE '%/series/%'
    """)
    suspend fun fixLiveChannelTypes()
}
