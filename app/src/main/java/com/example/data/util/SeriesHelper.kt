package com.example.data.util

import com.example.data.model.PlaylistItem

val bracketsAndTagsRegex = listOf(
    Regex("\\[.*?\\]"),
    Regex("\\(.*?\\)")
)

val qualityAndFlagsRegex = listOf(
    Regex("(?i)\\b1080p\\b"), Regex("(?i)\\b720p\\b"), Regex("(?i)\\b2160p\\b"), Regex("(?i)\\b4k\\b"), Regex("(?i)\\bfhd\\b"), Regex("(?i)\\bhd\\b"), Regex("(?i)\\bsd\\b"), Regex("(?i)\\buhd\\b"),
    Regex("(?i)\\bdublado\\b"), Regex("(?i)\\blegendado\\b"), Regex("(?i)\\bdual\\b"), Regex("(?i)\\baudio\\b"), Regex("(?i)\\bh264\\b"), Regex("(?i)\\bh265\\b"), Regex("(?i)\\bhevc\\b"), Regex("(?i)\\bx264\\b")
)

val seasonEpPatternsRegex = listOf(
    Regex("(?i)s\\d{1,2}\\s?e\\d{1,2}"),        // s01e01, s01 e01
    Regex("(?i)\\d{1,2}x\\d{1,2}"),             // 1x01
    Regex("(?i)\\be\\d{1,2}\\b"),               // e01, e1 (as isolated word)
    Regex("(?i)temporada\\s?\\d{1,2}"),         // temporada 1, temporada01
    Regex("(?i)\\bep\\s?\\d{1,2}\\b"),          // ep 01, ep 1
    Regex("(?i)\\bepisódio\\s?\\d{1,2}\\b"),    // episódio 1
    Regex("(?i)\\bepisodio\\s?\\d{1,2}\\b"),    // episodio 1
    Regex("(?i)\\bep\\d{1,2}\\b"),              // ep01, ep1
    Regex("(?i)\\bcapítulo\\s?\\d+"),           // capítulo 1
    Regex("(?i)\\bcapitulo\\s?\\d+"),           // capitulo 1
    Regex("(?i)\\bcap\\.?\\s?\\d+"),            // cap. 1, cap 1
    Regex("(?i)\\bpart\\s?\\d+"),               // part 1
    Regex("(?i)\\bparte\\s?\\d+"),              // parte 1
    Regex("(?i)\\b\\d+\\s?ª\\s?temporada"),     // 1ª temporada
    Regex("(?i)\\b\\d+\\s?ª\\s?temp"),          // 1ª temp
    Regex("(?i)\\btemp\\.?\\s?\\d+"),           // temp. 1, temp 1
    Regex("(?i)\\bep\\.?\\s?\\d+"),             // ep. 1, ep 1
    Regex("(?i)\\bepisódio\\s?\\d+"),           // episódio 1
    Regex("(?i)\\bepisodio\\s?\\d+"),           // episodio 1
    Regex("(?i)\\s*-\\s*\\d+$"),                // - 01 at the end
    Regex("\\s+\\d+$")                          // trailing space and digit(s) (episode number)
)

val multipleSpacesRegex = Regex("\\s+")
val trailingHyphenSpaceRegex = Regex(" - $")
val trailingHyphenRegex = Regex("-$")
val trailingColonSpaceRegex = Regex(" :$")
val trailingColonRegex = Regex(":$")
val trailingDotRegex = Regex("\\.$")

