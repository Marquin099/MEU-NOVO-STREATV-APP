package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.UserProfile
import com.example.data.model.PlaylistItem
import com.example.data.model.FavoriteItem
import com.example.data.model.WatchHistory

@Database(
    entities = [UserProfile::class, PlaylistItem::class, FavoriteItem::class, WatchHistory::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Increase SQLite CursorWindow size to 100MB to support large IPTV playlists
                try {
                    val field = android.database.CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
                    field.isAccessible = true
                    field.set(null, 100 * 1024 * 1024) // 100MB
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Failed to override CursorWindow size", e)
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "streatv_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
