package com.nuvio.tv.features.livetv

import java.util.Calendar

/**
 * Tự động trích xuất và sinh lịch phát sóng (EPG) thông minh cho những kênh
 * không có dữ liệu XMLTV (như các kênh trực tiếp thể thao, link m3u TTTT, bóng đá, livestream sự kiện).
 */
object SyntheticEpgGenerator {
    // Match "15:00 15/11" or "02:30 27/10" or "18:00 15-11"
    private val TIME_DATE_COLON = Regex("""(\d{1,2}):(\d{2})\s+(\d{1,2})[/.-](\d{1,2})""")

    // Match "15h00 15/11" or "21h 15/11" or "19h30 15/11"
    private val TIME_DATE_H = Regex("""(\d{1,2})[hH](\d{2})?\s+(\d{1,2})[/.-](\d{1,2})""")

    // Match "15:00" or "20:30"
    private val TIME_ONLY_COLON = Regex("""(\d{1,2}):(\d{2})""")

    // Match "20h30" or "21h"
    private val TIME_ONLY_H = Regex("""(\d{1,2})[hH](\d{2})?""")

    // Lược bỏ các thẻ định dạng stream ở cuối tên kênh như [hls], [flv], [FHD], [HD]
    private val STREAM_TAGS_REGEX = Regex("""(?i)\s*\[(hls|flv|fhd|hd|sd|4k|raw)\]""")

    data class ParsedTime(
        val startMs: Long,
        val durationMs: Long = 135 * 60 * 1000L // 2 giờ 15 phút cho 1 trận đấu/sự kiện
    )

    fun cleanChannelTitle(name: String): String {
        return name.replace(STREAM_TAGS_REGEX, "").trim()
    }

    fun parseEventTime(name: String, nowMs: Long = System.currentTimeMillis()): ParsedTime? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val currentYear = cal.get(Calendar.YEAR)

        // 1. Match HH:mm dd/MM
        TIME_DATE_COLON.find(name)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val day = match.groupValues[3].toIntOrNull() ?: return@let
            val month = (match.groupValues[4].toIntOrNull() ?: return@let) - 1
            if (hour in 0..23 && minute in 0..59 && day in 1..31 && month in 0..11) {
                val eventCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return ParsedTime(eventCal.timeInMillis)
            }
        }

        // 2. Match HHh[mm] dd/MM
        TIME_DATE_H.find(name)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            val day = match.groupValues[3].toIntOrNull() ?: return@let
            val month = (match.groupValues[4].toIntOrNull() ?: return@let) - 1
            if (hour in 0..23 && minute in 0..59 && day in 1..31 && month in 0..11) {
                val eventCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return ParsedTime(eventCal.timeInMillis)
            }
        }

        // 3. Match HH:mm (ngày hôm nay)
        TIME_ONLY_COLON.find(name)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (hour in 0..23 && minute in 0..59) {
                val eventCal = Calendar.getInstance().apply {
                    timeInMillis = nowMs
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return ParsedTime(eventCal.timeInMillis)
            }
        }

        // 4. Match HHh[mm] (ngày hôm nay)
        TIME_ONLY_H.find(name)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            if (hour in 0..23 && minute in 0..59) {
                val eventCal = Calendar.getInstance().apply {
                    timeInMillis = nowMs
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return ParsedTime(eventCal.timeInMillis)
            }
        }

        return null
    }

    fun generate(channel: LiveTvChannel, nowMs: Long = System.currentTimeMillis()): List<EpgProgram> {
        val name = channel.name.trim()
        if (name.isBlank()) return emptyList()

        val parsedTime = parseEventTime(name, nowMs)
        val cleanName = cleanChannelTitle(name)
        val groupName = channel.group?.trim().takeIf { !it.isNullOrBlank() } ?: "Trực tiếp"

        if (parsedTime != null) {
            val eventStart = parsedTime.startMs
            val eventStop = eventStart + parsedTime.durationMs
            val programs = mutableListOf<EpgProgram>()

            // 1. Khung giờ trước trận đấu (Pre-event)
            programs.add(
                EpgProgram(
                    startMs = eventStart - 24 * 3600 * 1000L,
                    stopMs = eventStart,
                    title = "Sắp diễn ra: $cleanName",
                    description = "Trận đấu/sự kiện sẽ bắt đầu vào giờ đã lên lịch. Phát sóng trên $groupName.",
                    category = "Thể thao"
                )
            )

            // 2. Khung giờ diễn ra trận đấu (Main event / Live)
            programs.add(
                EpgProgram(
                    startMs = eventStart,
                    stopMs = eventStop,
                    title = cleanName,
                    description = "🔴 Trực tiếp: $cleanName · Nhóm: $groupName",
                    category = "Thể thao"
                )
            )

            // 3. Khung giờ sau trận đấu (Post-event)
            programs.add(
                EpgProgram(
                    startMs = eventStop,
                    stopMs = eventStop + 24 * 3600 * 1000L,
                    title = "Đã phát sóng: $cleanName",
                    description = "Sự kiện đã kết thúc. Theo dõi các sự kiện tiếp theo trên $groupName.",
                    category = "Thể thao"
                )
            )

            return programs
        } else {
            // Không có giờ cụ thể: Tạo các block chương trình 3 tiếng bao phủ từ -24h đến +24h
            val blockDuration = 3 * 3600 * 1000L
            val baseTime = (nowMs / blockDuration) * blockDuration
            val programs = mutableListOf<EpgProgram>()

            for (i in -8..8) {
                val bStart = baseTime + i * blockDuration
                val bStop = bStart + blockDuration
                programs.add(
                    EpgProgram(
                        startMs = bStart,
                        stopMs = bStop,
                        title = cleanName,
                        description = "Phát sóng trực tiếp liên tục trên $cleanName · Nhóm: $groupName",
                        category = groupName
                    )
                )
            }
            return programs
        }
    }
}
