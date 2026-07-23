package com.example.data.database

import androidx.room.*
import com.example.data.model.PlaylistItem
import com.example.data.model.WatchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(watchHistory: WatchHistory)

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND itemId = :itemId LIMIT 1")
    suspend fun getWatchHistoryItem(profileId: Int, itemId: Int): WatchHistory?

    @Query("DELETE FROM watch_history WHERE profileId = :profileId AND itemId = :itemId")
    suspend fun deleteWatchHistory(profileId: Int, itemId: Int)

    @Query("""
        SELECT p.* FROM playlist_items p 
        INNER JOIN watch_history w ON p.id = w.itemId 
        WHERE w.profileId = :profileId
        ORDER BY w.lastWatched DESC
    """)
    fun getContinueWatching(profileId: Int): Flow<List<PlaylistItem>>

    @Query("DELETE FROM watch_history WHERE profileId = :profileId")
    suspend fun clearWatchHistoryForProfile(profileId: Int)
}
