package com.nuvio.tv.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class EnglishToVietnameseTransliteratorTest {

    private lateinit var transliterator: EnglishToVietnameseTransliterator

    @Before
    fun setUp() {
        // Load dictionary from assets if available, or initialize with fallback stream
        val assetFile = listOf(
            File("src/main/assets/tts_en_vi_dict.tsv"),
            File("app/src/main/assets/tts_en_vi_dict.tsv")
        ).firstOrNull { it.exists() }

        val inputStream: java.io.InputStream = if (assetFile != null) {
            FileInputStream(assetFile)
        } else {
            val sampleTsv = """
                # word	phonetic	type
                tony stark	Tô-ni X-tác	name
                doctor strange	Đốc-tơ X-trên	name
                john wick	Giôn Uých	name
                harry potter	Ha-ri Pót-tơ	name
                new york	Niu Oóc	place
                los angeles	Lốt An-giơ-lét	place
                spider-man	X-pai-đơ Men	name
                michael	Mai-cơn	name
                thanos	Tha-nốt	name
                sherlock	Sơ-lốc	name
                hello	hê-lô	word
                sorry	xo-ri	word
                thank you	thenh kiu	word
                fbi	Ép-bi-ai	acronym
                cia	Xi-ai-ê	acronym
                ok	ô-kê	word
                boss	bót	word
            """.trimIndent()
            java.io.ByteArrayInputStream(sampleTsv.toByteArray(Charsets.UTF_8))
        }

        val parsed = TtsPhoneticDictionary.parseFromInputStream(inputStream)
        transliterator = EnglishToVietnameseTransliterator(parsed)
    }

    @Test
    fun transliteratesMultiWordCharacterAndPlaceNames() {
        val input = "Tôi đã gặp Tony Stark ở New York."
        val output = transliterator.transliterate(input)
        assertTrue(output.contains("Tô ni Xtác"))
        assertTrue(output.contains("Niu Yoóc"))
        assertFalse(output.contains("Tony Stark"))
        assertFalse(output.contains("New York"))
    }

    @Test
    fun transliteratesDoctorStrangeAndJohnWick() {
        val input = "Doctor Strange và John Wick đang tới."
        val output = transliterator.transliterate(input)
        assertTrue(output.contains("Đốc tơ Xtơ ren"))
        assertTrue(output.contains("Giôn Uých"))
    }

    @Test
    fun transliteratesSingleWordNames() {
        val input = "Thanos đã tiêu diệt tất cả."
        val output = transliterator.transliterate(input)
        assertTrue(output.contains("Tha nốt"))
        assertFalse(output.contains("Thanos"))
    }

    @Test
    fun transliteratesCommonEnglishWords() {
        val input = "Hello, tôi xin lỗi. Thank you rất nhiều."
        val output = transliterator.transliterate(input)
        assertTrue(output.contains("Hê lô") || output.contains("hê lô"))
        assertTrue(output.contains("thanh kiu") || output.contains("Thanh kiu"))
    }

    @Test
    fun spellsOutAcronyms() {
        val input = "Đặc vụ FBI và CIA đang điều tra."
        val output = transliterator.transliterate(input)
        assertTrue(output.contains("ép bi ai") || output.contains("Ép bi ai"))
        assertTrue(output.contains("xi ai ây") || output.contains("Xi ai ây"))
    }

    @Test
    fun handlesPossessiveCase() {
        val input = "Đây là John's xe hơi."
        val output = transliterator.transliterate(input)
        assertTrue(output.contains("của"))
    }

    @Test
    fun protectsVietnameseNonDiacriticStopWords() {
        val sentences = listOf(
            "Anh ấy đang đi làm.",
            "Em có khỏe không?",
            "Minh và Nam vừa mới về.",
            "Con gái của Lan đang học bài.",
            "Khi nào chúng ta đi xem phim?",
            "Tôi là người Việt Nam."
        )

        for (sentence in sentences) {
            val output = transliterator.transliterate(sentence)
            // Should preserve common Vietnamese words without altering them to foreign phonetics
            if (sentence.startsWith("Anh")) {
                assertTrue(output.startsWith("Anh"))
            }
            if (sentence.startsWith("Em")) {
                assertTrue(output.startsWith("Em"))
            }
            if (sentence.startsWith("Minh")) {
                assertTrue(output.contains("Minh"))
                assertTrue(output.contains("Nam"))
            }
            if (sentence.startsWith("Con")) {
                assertTrue(output.startsWith("Con"))
                assertTrue(output.contains("Lan"))
            }
        }
    }

    @Test
    fun preservesPunctuationAndStructure() {
        val input = "Chào bạn! Tony Stark, bạn có ở đó không?"
        val output = transliterator.transliterate(input)
        assertTrue(output.startsWith("Chào bạn!"))
        assertTrue(output.endsWith("bạn có ở đó không?"))
        assertTrue(output.contains("Tô ni Xtác,"))
    }

    @Test
    fun blankAndEmptyInputsReturnDirectly() {
        assertEquals("", transliterator.transliterate(""))
        assertEquals("   ", transliterator.transliterate("   "))
    }

    @Test
    fun transliteratesPunctuatedMultiWordPhrasesWithoutPerCueRegexOverhead() {
        val input = "Obi-Wan Kenobi xem Fast & Furious cùng Tony Stark ở New York!"
        val startNs = System.nanoTime()
        var output = ""
        repeat(500) {
            output = transliterator.transliterate(input)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        assertTrue(output.contains("Tô ni Xtác"))
        assertTrue(output.contains("Niu Yoóc"))
        assertTrue("500 transliterations took ${elapsedMs}ms (expected < 500ms)", elapsedMs < 500L)
    }
}
