package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "playlist_items")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val logoUrl: String? = null,
    val groupName: String,
    val type: String, // "live", "movie", "series"
    val backdropUrl: String? = null,
    val titleLogoUrl: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val cleanTitle: String? = null,
    val numSeasons: Int? = null,
    val numEpisodes: Int? = null
) : Serializable
