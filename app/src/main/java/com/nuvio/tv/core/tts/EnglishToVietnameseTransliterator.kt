package com.nuvio.tv.core.tts

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transliterates English words, acronyms, and character/place names into Vietnamese phonetic
 * text so Google TTS with a Vietnamese voice (`vi-VN`) pronounces them naturally without needing
 * voice switching or causing audio stutter/latency.
 */
@Singleton
class EnglishToVietnameseTransliterator internal constructor(
    private val dictionaryProvider: () -> TtsPhoneticDictionary.ParsedDictionary,
    private val preloadAction: (() -> Unit)? = null,
) {
    @Inject
    constructor(dictionary: TtsPhoneticDictionary) : this(
        dictionaryProvider = { dictionary.getDictionary() },
        preloadAction = { dictionary.preloadAsync() },
    )

    // Constructor for testing
    constructor(preset: TtsPhoneticDictionary.ParsedDictionary) : this(
        dictionaryProvider = { preset },
        preloadAction = null,
    )

    fun preloadAsync() {
        preloadAction?.invoke()
    }

    companion object {
        private val TOKEN_REGEX = Regex("([\\p{L}\\p{N}']+)|([^\\p{L}\\p{N}']+)")

        private fun isWordBoundaryChar(ch: Char): Boolean =
            ch == '_' || Character.isLetterOrDigit(ch)
        /**
         * Common non-diacritic Vietnamese words that might look like English words but must
         * NEVER be transliterated when appearing in Vietnamese subtitles.
         */
        val VIETNAMESE_STOP_WORDS: Set<String> = hashSetOf(
            "anh", "em", "con", "cho", "khi", "nhung", "trong", "nha", "minh", "nam",
            "lan", "mai", "hai", "hoa", "huong", "linh", "trang", "tuan", "hung", "dung",
            "thanh", "quang", "phuong", "thuy", "ngan", "duc", "giang", "van", "ba", "me",
            "chu", "bac", "co", "di", "chau", "ong", "toi", "ta", "ban", "no", "ho", "chung",
            "nguoi", "cai", "chiec", "mot", "bon", "sau", "bay", "tam", "chin", "muoi",
            "tram", "ngan", "trieu", "ty", "khong", "co", "duoc", "bi", "phai", "se", "da",
            "dang", "vua", "moi", "sap", "rat", "qua", "lam", "het", "roi", "chua", "van",
            "con", "lai", "cu", "hay", "hoac", "neu", "thi", "vi", "nen", "de", "do", "boi",
            "voi", "va", "cung", "nhu", "ma", "ai", "gi", "nao", "dau", "sao", "the", "nay",
            "kia", "day", "ay", "tren", "duoi", "ngoai", "truoc", "sau", "giua", "ben",
            "canh", "gan", "xa", "ve", "tu", "den", "qua", "di", "vao", "ra", "len", "xuong",
            "thay", "xem", "nghe", "noi", "biet", "hieu", "muon", "can", "thich", "yeu",
            "ghet", "an", "uong", "ngu", "ngoi", "dung", "chay", "tao", "lay", "dua", "nhan",
            "giu", "bo", "quen", "nho", "tin", "so", "lo", "vui", "buon", "giup", "cuu",
            "chet", "song", "mau", "nhanh", "cham", "tot", "xau", "dung", "sai", "that", "gia",
            "xa", "gan", "cao", "thap", "to", "nho", "be", "lon", "nhieu", "it", "day", "voi",
            "nang", "nhe", "nong", "lanh", "am", "mat", "sang", "toi", "dep", "kho", "de",
            "xa", "gan", "day", "kia", "do", "chua", "nua", "luon", "ngay", "gio", "phut",
            "giay", "tuan", "thang", "tien", "bac", "nha", "cua", "xe", "duong", "pho", "lang",
            "dong", "tay", "nam", "bac", "troi", "dat", "nuoc", "lua", "gio", "may", "mua",
            "nang", "bien", "song", "nui", "rung", "cay", "hoa", "la", "chim", "ca"
        )

        /** Characters that unambiguously belong to Vietnamese or other non-English alphabets. */
        private val VIETNAMESE_SPECIFIC_CHARS = charArrayOf(
            'à', 'á', 'ả', 'ã', 'ạ', 'ă', 'ằ', 'ắ', 'ẳ', 'ẵ', 'ặ', 'â', 'ầ', 'ấ', 'ẩ', 'ẫ', 'ậ',
            'è', 'é', 'ẻ', 'ẽ', 'ẹ', 'ê', 'ề', 'ế', 'ể', 'ễ', 'ệ',
            'ì', 'í', 'ỉ', 'ĩ', 'ị',
            'ò', 'ó', 'ỏ', 'õ', 'ọ', 'ô', 'ồ', 'ố', 'ổ', 'ỗ', 'ộ', 'ơ', 'ờ', 'ớ', 'ở', 'ỡ', 'ợ',
            'ù', 'ú', 'ủ', 'ũ', 'ụ', 'ư', 'ừ', 'ứ', 'ử', 'ữ', 'ự',
            'ỳ', 'ý', 'ỷ', 'ỹ', 'ỵ',
            'đ',
            'À', 'Á', 'Ả', 'Ã', 'Ạ', 'Ă', 'Ằ', 'Ắ', 'Ẳ', 'Ẵ', 'Ặ', 'Â', 'Ầ', 'Ấ', 'Ẩ', 'Ẫ', 'Ậ',
            'È', 'É', 'Ẻ', 'Ẽ', 'Ẹ', 'Ê', 'Ề', 'Ế', 'Ể', 'Ễ', 'Ệ',
            'Ì', 'Í', 'Ỉ', 'Ĩ', 'Ị',
            'Ò', 'Ó', 'Ỏ', 'Õ', 'Ọ', 'Ô', 'Ồ', 'Ố', 'Ổ', 'Ỗ', 'Ộ', 'Ơ', 'Ờ', 'Ớ', 'Ở', 'Ỡ', 'Ợ',
            'Ù', 'Ú', 'Ủ', 'Ũ', 'Ụ', 'Ư', 'Ừ', 'Ứ', 'Ử', 'Ữ', 'Ự',
            'Ỳ', 'Ý', 'Ỷ', 'Ỹ', 'Ỵ',
            'Đ'
        ).toSet()

        private val ACRONYM_SPELLOUT: Map<Char, String> = mapOf(
            'A' to "ây", 'B' to "bi", 'C' to "xi", 'D' to "đi", 'E' to "i",
            'F' to "ép", 'G' to "gi", 'H' to "hắt", 'I' to "ai", 'J' to "giây",
            'K' to "cây", 'L' to "en", 'M' to "em", 'N' to "en", 'O' to "ô",
            'P' to "pi", 'Q' to "kiu", 'R' to "a", 'S' to "ét", 'T' to "ti",
            'U' to "diu", 'V' to "vi", 'W' to "đáp liu", 'X' to "ếch", 'Y' to "oai",
            'Z' to "dét"
        )
    }

    /**
     * Transliterates [text] by identifying multi-word English names/phrases, dictionary matches,
     * acronyms, and foreign proper names, mapping them to Vietnamese phonetics.
     */
    fun transliterate(text: String): String {
        if (text.isBlank()) return text
        if (text.startsWith("http://") || text.startsWith("https://")) return text

        val dict = dictionaryProvider()
        val phrasesByFirstWord = dict.phrasesByFirstWord
        val words = dict.words
        val lowerText = text.lowercase(Locale.ROOT)
        val sb = StringBuilder(text.length + 32)
        var skipUntil = 0

        for (match in TOKEN_REGEX.findAll(text)) {
            val matchStart = match.range.first
            val matchEndExclusive = match.range.last + 1
            if (matchStart < skipUntil) continue

            val token = match.value
            val isWord = match.groups[1] != null

            if (!isWord) {
                sb.append(token)
                continue
            }

            // O(1) multi-word phrase lookup indexed by first word (zero per-cue regex compilation).
            if (phrasesByFirstWord.isNotEmpty() &&
                (matchStart == 0 || !isWordBoundaryChar(text[matchStart - 1]))
            ) {
                val candidates = phrasesByFirstWord[token.lowercase(Locale.ROOT)]
                if (candidates != null) {
                    var matchedPhrase: TtsPhoneticDictionary.PhraseEntry? = null
                    for (candidate in candidates) {
                        val endIndex = matchStart + candidate.rawKey.length
                        if (endIndex <= lowerText.length &&
                            lowerText.startsWith(candidate.rawKey, matchStart) &&
                            (endIndex == text.length || !isWordBoundaryChar(text[endIndex]))
                        ) {
                            matchedPhrase = candidate
                            break
                        }
                    }
                    if (matchedPhrase != null) {
                        sb.append(preserveCaseOrPhonetic(text.substring(matchStart, matchStart + matchedPhrase.rawKey.length), matchedPhrase.phonetic))
                        skipUntil = matchStart + matchedPhrase.rawKey.length
                        continue
                    }
                }
            }

            sb.append(transliterateSingleToken(token, words))
            skipUntil = matchEndExclusive
        }

        return sb.toString()
    }

    private fun transliterateSingleToken(token: String, dictionaryWords: Map<String, String>): String {
        // If token has Vietnamese specific accented characters, it is definitely Vietnamese
        if (token.any { it in VIETNAMESE_SPECIFIC_CHARS }) {
            return token
        }

        // Check possessive 's (e.g. John's, Stark's)
        var baseToken = token
        var possessiveSuffix = ""
        if (token.length > 2 && (token.endsWith("'s") || token.endsWith("’s"))) {
            baseToken = token.substring(0, token.length - 2)
            possessiveSuffix = " của"
        }

        val lowerBase = baseToken.lowercase(Locale.ROOT)

        // If it's a known Vietnamese stop word, leave it untouched
        if (VIETNAMESE_STOP_WORDS.contains(lowerBase)) {
            return token
        }

        // 1. Direct dictionary match
        val dictMatch = dictionaryWords[lowerBase]
        if (dictMatch != null) {
            val phonetic = preserveCaseOrPhonetic(baseToken, dictMatch)
            return if (possessiveSuffix.isNotEmpty()) "$phonetic$possessiveSuffix" else phonetic
        }

        // 2. Acronym match (e.g. FBI, CIA, MIT, KGB - 2 to 5 all-uppercase letters)
        if (baseToken.length in 2..5 && baseToken.all { it in 'A'..'Z' }) {
            val spelled = baseToken.map { ACRONYM_SPELLOUT[it] ?: it.toString() }.joinToString(" ")
            return if (possessiveSuffix.isNotEmpty()) "$spelled$possessiveSuffix" else spelled
        }

        // 3. Fallback rule-based transliteration for unknown English proper names
        // Trigger condition:
        // - Starts with a capital letter (likely a name) AND has at least 3 letters
        // - Contains letters not in traditional Vietnamese alphabet: 'j', 'w', 'z', 'f'
        // - Or contains typical English letter combinations: 'sh', 'th', 'ph', 'wh', 'ck', 'str', 'br', 'cr'
        if (isLikelyEnglishProperName(baseToken)) {
            val transliterated = applyRuleBasedPhonetics(baseToken)
            if (transliterated.isNotBlank() && transliterated != baseToken) {
                return if (possessiveSuffix.isNotEmpty()) "$transliterated$possessiveSuffix" else transliterated
            }
        }

        return token
    }

    private fun isLikelyEnglishProperName(token: String): Boolean {
        if (token.length < 3) return false
        val lower = token.lowercase(Locale.ROOT)
        if (VIETNAMESE_STOP_WORDS.contains(lower)) return false

        val hasEnglishExclusiveLetters = lower.any { it == 'j' || it == 'w' || it == 'z' || it == 'f' }
        val hasEnglishClusters = lower.contains("sh") || lower.contains("th") || lower.contains("ck") ||
            lower.contains("ph") || lower.contains("wh") || lower.contains("tion") || lower.contains("sion") ||
            lower.contains("str") || lower.contains("br") || lower.contains("cr") || lower.contains("dr") ||
            lower.contains("gr") || lower.contains("pr") || lower.contains("tr") || lower.contains("ight")

        val startsWithCapital = token[0].isUpperCase() && token.substring(1).any { it.isLowerCase() }

        return hasEnglishExclusiveLetters || hasEnglishClusters || (startsWithCapital && lower.length >= 4)
    }

    private fun applyRuleBasedPhonetics(name: String): String {
        var s = name.lowercase(Locale.ROOT)

        // Cluster replacements
        s = s.replace("tion", "sần")
            .replace("sion", "sần")
            .replace("ight", "ait")
            .replace("ough", "ốp")
            .replace("ee", "i")
            .replace("ea", "i")
            .replace("oo", "u")
            .replace("ck", "c")
            .replace("ph", "ph")
            .replace("sh", "s")
            .replace("ch", "ch")
            .replace("wh", "o")
            .replace("th", "th")
            .replace("w", "u")
            .replace("j", "gi")
            .replace("z", "d")
            .replace("f", "ph")

        // Final s / x
        if (s.endsWith("s") && s.length > 2 && !s.endsWith("ss")) {
            s = s.dropLast(1) + "t"
        }

        // Capitalize first letter to keep name aesthetics
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun preserveCaseOrPhonetic(original: String, phonetic: String): String {
        return phonetic
    }
}
