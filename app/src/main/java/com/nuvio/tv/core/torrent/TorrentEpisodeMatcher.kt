package com.nuvio.tv.core.torrent

import java.util.Locale

/**
 * Intelligent file matcher and selector for torrent streams (especially TorrServer multi-season
 * packs and TV series torrents).
 *
 * Accurately extracts season and episode metadata from directory structures, multi-season ranges,
 * filenames, and anime episode numbering formats.
 */
object TorrentEpisodeMatcher {

    val VIDEO_EXTENSIONS = setOf(
        "mkv", "mp4", "avi", "webm", "ts", "m4v", "mov", "wmv", "flv", "m2ts", "iso"
    )

    private data class ScoredFile<T>(
        val file: T,
        val score: Int
    )

    fun isVideoFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    /**
     * Checks if a path indicates junk, sample, extras, featurettes, or trailer files.
     */
    fun isJunkOrExtra(normalizedPath: String, targetSeason: Int?): Boolean {
        val lower = normalizedPath.lowercase()
        val segments = lower.split('/').filter { it.isNotBlank() }
        val filename = segments.lastOrNull() ?: ""
        val folders = if (segments.size > 1) segments.dropLast(1) else emptyList()

        // Check for sample
        if (lower.contains("sample") && !lower.contains("sampler")) {
            val sampleRegex = Regex("""(?i)(?:^|[\s._\-\[/])sample(?:$|[\s._\-\]/])""")
            if (sampleRegex.containsMatchIn(lower)) return true
        }

        // Check for extras/bonus/featurettes/trailers in folder or filename
        val extraPatterns = listOf(
            "extras", "extra", "featurette", "featurettes", "bonus", "behind the scenes",
            "deleted scenes", "interview", "trailer", "promo", "teaser"
        )
        if (folders.any { folder -> extraPatterns.any { pattern -> folder.contains(pattern) } }) {
            return true
        }
        if (extraPatterns.any { pattern ->
            Regex("""(?i)(?:^|[\s._\-\[(])$pattern(?:$|[\s._\-\])])""").containsMatchIn(filename)
        }) {
            return true
        }

        // Check for specials (Season 0) when targetSeason != 0
        if (targetSeason != 0) {
            val isSpecialFolder = folders.any {
                it == "specials" || it == "special" || it == "season 0" || it == "season 00" ||
                    it == "s0" || it == "s00"
            }
            if (isSpecialFolder) return true
            if (filename.startsWith("s00e", ignoreCase = true) || filename.startsWith("s0e", ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    // Multi-season range in folder name (e.g., S01-S05, Seasons 1-5, Season 1 to 5, S1~S8)
    private val seasonRangeFolderRegex = Regex(
        """(?i)(?:season|seasons|saison|temporada|staffel|series|mùa|s)s?\s*0*(\d+)\s*(?:-|–|—|~|to)\s*(?:season|seasons|saison|temporada|staffel|series|mùa|s)?\s*0*(\d+)"""
    )

    // Standalone single season folder: "Season 2", "Season 02", "S2", "S02", "Series 2", "Mùa 2"
    private val standaloneSeasonFolderRegex = Regex(
        """(?i)^(?:season|saison|temporada|staffel|series|mùa|s)\s*[._-]?\s*0*(\d+)$"""
    )

    // Embedded single season in folder name: "Game of Thrones Season 2", "Friends Season 05 [1080p]", "Show S02"
    private val embeddedSeasonFolderRegex = Regex(
        """(?i)(?<![a-z0-9])(?:season|saison|temporada|staffel|series|mùa)\s*[._-]?\s*0*(\d+)(?![0-9])"""
    )
    private val embeddedShortSeasonFolderRegex = Regex(
        """(?i)(?<![a-z0-9])s\s*0*(\d+)(?![a-z0-9])"""
    )

    fun extractFolderSeason(folderName: String): Int? {
        val clean = folderName.trim()
        if (seasonRangeFolderRegex.containsMatchIn(clean)) {
            return null // It's a range of seasons, not a single season folder
        }
        standaloneSeasonFolderRegex.matchEntire(clean)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        embeddedSeasonFolderRegex.find(clean)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        embeddedShortSeasonFolderRegex.find(clean)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        return null
    }

    /**
     * Checks if any ancestor folder is a season range that contains targetSeason.
     */
    fun folderContainsSeasonRange(folders: List<String>, targetSeason: Int): Boolean {
        for (folder in folders) {
            val match = seasonRangeFolderRegex.find(folder)
            if (match != null) {
                val start = match.groupValues[1].toIntOrNull() ?: continue
                val end = match.groupValues[2].toIntOrNull() ?: continue
                if (targetSeason in minOf(start, end)..maxOf(start, end)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Detects specific single season from the closest folder up the hierarchy.
     */
    fun detectFolderSeason(folders: List<String>): Int? {
        // Search from closest folder (parent) to root
        return folders.asReversed().firstNotNullOfOrNull { extractFolderSeason(it) }
    }

    // S02E05, S2E5, S02.E05, s02_e05, S02xE05
    private val filenameStandardSeasonEpRegex = Regex(
        """(?i)(?<![a-z0-9])s0*(\d+)[._\s-]*[ex]0*(\d+)(?![0-9])"""
    )

    // S02E01-E02, S02E01-02, S02E01~E03
    private val filenameStandardSeasonEpRangeRegex = Regex(
        """(?i)(?<![a-z0-9])s0*(\d+)[._\s-]*[ex]0*(\d+)\s*(?:-|–|—|~|&|[ex])\s*(?:[ex])?0*(\d+)(?![0-9])"""
    )

    // 2x05, 02x05
    private val filenameXSeasonEpRegex = Regex(
        """(?i)(?<![a-z0-9])0*(\d+)[xX]0*(\d+)(?![0-9])"""
    )

    // 2x01-2x02, 2x01-02
    private val filenameXSeasonEpRangeRegex = Regex(
        """(?i)(?<![a-z0-9])0*(\d+)[xX]0*(\d+)\s*(?:-|–|—|~|&)\s*(?:0*\d+[xX])?0*(\d+)(?![0-9])"""
    )

    // Season 2 Episode 5, Season.02.Episode.05
    private val filenameWordsSeasonEpRegex = Regex(
        """(?i)(?<![a-z0-9])(?:season|saison|temporada|staffel|series|mùa)\s*0*(\d+)[._\s-]+(?:episode|ep|e)\s*0*(\d+)(?![0-9])"""
    )

    // Episode prefixed: E05, Ep 05, Ep.05, Episode 05, Episode 5
    private val filenameEpPrefixedRegex = Regex(
        """(?i)(?<![a-z0-9])(?:episode|ep|e)[._\s-]*0*(\d+)(?![0-9])"""
    )

    // Episode prefixed range: E01-E02, Ep.01-02, Episode 01-03
    private val filenameEpPrefixedRangeRegex = Regex(
        """(?i)(?<![a-z0-9])(?:episode|ep|e)[._\s-]*0*(\d+)\s*(?:-|–|—|~|&|e)\s*(?:episode|ep|e)?\s*0*(\d+)(?![0-9])"""
    )

    // Anime / standalone episode regex: e.g. One Piece - 1050, 0500, 24
    private val animeAbsoluteEpRegex = Regex(
        """(?i)(?<!\d)(?:e|ep|ep\.|episode[\s._-]*)?0*(\d+)(?:v\d+)?(?![pPkK\d]|bit|fps)(?=[\s._\-\])]|$)"""
    )

    fun extractCandidateNumbers(name: String, targetEpisode: Int?): List<Int> {
        val matches = Regex("""(?i)(?<![a-z0-9])0*(\d+)(?![a-z0-9])""").findAll(name)
        val result = mutableListOf<Int>()
        for (m in matches) {
            val numStr = m.groupValues[1]
            val num = numStr.toIntOrNull() ?: continue
            val endIdx = m.range.last + 1
            val startIdx = m.range.first

            val remainingAfter = name.substring(endIdx)
            val before = name.substring(0, startIdx)

            // Skip resolutions: 1080p, 720p, 480p, 2160p, 4k
            if (remainingAfter.startsWith("p", ignoreCase = true) ||
                remainingAfter.startsWith("k", ignoreCase = true) ||
                remainingAfter.startsWith("bit", ignoreCase = true) ||
                remainingAfter.startsWith("fps", ignoreCase = true)
            ) {
                continue
            }
            if (num in setOf(1080, 720, 480, 576, 2160, 4320)) {
                continue
            }

            // Skip video codecs: x264, h264, x265, h265
            if (before.endsWith("x", ignoreCase = true) || before.endsWith("h", ignoreCase = true)) {
                continue
            }

            // Skip audio channels: 5.1, 7.1, 2.0
            if (remainingAfter.startsWith(".1") || remainingAfter.startsWith(".0")) {
                continue
            }

            // Skip 4-digit years (1900..2099) unless targetEpisode is explicitly that number
            if (num in 1900..2099 && num != targetEpisode) {
                continue
            }

            result.add(num)
        }
        return result
    }

    /**
     * Calculates match confidence score for a file path against target season and episode.
     * Returns 0 for mismatch or non-video, > 0 for valid match (higher score = better match).
     */
    fun calculateMatchScore(
        path: String,
        targetSeason: Int?,
        targetEpisode: Int?
    ): Int {
        val normalized = path.replace('\\', '/').trim().trimStart('/')
        if (normalized.isBlank()) return 0
        if (!isVideoFile(normalized)) return 0
        if (isJunkOrExtra(normalized, targetSeason)) return 0

        val segments = normalized.split('/').filter { it.isNotBlank() }
        val filename = segments.lastOrNull().orEmpty()
        val nameWithoutExt = filename.substringBeforeLast('.')
        val folders = if (segments.size > 1) segments.dropLast(1) else emptyList()

        // If no target season or episode requested (e.g. movie)
        if (targetSeason == null && targetEpisode == null) {
            return 100
        }

        val detectedFolderSeason = detectFolderSeason(folders)

        // 1. Check filename for combined Season + Episode
        // 1A. Standard SxxExx
        filenameStandardSeasonEpRegex.find(nameWithoutExt)?.let { match ->
            val fileSeason = match.groupValues[1].toIntOrNull()
            val fileEp = match.groupValues[2].toIntOrNull()
            if (fileSeason != null && fileEp != null) {
                if (targetSeason != null && fileSeason != targetSeason) return 0
                if (targetEpisode != null) {
                    return if (fileEp == targetEpisode) 1000 else 0
                }
                return if (fileSeason == targetSeason) 800 else 0
            }
        }

        // 1B. Standard SxxExx range: S02E01-E02
        filenameStandardSeasonEpRangeRegex.find(nameWithoutExt)?.let { match ->
            val fileSeason = match.groupValues[1].toIntOrNull()
            val startEp = match.groupValues[2].toIntOrNull()
            val endEp = match.groupValues[3].toIntOrNull()
            if (fileSeason != null && startEp != null && endEp != null) {
                if (targetSeason != null && fileSeason != targetSeason) return 0
                if (targetEpisode != null) {
                    val range = minOf(startEp, endEp)..maxOf(startEp, endEp)
                    return if (targetEpisode in range) 950 else 0
                }
                return if (fileSeason == targetSeason) 750 else 0
            }
        }

        // 1C. 2x05 notation
        filenameXSeasonEpRegex.find(nameWithoutExt)?.let { match ->
            val fileSeason = match.groupValues[1].toIntOrNull()
            val fileEp = match.groupValues[2].toIntOrNull()
            if (fileSeason != null && fileEp != null) {
                if (targetSeason != null && fileSeason != targetSeason) return 0
                if (targetEpisode != null) {
                    return if (fileEp == targetEpisode) 1000 else 0
                }
                return if (fileSeason == targetSeason) 800 else 0
            }
        }

        // 1D. 2x01-02 range notation
        filenameXSeasonEpRangeRegex.find(nameWithoutExt)?.let { match ->
            val fileSeason = match.groupValues[1].toIntOrNull()
            val startEp = match.groupValues[2].toIntOrNull()
            val endEp = match.groupValues[3].toIntOrNull()
            if (fileSeason != null && startEp != null && endEp != null) {
                if (targetSeason != null && fileSeason != targetSeason) return 0
                if (targetEpisode != null) {
                    val range = minOf(startEp, endEp)..maxOf(startEp, endEp)
                    return if (targetEpisode in range) 950 else 0
                }
                return if (fileSeason == targetSeason) 750 else 0
            }
        }

        // 1E. Words: Season 2 Episode 5
        filenameWordsSeasonEpRegex.find(nameWithoutExt)?.let { match ->
            val fileSeason = match.groupValues[1].toIntOrNull()
            val fileEp = match.groupValues[2].toIntOrNull()
            if (fileSeason != null && fileEp != null) {
                if (targetSeason != null && fileSeason != targetSeason) return 0
                if (targetEpisode != null) {
                    return if (fileEp == targetEpisode) 1000 else 0
                }
                return if (fileSeason == targetSeason) 800 else 0
            }
        }

        // Check if filename specifies another season explicitly without episode (e.g. "Show S01 - Title")
        val otherSeasonInFilename = Regex("""(?i)\b[sS]0*(\d+)\b""").find(nameWithoutExt)?.let {
            it.groupValues[1].toIntOrNull()
        }
        if (otherSeasonInFilename != null && targetSeason != null && otherSeasonInFilename != targetSeason) {
            return 0
        }

        // 2. Check folder season conflict
        if (targetSeason != null && detectedFolderSeason != null && detectedFolderSeason != targetSeason) {
            return 0 // Explicitly in another season's folder!
        }

        val folderMatchesTargetSeason = targetSeason != null && detectedFolderSeason == targetSeason

        if (targetEpisode == null) {
            // Only season requested
            return if (folderMatchesTargetSeason) 500 else 0
        }

        // 3. Filename Episode with prefix (E05, Ep 05, Episode 5)
        filenameEpPrefixedRangeRegex.find(nameWithoutExt)?.let { match ->
            val startEp = match.groupValues[1].toIntOrNull()
            val endEp = match.groupValues[2].toIntOrNull()
            if (startEp != null && endEp != null) {
                val range = minOf(startEp, endEp)..maxOf(startEp, endEp)
                if (targetEpisode in range) {
                    return if (folderMatchesTargetSeason) 940 else 840
                } else {
                    return 0 // Explicitly another episode range
                }
            }
        }

        val epPrefixedMatches = filenameEpPrefixedRegex.findAll(nameWithoutExt).mapNotNull {
            it.groupValues[1].toIntOrNull()
        }.toList()
        if (epPrefixedMatches.isNotEmpty()) {
            if (targetEpisode in epPrefixedMatches) {
                return if (folderMatchesTargetSeason) 930 else 830
            } else {
                return 0 // Specified a different episode with Ep prefix
            }
        }

        // 4. Compact 3 or 4 digit notation (e.g., 205 for S02E05)
        if (targetSeason != null && targetSeason in 1..99 && targetEpisode in 1..99) {
            val compact3or4 = "${targetSeason}${String.format(Locale.US, "%02d", targetEpisode)}"
            if (compact3or4.toIntOrNull() !in 1920..2030) {
                val compactRegex = Regex("""(?i)(?<![a-z0-9])${compact3or4}(?![a-z0-9])""")
                if (compactRegex.containsMatchIn(nameWithoutExt)) {
                    return 850
                }
            }
        }

        // 5. Standalone episode number in filename
        val candidateNumbers = extractCandidateNumbers(nameWithoutExt, targetEpisode)
        if (candidateNumbers.isNotEmpty()) {
            if (targetEpisode in candidateNumbers) {
                if (folderMatchesTargetSeason) {
                    return 900
                }
                if (targetSeason == 1 || targetSeason == null) {
                    return 800 // Anime or single-season
                }
                if (folderContainsSeasonRange(folders, targetSeason)) {
                    return 750
                }
                return 600
            } else {
                // Filename contains specific episode numbers, but NOT targetEpisode
                // If folder matched season, this is definitely another episode in that season
                if (folderMatchesTargetSeason) {
                    return 0
                }
            }
        }

        // 6. Anime pattern fallback (for One Piece, Conan etc. with arbitrary prefixes)
        if (targetSeason == 1 || targetSeason == null) {
            val animeMatch = animeAbsoluteEpRegex.findAll(nameWithoutExt).mapNotNull {
                it.groupValues[1].toIntOrNull()
            }.toList()
            if (targetEpisode in animeMatch) {
                return 800
            }
        }

        return 0
    }

    fun matchesEpisode(path: String, season: Int?, episode: Int?): Boolean {
        if (episode == null) return false
        return calculateMatchScore(path, season, episode) > 0
    }

    /**
     * Selects the best matching file from a collection of torrent files.
     */
    fun <T> selectBestMatchingFile(
        files: List<T>,
        requestedIdx: Int?,
        targetSeason: Int?,
        targetEpisode: Int?,
        getPath: (T) -> String,
        getId: (T) -> Int,
        getLength: (T) -> Long
    ): T? {
        if (files.isEmpty()) return null

        val videoFiles = files.filter { isVideoFile(getPath(it)) }
        val candidates = videoFiles.ifEmpty { files }
        val nonJunk = candidates.filterNot { isJunkOrExtra(getPath(it), targetSeason) }
        val pool = nonJunk.ifEmpty { candidates }

        // If neither season nor episode requested (movie/standalone), honor requestedIdx or pick largest
        if (targetSeason == null && targetEpisode == null) {
            if (requestedIdx != null) {
                pool.firstOrNull { getId(it) == requestedIdx }?.let { return it }
                files.firstOrNull { getId(it) == requestedIdx }?.let { return it }
            }
            return pool.maxByOrNull { getLength(it) } ?: pool.firstOrNull()
        }

        // Score all candidate files
        val scored = pool.map { file ->
            val score = calculateMatchScore(
                path = getPath(file),
                targetSeason = targetSeason,
                targetEpisode = targetEpisode
            )
            ScoredFile(file = file, score = score)
        }

        val matched = scored.filter { it.score > 0 }

        if (matched.isNotEmpty()) {
            // If requestedIdx points to one of the matched files with high confidence, prefer it
            if (requestedIdx != null) {
                val requestedMatch = matched.firstOrNull { getId(it.file) == requestedIdx }
                val maxScore = matched.maxOf { it.score }
                // Only accept requestedIdx if its score is top tier (within 100 points of maxScore)
                if (requestedMatch != null && requestedMatch.score >= (maxScore - 100)) {
                    return requestedMatch.file
                }
            }

            // Otherwise pick the file with the highest match score.
            // On tie: prefer larger file size, then shorter path.
            val best = matched.sortedWith(
                compareByDescending<ScoredFile<T>> { it.score }
                    .thenByDescending { getLength(it.file) }
                    .thenBy { getPath(it.file).length }
            ).first()

            return best.file
        }

        // Fallback if no files matched patterns:
        if (requestedIdx != null) {
            pool.firstOrNull { getId(it) == requestedIdx }?.let { return it }
            files.firstOrNull { getId(it) == requestedIdx }?.let { return it }
        }

        return pool.maxByOrNull { getLength(it) } ?: pool.firstOrNull()
    }

    /**
     * Overload for TorrServerRemoteFile.
     */
    fun selectBestMatchingFileId(
        files: List<TorrServerRemoteFile>,
        requestedIdx: Int?,
        targetSeason: Int?,
        targetEpisode: Int?
    ): Int? {
        return selectBestMatchingFile(
            files = files,
            requestedIdx = requestedIdx,
            targetSeason = targetSeason,
            targetEpisode = targetEpisode,
            getPath = { it.path },
            getId = { it.id },
            getLength = { it.length }
        )?.id
    }
}