fun PlaylistItem.cleanSeriesName(): String {
    var clean = title
    // Remove tags like [DUBLADO], (720p), etc.
    for (regex in bracketsAndTagsRegex) {
        clean = clean.replace(regex, "")
    }
    
    // Remove quality and flags
    for (regex in qualityAndFlagsRegex) {
        clean = clean.replace(regex, "")
    }
    
    clean = clean.trim()

    // Robust Regex to match and remove episode/season patterns and anything after them
    // This cleans up "S01E01", "S1E1", "S01 E01", "S01 - E01", "T01E01", "T1:E1", "T1 E1", "Temporada XX", "Temp XX", "Episódio XX", "Ep XX", "Capítulo XX", etc.
    val patternsToTruncate = listOf(
        Regex("(?i)\\s*[-–—:|•·/]*\\s*\\b(s\\d{1,2}\\s?e\\d{1,2}|s\\d{1,2}\\s?-\\s?e\\d{1,2}|t\\d{1,2}\\s?e\\d{1,2}|t\\d{1,2}\\s?:\\s?e\\d{1,2}|s\\d{1,2}\\b|e\\d{1,2}\\b|\\d{1,2}x\\d{1,2})\\b.*"),
        Regex("(?i)\\s*[-–—:|•·/]*\\s*\\b(temporada|temp|season|capítulo|capitulo|cap|episódio|episodio|ep|t)\\s*\\.?\\s*\\d+.*"),
        Regex("(?i)\\s*[-–—:|•·/]*\\s*\\bpart\\s*\\d+.*"),
        Regex("(?i)\\s*[-–—:|•·/]*\\s*\\bparte\\s*\\d+.*"),
        Regex("(?i)\\s*[-–—:|•·/]*\\s*\\d+\\s*ª\\s*(temporada|temp).*"),
        Regex("(?i)\\s*[-–—:|•·/]+\\s*\\d+\\s*$"),
        Regex("\\s+\\d+\\s*$")
    )

    // Find the earliest match of any truncate pattern and truncate everything after it
    var earliestMatchIndex = -1
    for (regex in patternsToTruncate) {
        val matchResult = regex.find(clean)
        if (matchResult != null) {
            val index = matchResult.range.first
            if (earliestMatchIndex == -1 || index < earliestMatchIndex) {
                earliestMatchIndex = index
            }
        }
    }

    if (earliestMatchIndex != -1) {
        clean = clean.substring(0, earliestMatchIndex).trim()
    } else {
        // Fallback for episode titles without explicit S01E01/numbers, e.g. "Trying - Wood Scott", "Trying - Toscana"
        val delimiters = listOf(" - ", " : ", " | ")
        var candidate: String? = null
        for (delim in delimiters) {
            if (clean.contains(delim)) {
                val prefix = clean.substringBefore(delim).trim()
                if (prefix.length >= 2) {
                    candidate = prefix
                    break
                }
            }
        }
        if (candidate != null) {
            clean = candidate
        } else {
            for (regex in seasonEpPatternsRegex) {
                clean = clean.replace(regex, "")
                clean = clean.trim()
            }
        }
    }

    // Clean remaining trailing junk like trailing hyphens, colons, dots, brackets, or special characters
    val trailingJunk = Regex("(?i)\\s*[-–—:|•·/.,_+\\s]*$")
    clean = clean.replace(trailingJunk, "").trim()
    clean = clean.replace(multipleSpacesRegex, " ").trim()
    
    return if (clean.isBlank() || clean.length < 2) title else clean
}

val forbiddenKeywords = listOf(
    "dontgohome",
    "laestavoce",
    "theresyou"
)

