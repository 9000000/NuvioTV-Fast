package com.nuvio.tv.features.livetv

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds archive (catch-up) URLs for common provider formats:
 * default / append (template in catchup-source), shift, flussonic and xtream codes.
 */
object CatchupUrlBuilder {

    fun isAvailable(channel: LiveTvChannel, program: EpgProgram, nowMs: Long): Boolean =
        isAvailable(channel.catchup, program.startMs, nowMs)

    fun isAvailable(catchup: CatchupInfo?, programStartMs: Long, nowMs: Long): Boolean {
        if (catchup == null) return false
        if (programStartMs >= nowMs) return false
        val maxAgeMs = catchup.days.coerceAtLeast(1) * 24L * 60 * 60 * 1000
        return nowMs - programStartMs <= maxAgeMs
    }

    fun build(
        liveUrl: String,
        catchup: CatchupInfo,
        startMs: Long,
        stopMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        preferHls: Boolean = false,
        serverTimezone: String? = null
    ): String? {
        val correctionMs = (catchup.correctionHours * 3_600_000).toLong()
        val startMsC = startMs + correctionMs
        val start = startMsC / 1000
        val end = (stopMs + correctionMs) / 1000
        val now = nowMs / 1000
        val duration = (end - start).coerceAtLeast(60)
        val xtreamGuess = { xtream(liveUrl, startMsC, duration, preferHls, serverTimezone) }
        val zone = serverTimezone?.takeIf { it.isNotBlank() }?.let { TimeZone.getTimeZone(it) }
        val fillIn = { t: String -> fill(t, start, end, now, duration, zone) }
        return when (catchup.type) {
            "append" -> catchup.source?.let { liveUrl + fillIn(it) }
            "default" -> {
                val src = catchup.source
                when {
                    src.isNullOrBlank() -> xtreamGuess() ?: shift(liveUrl, start, now)
                    src.startsWith("http", ignoreCase = true) -> fillIn(src)
                    else -> liveUrl + fillIn(src)
                }
            }
            "shift", "timeshift" -> if (catchup.source.isNullOrBlank()) xtreamGuess() ?: shift(liveUrl, start, now) else shift(liveUrl, start, now)
            "flussonic", "flussonic-hls", "flussonic-ts", "fs" -> flussonic(liveUrl, start, duration)
            "xc", "xtream" -> xtream(liveUrl, startMsC, duration, preferHls, serverTimezone)
            else -> catchup.source?.let { fillIn(it) }
        }
    }

    private fun shift(url: String, start: Long, now: Long): String {
        val sep = if (url.contains('?')) '&' else '?'
        return "$url${sep}utc=$start&lutc=$now"
    }

    private fun flussonic(url: String, start: Long, duration: Long): String? {
        val m = Regex("""^(https?://[^/]+)/(.*)/([^/?]*)(\?.*)?$""").find(url) ?: return null
        val host = m.groupValues[1]
        val stream = m.groupValues[2]
        val file = m.groupValues[3]
        val query = m.groupValues[4]
        return if (file.endsWith(".m3u8") || file.isEmpty()) {
            val base = file.removeSuffix(".m3u8").ifEmpty { "index" }
            "$host/$stream/$base-$start-$duration.m3u8$query"
        } else {
            "$host/$stream/timeshift_abs-$start.ts$query"
        }
    }

