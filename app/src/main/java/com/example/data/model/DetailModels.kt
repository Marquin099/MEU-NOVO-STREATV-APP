package com.example.data.model

import java.io.Serializable

data class CastMember(
    val name: String,
    val character: String,
    val photoUrl: String
) : Serializable

data class Episode(
    val title: String,
    val duration: String,
    val synopsis: String,
    val thumbnailUrl: String,
    val releaseDate: String? = null, // Format: "dd/MM/yyyy" or null if released
    val isReleased: Boolean = true,
    val playUrl: String? = null
) : Serializable

data class Season(
    val name: String,
    val episodes: List<Episode>
) : Serializable
