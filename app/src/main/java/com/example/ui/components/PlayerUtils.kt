package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun playInVLC(context: Context, url: String, title: String) {
    try {
        val uri = Uri.parse(url)
        val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            setPackage("org.videolan.vlc")
            putExtra("title", title)
            putExtra("from_start", false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(vlcIntent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Instale o VLC Player para usar esta opção!",
            Toast.LENGTH_LONG
        ).show()
        
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.videolan.vlc"))
            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(marketIntent)
        } catch (anfe: ActivityNotFoundException) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.videolan.vlc"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }
}

fun cleanServerUrl(serverUrl: String): String {
    if (serverUrl.isBlank()) return ""
    var cleaned = serverUrl.trim()
    val removeSuffixes = listOf("/get.php", "/player_api.php", "/xmltv.php")
    for (ending in removeSuffixes) {
        if (cleaned.endsWith(ending)) {
            cleaned = cleaned.substring(0, cleaned.length - ending.length)
        }
    }
    if (cleaned.endsWith("/")) {
        cleaned = cleaned.substring(0, cleaned.length - 1)
    }
    return cleaned
}

fun rewriteUrlWithActiveCredentials(
    originalUrl: String,
    activeServerUrl: String,
    activeUser: String,
    activePass: String
): String {
    if (originalUrl.isBlank()) return ""
    
    // If it's a sample URL or mock URL, return it as-is
    if (originalUrl.contains("googleapis.com") || originalUrl.contains("sample") || originalUrl.contains("BigBuckBunny")) {
        return originalUrl
    }
    
    try {
        val uri = Uri.parse(originalUrl)
        val pathSegments = uri.pathSegments
        
        val cleanServer = cleanServerUrl(activeServerUrl)
        val query = uri.query
        val suffix = if (!query.isNullOrBlank()) "?$query" else ""
        
        // Pattern 1: /movie/username/password/stream_id.ext -> 4 segments
        // Pattern 2: /series/username/password/stream_id.ext -> 4 segments
        // Pattern 3: /live/username/password/stream_id.ext -> 4 segments
        if (pathSegments.size >= 4) {
            val type = pathSegments[0].lowercase()
            if (type == "movie" || type == "movies" || type == "series" || type == "live") {
                var streamIdWithExt = pathSegments[3]
                if ((type == "movie" || type == "movies") && streamIdWithExt.endsWith(".ts", ignoreCase = true)) {
                    streamIdWithExt = streamIdWithExt.substring(0, streamIdWithExt.length - 3) + ".mp4"
                }
                return "$cleanServer/$type/$activeUser/$activePass/$streamIdWithExt$suffix"
            }
        }
        
        // Pattern 4: /username/password/stream_id -> exactly 3 segments
        if (pathSegments.size == 3) {
            val streamIdWithExt = pathSegments[2]
            return "$cleanServer/$activeUser/$activePass/$streamIdWithExt$suffix"
        }
    } catch (e: Exception) {
        // Fallback
    }
    
    return originalUrl
}