    private fun xtream(url: String, startMs: Long, duration: Long, preferHls: Boolean, serverTimezone: String?): String? {
        val m = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(\.[a-z0-9]+)?(\?.*)?$""", RegexOption.IGNORE_CASE)
            .find(url) ?: return null
        val query = m.groupValues[6]
        val (host, user, pass, id) = m.destructured
        val ext = if (preferHls) ".m3u8" else m.groupValues[5].ifEmpty { ".ts" }
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        serverTimezone?.takeIf { it.isNotBlank() }?.let { fmt.timeZone = TimeZone.getTimeZone(it) }
        val minutes = (duration / 60).coerceAtLeast(1)
        return "$host/timeshift/$user/$pass/$minutes/${fmt.format(Date(startMs))}/$id$ext$query"
    }

    fun alternatives(
        url: String,
        serverTimezone: String?,
        showMinutes: Long? = null,
        airedMinutes: Long? = null
    ): List<String> {
        val path = url.substringBefore('?')
        val query = url.substring(path.length)
        val m = Regex("""^(https?://[^/]+)/timeshift/([^/]+)/([^/]+)/(\d+)/([0-9-]+:[0-9-]+)/(\d+)(\.[a-z0-9]+)?$""", RegexOption.IGNORE_CASE)
            .find(path) ?: return listOfNotNull(tsFallback(url))
        val (host, user, pass, minutes, stamp, id) = m.destructured
        val ext = m.groupValues[7]
        val out = LinkedHashSet<String>()
        if (ext.equals(".m3u8", true)) out += "$host/timeshift/$user/$pass/$minutes/$stamp/$id.ts$query"
        else out += "$host/timeshift/$user/$pass/$minutes/$stamp/$id.m3u8$query"
        val aired = airedMinutes?.coerceAtLeast(1)?.takeIf { it < (minutes.toLongOrNull() ?: 0) }
        if (aired != null) {
            out += "$host/timeshift/$user/$pass/$aired/$stamp/$id${ext.ifEmpty { ".ts" }}$query"
            out += "$host/timeshift/$user/$pass/$aired/$stamp/$id${if (ext.equals(".m3u8", true)) ".ts" else ".m3u8"}$query"
        }
        val short = showMinutes?.coerceAtLeast(1)?.takeIf { it < (minutes.toLongOrNull() ?: 0) }
        if (short != null) {
            out += "$host/timeshift/$user/$pass/$short/$stamp/$id${ext.ifEmpty { ".ts" }}$query"
            out += "$host/timeshift/$user/$pass/$short/$stamp/$id${if (ext.equals(".m3u8", true)) ".ts" else ".m3u8"}$query"
        }
        out += "$host/streaming/timeshift.php?username=$user&password=$pass&stream=$id&start=$stamp&duration=$minutes"
        val serverZone = serverTimezone?.takeIf { it.isNotBlank() }?.let { TimeZone.getTimeZone(it) }
        if (serverZone == null) {
            val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
            fmt.timeZone = TimeZone.getDefault()
            val startMs = runCatching { fmt.parse(stamp)?.time }.getOrNull()
            if (startMs != null) {
                val other = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(startMs))
                if (other != stamp) out += "$host/timeshift/$user/$pass/$minutes/$other/$id${ext.ifEmpty { ".ts" }}$query"
            }
        }
        out.remove(url)
        return out.toList()
    }

    fun tsFallback(url: String): String? {
        val path = url.substringBefore('?')
        if (!path.contains("/timeshift/") || !path.endsWith(".m3u8", ignoreCase = true)) return null
        return path.dropLast(".m3u8".length) + ".ts" + url.substring(path.length)
    }

    internal fun fill(
        template: String, start: Long, end: Long, now: Long, duration: Long,
        serverZone: TimeZone? = null
    ): String {
        var out = template
        val xtreamLink = template.contains("/timeshift/", ignoreCase = true) || template.contains("timeshift.php", ignoreCase = true)
        val formatZone = if (serverZone != null && xtreamLink) serverZone else TimeZone.getTimeZone("UTC")
        out = Regex("""\$?\{(utc|start|utcend|end|lutc|now|timestamp):([^}]+)\}""").replace(out) { m ->
            val epoch = when (m.groupValues[1]) {
                "utcend", "end" -> end
                "lutc", "now", "timestamp" -> now
                else -> start
            }
            formatPattern(m.groupValues[2], epoch, formatZone)
        }
        out = Regex("""\$?\{duration:(\d+)\}""").replace(out) { m ->
            val divisor = m.groupValues[1].toLongOrNull()?.coerceAtLeast(1) ?: 1
            (duration / divisor).toString()
        }
        out = Regex("""\$?\{offset:(\d+)\}""").replace(out) { m ->
            val divisor = m.groupValues[1].toLongOrNull()?.coerceAtLeast(1) ?: 1
            ((now - start) / divisor).toString()
        }
        val simple = mapOf(
            "utc" to start, "start" to start, "timestamp" to now,
            "utcend" to end, "end" to end,
            "lutc" to now, "now" to now,
            "duration" to duration, "offset" to (now - start)
        )
        simple.forEach { (k, v) ->
            out = out.replace("\${$k}", v.toString()).replace("{$k}", v.toString())
        }
        val cal = java.util.Calendar.getInstance(serverZone ?: TimeZone.getDefault()).apply { timeInMillis = start * 1000 }
        out = out
            .replace("{Y}", "%04d".format(cal.get(java.util.Calendar.YEAR)))
            .replace("{m}", "%02d".format(cal.get(java.util.Calendar.MONTH) + 1))
            .replace("{d}", "%02d".format(cal.get(java.util.Calendar.DAY_OF_MONTH)))
            .replace("{H}", "%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY)))
            .replace("{M}", "%02d".format(cal.get(java.util.Calendar.MINUTE)))
            .replace("{S}", "%02d".format(cal.get(java.util.Calendar.SECOND)))
        return out
    }

    private fun formatPattern(pattern: String, epochSec: Long, zone: TimeZone): String {
        val javaPattern = buildString {
            pattern.forEach { c ->
                append(
                    when (c) {
                        'Y' -> "yyyy"; 'm' -> "MM"; 'd' -> "dd"
                        'H' -> "HH"; 'M' -> "mm"; 'S' -> "ss"
                        else -> if (c.isLetter()) "'$c'" else c.toString()
                    }
                )
            }
        }
        return runCatching {
            SimpleDateFormat(javaPattern, Locale.US).apply { timeZone = zone }
                .format(Date(epochSec * 1000))
        }.getOrDefault(epochSec.toString())
    }
}
