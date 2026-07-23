package com.example.data.util

import com.example.data.model.PlaylistItem
import java.io.BufferedReader
import java.io.StringReader

object M3UParser {
    fun parse(m3uContent: String): List<PlaylistItem> {
        val reader = BufferedReader(StringReader(m3uContent))
        val items = mutableListOf<PlaylistItem>()
        var currentLine = reader.readLine()

        var extinfLine: String? = null

        while (currentLine != null) {
            val line = currentLine.trim()
            if (line.startsWith("#EXTINF:")) {
                extinfLine = line
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                if (extinfLine != null) {
                    val item = parseItem(extinfLine, line)
                    items.add(item)
                    extinfLine = null
                }
            }
            currentLine = reader.readLine()
        }
        return items
    }

    private fun parseItem(extinf: String, url: String): PlaylistItem {
        val logoUrl = extractAttribute(extinf, "tvg-logo") ?: extractAttribute(extinf, "logo")
        val groupName = extractAttribute(extinf, "group-title") ?: "Geral"

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
        val isVideoFile = urlUpper.contains(".MP4")

        val hasSeriesIndicator = urlUpper.contains("/SERIES/") ||
                urlUpper.contains("/SERIE/") ||
                urlUpper.contains("/EPISODES/") ||
                urlUpper.contains("/EPISODE/") ||
                (groupUpper.contains("SÉRIE") && !groupUpper.contains("CANAIS") && !groupUpper.contains("TV") && !groupUpper.contains("LIVE")) ||
                (groupUpper.contains("SERIES") && !groupUpper.contains("CANAIS") && !groupUpper.contains("TV") && !groupUpper.contains("LIVE")) ||
                groupUpper.contains("EPISODIO") ||
                groupUpper.contains("TEMPORADA") ||
                groupUpper.contains("EPISÓDIO") ||
                title.uppercase().contains("S0") ||
                title.uppercase().contains("S1") ||
                title.uppercase().contains("E0") ||
                title.uppercase().contains("E1") ||
                title.uppercase().contains("EP.") ||
                title.uppercase().contains("EPISÓDIO")

        val hasMovieIndicator = urlUpper.contains("/MOVIE/") ||
                urlUpper.contains("/MOVIES/") ||
                urlUpper.contains("/VOD/") ||
                (groupUpper.contains("FILME") && !groupUpper.contains("CANAIS") && !groupUpper.contains("TV") && !groupUpper.contains("LIVE")) ||
                (groupUpper.contains("MOVIE") && !groupUpper.contains("CANAIS") && !groupUpper.contains("TV") && !groupUpper.contains("LIVE")) ||
                groupUpper.contains("VOD") ||
                (groupUpper.contains("CINEMA") && !groupUpper.contains("CANAIS") && !groupUpper.contains("TV") && !groupUpper.contains("LIVE")) ||
                groupUpper.contains("LANÇAMENTOS") ||
                groupUpper.contains("ANIMACAO") ||
                groupUpper.contains("DOCUMENTARIOS")

        val isLiveIndicator = groupUpper.contains("CANAIS") ||
                groupUpper.contains("LIVE") ||
                groupUpper.contains("TV AO VIVO") ||
                groupUpper.contains("TELEVISÃO") ||
                groupUpper.contains("TELEVISAO") ||
                groupUpper.contains("NOTICIAS") ||
                groupUpper.contains("ESPORTES") ||
                groupUpper.contains("NEWS") ||
                groupUpper.contains("SPORTS") ||
                urlUpper.contains("/LIVE/") ||
                urlUpper.contains(".M3U8") ||
                urlUpper.contains(".TS")

        val type = when {
            hasSeriesIndicator && urlUpper.contains(".MP4") -> "series"
            (hasMovieIndicator || isVideoFile) && urlUpper.contains(".MP4") -> "movie"
            else -> "live"
        }

        return PlaylistItem(
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
}
