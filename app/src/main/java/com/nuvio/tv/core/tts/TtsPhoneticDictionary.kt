package com.nuvio.tv.core.tts

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and caches the English-to-Vietnamese phonetic dictionary from `assets/tts_en_vi_dict.tsv`.
 */
@Singleton
class TtsPhoneticDictionary @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val DEFAULT_DICT_ASSET_PATH = "tts_en_vi_dict.tsv"
        private const val TAG = "TtsPhoneticDictionary"

        fun parseFromInputStream(inputStream: InputStream): ParsedDictionary {
            val phrases = mutableListOf<PhraseEntry>()
            val words = mutableMapOf<String, String>()

            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val parts = trimmed.split('\t')
                    if (parts.size < 2) continue
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()
                    if (key.isEmpty() || value.isEmpty()) continue

                    val tokens = key.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (tokens.size > 1) {
                        phrases.add(PhraseEntry(tokens = tokens, rawKey = key, phonetic = value))
                    } else {
                        words[key] = value
                    }
                }
            }

            // Sort phrases with more tokens first, then longer string length first
            phrases.sortWith(
                compareByDescending<PhraseEntry> { it.tokens.size }
                    .thenByDescending { it.rawKey.length }
            )

            return ParsedDictionary(phrases = phrases, words = words)
        }
    }

    data class PhraseEntry(
        val tokens: List<String>,
        val rawKey: String,
        val phonetic: String,
    )

    data class ParsedDictionary(
        val phrases: List<PhraseEntry>,
        val words: Map<String, String>,
    )

    @Volatile private var cachedDictionary: ParsedDictionary? = null
    private val loadLock = Any()

    fun getDictionary(): ParsedDictionary {
        cachedDictionary?.let { return it }
        synchronized(loadLock) {
            cachedDictionary?.let { return it }
            val loaded = runCatching {
                context.assets.open(DEFAULT_DICT_ASSET_PATH).use { input ->
                    parseFromInputStream(input)
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed loading TTS phonetic dictionary from $DEFAULT_DICT_ASSET_PATH", e)
            }.getOrDefault(ParsedDictionary(emptyList(), emptyMap()))

            cachedDictionary = loaded
            return loaded
        }
    }
}
