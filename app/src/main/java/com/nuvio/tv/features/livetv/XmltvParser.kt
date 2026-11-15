package com.nuvio.tv.features.livetv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class XmltvChannel(
    val id: String,
    val displayNames: List<String>,
    val icon: String?
)

data class XmltvResult(
    val channels: Map<String, XmltvChannel>,
    /** Programs keyed by XMLTV channel id, sorted by start time. */
    val programs: Map<String, List<EpgProgram>>,
    /** Reading stopped early here (a broken file): what was read before it is kept. */
    val error: String? = null,
    /**
     * Every channel id the guide's shows mention, kept or not (just the ids).
     */
    val programChannelIds: Set<String> = emptySet()
)


/**
 * Streaming XMLTV parser. Large provider guides are often 50–200 MB, so only
 * programs inside [windowStartMs, windowEndMs] and (once the channel list has
 * been read) only channels that match the playlist are kept in memory.
 */
object XmltvParser {

    fun parse(
        input: InputStream,
        windowStartMs: Long,
        windowEndMs: Long,
        keepUnlisted: ((String) -> Boolean)? = null,
        onPrograms: ((String, List<EpgProgram>) -> Unit)? = null,
        selectChannels: (Map<String, XmltvChannel>) -> Set<String>?
    ): XmltvResult {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        // Forgiving mode, like other IPTV apps
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setInput(input, null)

        val channels = LinkedHashMap<String, XmltvChannel>()
        val programs = HashMap<String, MutableList<EpgProgram>>()
        val providerIdentities = ProgrammeIdentityPool()
        var wanted: Set<String>? = null
        var wantedResolved = false
        val seenIds = HashSet<String>()
        val unlistedAnswers = HashMap<String, Boolean>()
        var bufId: String? = null
        var buf = ArrayList<EpgProgram>()
        fun flush() {
            val id = bufId ?: return
            if (buf.isNotEmpty()) onPrograms?.invoke(id, orderedPrograms(buf))
            buf = ArrayList()
            bufId = null
        }

        var error: String? = null
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> readChannel(parser)?.let { channels[it.id] = it }
                        "programme" -> {
                            if (!wantedResolved) {
                                wanted = selectChannels(channels)
                                wantedResolved = true
                            }
                            val channelId = parser.getAttributeValue(null, "channel")
                            if (channelId != null) seenIds += channelId
                            val keep = channelId != null && when {
                                wanted != null -> channelId in wanted
                                keepUnlisted != null -> unlistedAnswers.getOrPut(channelId) { keepUnlisted(channelId) }
                                else -> true
                            }
                            if (keep) {
                                val program = readProgramme(parser, windowStartMs, windowEndMs, providerIdentities)
                                if (program != null) {
                                    if (onPrograms != null) {
                                        if (bufId != channelId) { flush(); bufId = channelId }
                                        buf.add(program)
                                    } else {
                                        programs.getOrPut(channelId) { ArrayList() }.add(program)
                                    }
                                }
                            } else {
                                skipProgramme(parser)
                            }
                            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "programme") {
                                event = parser.eventType
                                continue
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            error = "line ${runCatching { parser.lineNumber }.getOrDefault(-1)}: " +
                (e.message?.take(140) ?: e.javaClass.simpleName)
            if (!wantedResolved) wanted = null
        }
        runCatching { flush() }
        val sorted = programs.mapValues { (_, list) -> orderedPrograms(list) }
        return XmltvResult(channels, sorted, error, seenIds)
    }

    private fun orderedPrograms(programs: MutableList<EpgProgram>): List<EpgProgram> {
        for (i in 1 until programs.size) {
            if (programs[i - 1].startMs > programs[i].startMs) {
                programs.sortBy { it.startMs }
                break
            }
        }
        return programs
    }

