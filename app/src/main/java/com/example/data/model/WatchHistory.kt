package com.example.data.model

import androidx.room.Entity

@Entity(
    tableName = "watch_history",
    primaryKeys = ["profileId", "itemId"]
)
data class WatchHistory(
    val profileId: Int,
    val itemId: Int,
    val position: Long,
    val duration: Long,
    val lastWatched: Long
)
