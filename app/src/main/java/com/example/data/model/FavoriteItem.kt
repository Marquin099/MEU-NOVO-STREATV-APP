package com.example.data.model

import androidx.room.Entity

@Entity(
    tableName = "favorite_items",
    primaryKeys = ["profileId", "itemId"]
)
data class FavoriteItem(
    val profileId: Int,
    val itemId: Int
)