    private fun readChannel(parser: XmlPullParser): XmltvChannel? {
        val id = parser.getAttributeValue(null, "id") ?: run { skip(parser); return null }
        val names = mutableListOf<String>()
        var icon: String? = null
        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "display-name" -> readText(parser).takeIf { it.isNotBlank() }?.let { names.add(it.trim()) }
                    "icon" -> {
                        icon = parser.getAttributeValue(null, "src")
                        skip(parser)
                    }
                    else -> skip(parser)
                }
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        return XmltvChannel(id, names, icon)
    }

    private fun readProgramme(
        parser: XmlPullParser,
        windowStart: Long,
        windowEnd: Long,
        providerIdentities: ProgrammeIdentityPool
    ): EpgProgram? {
        val providerProgramId = parser.getAttributeValue(null, "stalker-id")
        val providerSourceIdentity = providerIdentities.share(parser.getAttributeValue(null, "stalker-source"))
        val start = parseTime(parser.getAttributeValue(null, "start"))
        val stop = parseTime(parser.getAttributeValue(null, "stop"))
        if (start == null || stop == null || stop <= windowStart || start >= windowEnd) {
            skipProgramme(parser)
            return null
        }
        var title: String? = null
        var desc: String? = null
        var category: String? = null
        var episode: String? = null
        var subTitle: String? = null
        var icon: String? = null
        var date: String? = null
        var people: ArrayList<String>? = null
        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") break
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> { val t = readText(parser); if (title == null) title = t }
                    "sub-title" -> { val t = readText(parser); if (subTitle == null) subTitle = t }
                    "desc" -> { val t = readText(parser); if (desc == null) desc = t }
                    "category" -> { val t = readText(parser); if (category == null) category = t }
                    "episode-num" -> {
                        val system = parser.getAttributeValue(null, "system")
                        val t = readText(parser)
                        if (system == "onscreen" || episode == null) episode = formatEpisode(system, t)
                    }
                    "icon" -> { icon = parser.getAttributeValue(null, "src"); skip(parser) }
                    "date" -> { val t = readText(parser); if (date == null) date = t }
                    "credits" -> readCredits(parser, people ?: ArrayList<String>().also { people = it })
                    else -> skip(parser)
                }
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        val year = date?.trim()?.takeIf { it.length == 4 }?.toIntOrNull()?.takeIf { it in 1900..2100 }
            ?: yearIn(title) ?: yearIn(desc)
        val fullDesc = when {
            subTitle.isNullOrBlank() -> desc?.takeIf { it.isNotBlank() }
            desc.isNullOrBlank() -> subTitle
            else -> "$subTitle\n$desc"
        }
        return EpgProgram(
            startMs = start,
            stopMs = stop,
            title = title?.trim().orEmpty().ifBlank { "No information" }.let { if (it.length <= 80) it.intern() else it },
            description = fullDesc?.let { if (it.length > 800) it.take(800).trimEnd() + "…" else it },
            category = category?.trim()?.intern(),
            episode = episode?.intern(),
            icon = icon?.intern(),
            year = year,
            people = people?.take(12) ?: emptyList(),
            providerProgramId = providerProgramId,
            providerSourceIdentity = providerSourceIdentity
        )
    }

    private val yearInParens = Regex("""\((19\d{2}|20\d{2})\)""")

    private fun yearIn(text: String?): Int? =
        text?.let { yearInParens.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    private fun readCredits(parser: XmlPullParser, out: MutableList<String>) {
        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "actor", "director" -> readText(parser).trim().takeIf { it.isNotEmpty() }?.let { out += it }
                    else -> skip(parser)
                }
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
    }

    private fun formatEpisode(system: String?, raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (system == "xmltv_ns") {
            val parts = text.split('.')
            val season = parts.getOrNull(0)?.substringBefore('/')?.trim()?.toIntOrNull()
            val ep = parts.getOrNull(1)?.substringBefore('/')?.trim()?.toIntOrNull()
            return when {
                season != null && season >= 100 -> null
                season != null && ep != null -> "S${season + 1} E${ep + 1}"
                ep != null -> "E${ep + 1}"
                else -> null
            }
        }
        if (system != null && system != "onscreen") return null
        return cleanEpisode(text)
    }

    private val episodeIdDigits = Regex("""\d{5,}""")
    private val episodeSeason = Regex("""(?i)\bS(?:eason)?\s*(\d+)""")

    fun cleanEpisode(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || t.length > 24) return null
        val letters = t.count { it.isLetter() }
        val digits = t.count { it.isDigit() }
        if (digits == 0) return t
        if (letters == 0 && digits > 4) return null
        if (episodeIdDigits.containsMatchIn(t)) return null
        episodeSeason.find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { if (it >= 100) return null }
        return t
    }

    private fun readText(parser: XmlPullParser): String {
        var first: String? = null
        var joined: StringBuilder? = null
        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.TEXT) {
                val text = parser.text
                val previous = first
                if (previous == null) first = text
                else {
                    val output = joined ?: StringBuilder(previous.length + text.length).append(previous).also { joined = it }
                    output.append(text)
                }
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        return joined?.toString() ?: first.orEmpty()
    }

    private fun skipProgramme(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> if (parser.name == "programme") return else depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    internal fun parseTime(value: String?): Long? {
        if (value == null) return null
        var begin = 0
        var end = value.length
        while (begin < end && value[begin].isWhitespace()) begin++
        while (end > begin && value[end - 1].isWhitespace()) end--
        var digitsEnd = begin
        while (digitsEnd < end && value[digitsEnd].isDigit()) digitsEnd++
        if (digitsEnd - begin < 12) return null
        val year = decimalPair(value, begin) * 100 + decimalPair(value, begin + 2)
        val month = decimalPair(value, begin + 4)
        val day = decimalPair(value, begin + 6)
        val hour = decimalPair(value, begin + 8)
        val minute = decimalPair(value, begin + 10)
        val second = if (digitsEnd - begin >= 14) decimalPair(value, begin + 12) else 0
        if (month !in 1..12 || day !in 1..31) return null
        var ms = (daysFromCivil(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second) * 1_000L
        var offset = digitsEnd
        while (offset < end && value[offset].isWhitespace()) offset++
        if (end - offset >= 5 && (value[offset] == '+' || value[offset] == '-')) {
            val sign = if (value[offset] == '-') -1 else 1
            val oh = signedPairOrZero(value, offset + 1)
            val om = signedPairOrZero(value, offset + 3)
            ms -= sign * (oh * 3_600_000L + om * 60_000L)
        }
        return ms
    }

    private fun decimalDigit(c: Char): Int = if (c in '0'..'9') c - '0' else Character.digit(c, 10)

    private fun decimalPair(value: String, at: Int): Int = decimalDigit(value[at]) * 10 + decimalDigit(value[at + 1])

    private fun signedPairOrZero(value: String, at: Int): Int {
        val first = value[at]
        val second = decimalDigit(value[at + 1])
        if (second < 0) return 0
        if (first == '+' || first == '-') return if (first == '-') -second else second
        val leading = decimalDigit(first)
        return if (leading < 0) 0 else leading * 10 + second
    }
}