fun isForbiddenTitle(title: String, cleanTitle: String? = null): Boolean {
    fun normalize(s: String): String {
        val unaccented = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return unaccented.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    val normTitle = normalize(title)
    val normClean = if (cleanTitle != null) normalize(cleanTitle) else ""

    if (forbiddenKeywords.any { normTitle.contains(it) || (normClean.isNotEmpty() && normClean.contains(it)) }) {
        return true
    }

    val forbiddenRaw = listOf("don't go home", "dont go home", "don’t go home", "lá está você", "lá esta voce", "la esta voce")
    return forbiddenRaw.any { forbidden ->
        title.contains(forbidden, ignoreCase = true) || (cleanTitle != null && cleanTitle.contains(forbidden, ignoreCase = true))
    }
}

fun groupSeriesList(list: List<PlaylistItem>): List<PlaylistItem> {
    if (list.isEmpty()) return emptyList()
    
    // Process type == "series" items first so that parent series entries take priority for IDs
    val sortedInput = list.sortedByDescending { it.type == "series" }

    val groupedMap = java.util.LinkedHashMap<String, PlaylistItem>()
    for (item in sortedInput) {
        if (isForbiddenTitle(item.title, item.cleanTitle)) continue

        val cleanedTitle = item.cleanSeriesName().trim()
        if (cleanedTitle.isBlank()) continue

        val normalizedGroup = item.groupName.trim()
        val key = normalizedGroup.lowercase() + ":::" + cleanedTitle.lowercase()

        val existing = groupedMap[key]
        if (existing == null) {
            groupedMap[key] = item.copy(
                title = cleanedTitle,
                cleanTitle = cleanedTitle,
                type = "series",
                groupName = normalizedGroup
            )
        } else {
            // Prefer the item with type == "series" if existing isn't, otherwise existing
            val existingIsSeries = existing.type == "series"
            val itemIsSeries = item.type == "series"
            
            val existingEnriched = !existing.titleLogoUrl.isNullOrEmpty() || !existing.backdropUrl.isNullOrEmpty() || (!existing.description.isNullOrEmpty() && existing.description != "Sem sinopse disponível.")
            val itemEnriched = !item.titleLogoUrl.isNullOrEmpty() || !item.backdropUrl.isNullOrEmpty() || (!item.description.isNullOrEmpty() && item.description != "Sem sinopse disponível.")

            val baseItem = when {
                existingIsSeries && !itemIsSeries -> existing
                !existingIsSeries && itemIsSeries -> item
                !existingEnriched && itemEnriched -> item
                else -> existing
            }

            val mergedMaxId = maxOf(existing.id, item.id)

            val mergedBackdrop = existing.backdropUrl?.takeIf { it.isNotEmpty() }
                ?: item.backdropUrl?.takeIf { it.isNotEmpty() }
            val mergedLogo = existing.logoUrl?.takeIf { it.isNotEmpty() }
                ?: item.logoUrl?.takeIf { it.isNotEmpty() }
            val mergedTitleLogo = existing.titleLogoUrl?.takeIf { it.isNotEmpty() }
                ?: item.titleLogoUrl?.takeIf { it.isNotEmpty() }
            val mergedDesc = existing.description?.takeIf { it.isNotEmpty() && it != "Sem sinopse disponível." }
                ?: item.description?.takeIf { it.isNotEmpty() && it != "Sem sinopse disponível." }
            val mergedYear = existing.year?.takeIf { it.isNotEmpty() }
                ?: item.year?.takeIf { it.isNotEmpty() }
            val mergedRating = existing.rating?.takeIf { it.isNotEmpty() && it != "0.0" }
                ?: item.rating?.takeIf { it.isNotEmpty() && it != "0.0" }
            val mergedGenre = existing.genre?.takeIf { it.isNotEmpty() }
                ?: item.genre?.takeIf { it.isNotEmpty() }

            groupedMap[key] = baseItem.copy(
                id = mergedMaxId,
                title = cleanedTitle,
                cleanTitle = cleanedTitle,
                type = "series",
                groupName = normalizedGroup,
                logoUrl = mergedLogo ?: baseItem.logoUrl,
                backdropUrl = mergedBackdrop ?: baseItem.backdropUrl,
                titleLogoUrl = mergedTitleLogo ?: baseItem.titleLogoUrl,
                logo = mergedTitleLogo ?: mergedLogo ?: baseItem.logo,
                description = mergedDesc ?: baseItem.description,
                year = mergedYear ?: baseItem.year,
                rating = mergedRating ?: baseItem.rating,
                genre = mergedGenre ?: baseItem.genre
            )
        }
    }
    
    return ArrayList(groupedMap.values)
}
