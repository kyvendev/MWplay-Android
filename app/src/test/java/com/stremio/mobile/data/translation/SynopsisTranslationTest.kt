package com.stremio.mobile.data.translation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Detector and translator are test doubles: no model, no network. */
class SynopsisTranslationTest {
    private class MemoryCache : TranslationCache {
        val entries = HashMap<String, CachedTranslation>()
        override fun get(key: String) = entries[key]
        override fun put(key: String, value: CachedTranslation) { entries[key] = value }
    }

    /** Word-by-word dictionary; unknown words (names, tokens) are copied as they are. */
    private class FakeTranslator(private val mangleTokens: Boolean = false, private val fail: Boolean = false) : TextTranslator {
        val calls = mutableListOf<Pair<String, String>>()
        private val words = mapOf(
            "A" to "Um", "chemistry" to "professor", "teacher" to "de química", "turns" to "vira",
            "to" to "para o", "crime" to "crime.", "in" to "em", "Un" to "Um", "professeur" to "professor",
            "lives" to "mora", "and" to "e",
        )
        override suspend fun toPortuguese(text: String, sourceLanguage: String): String {
            calls += text to sourceLanguage
            if (fail) throw IllegalStateException("offline")
            return text.split(' ').joinToString(" ") { word ->
                if (mangleTokens && word.startsWith("ZQX")) "zqx" else words[word.trimEnd('.')] ?: word
            }
        }
    }

    private val english = "A chemistry teacher turns to crime"
    private var clock = 1_000L

    private fun repository(
        language: String? = "en",
        translator: TextTranslator = FakeTranslator(),
        cache: TranslationCache = MemoryCache(),
    ) = SynopsisTranslationRepository({ language }, translator, cache, now = { clock })

    @Test
    fun portugueseSynopsisIsShownAsIs() = runBlocking {
        val translator = FakeTranslator()
        val result = repository(language = "pt", translator = translator).translate("series", "tt1", "Um professor de química.")
        assertEquals(SynopsisResult.Original("Um professor de química."), result)
        assertTrue(translator.calls.isEmpty())
    }

    @Test
    fun englishSynopsisIsTranslated() = runBlocking {
        val result = repository().translate("series", "tt1", english)
        assertEquals(SynopsisResult.Translated("Um professor de química vira para o crime.", "en"), result)
    }

    @Test
    fun otherLanguagesAreTranslatedFromTheirOwnLanguage() = runBlocking {
        val translator = FakeTranslator()
        val result = repository(language = "fr", translator = translator).translate("movie", "tt2", "Un professeur")
        assertEquals(SynopsisResult.Translated("Um professor", "fr"), result)
        assertEquals("fr", translator.calls.single().second)
    }

    @Test
    fun cachedTranslationIsReusedAndRefreshedWhenOld() = runBlocking {
        val cache = MemoryCache()
        val translator = FakeTranslator()
        val repo = repository(translator = translator, cache = cache)
        repo.translate("series", "tt1", english)
        assertEquals("Um professor de química vira para o crime.", repo.cached("series", "tt1", english)?.text)
        repo.translate("series", "tt1", english)
        assertEquals(1, translator.calls.size)
        // A changed synopsis is a new entry.
        assertNull(repo.cached("series", "tt1", "$english in Albuquerque"))
        // Six months later the translation is done again.
        clock += SynopsisTranslationRepository.REFRESH_AFTER_MS
        assertNull(repo.cached("series", "tt1", english))
        repo.translate("series", "tt1", english)
        assertEquals(2, translator.calls.size)
    }

    @Test
    fun serviceFailureShowsTheOriginalAndCachesNothing() = runBlocking {
        val cache = MemoryCache()
        val result = repository(translator = FakeTranslator(fail = true), cache = cache).translate("series", "tt1", english)
        assertEquals(SynopsisResult.Failed(english), result)
        assertTrue(cache.entries.isEmpty())
        // The detector failing (model unavailable) also keeps the original.
        val noDetector = SynopsisTranslationRepository({ throw IllegalStateException("no model") }, FakeTranslator(), MemoryCache())
        assertEquals(SynopsisResult.Original(english), noDetector.translate("series", "tt1", english))
    }

    @Test
    fun missingSynopsisMakesNoCalls() = runBlocking {
        val translator = FakeTranslator()
        val repo = repository(translator = translator)
        assertEquals(SynopsisResult.Original(null), repo.translate("movie", "tt3", null))
        assertEquals(SynopsisResult.Original("  "), repo.translate("movie", "tt3", "  "))
        assertTrue(translator.calls.isEmpty())
    }

    @Test
    fun undeterminedLanguageIsLeftAlone() = runBlocking {
        assertEquals(SynopsisResult.Original(english), repository(language = null).translate("movie", "tt3", english))
    }

    @Test
    fun titlesAndNamesAreNeverTranslated() = runBlocking {
        val translator = FakeTranslator()
        val text = "Walter White lives in Albuquerque and Breaking Bad"
        val result = repository(translator = translator).translate("series", "tt1", text, listOf("Breaking Bad", "Walter White", "Bryan Cranston"))
        assertEquals(SynopsisResult.Translated("Walter White mora em Albuquerque e Breaking Bad", "en"), result)
        // The translator only saw placeholders in place of the names.
        assertFalse(translator.calls.single().first.contains("Walter White"))
    }

    @Test
    fun mangledPlaceholdersFallBackToAPlainTranslation() = runBlocking {
        val translator = FakeTranslator(mangleTokens = true)
        val result = repository(translator = translator).translate("series", "tt1", "Walter White lives", listOf("Walter White"))
        assertEquals(SynopsisResult.Translated("Walter White mora", "en"), result)
        assertEquals(2, translator.calls.size)
    }

    @Test
    fun paragraphsAreKept() = runBlocking {
        val result = repository().translate("movie", "tt4", "A teacher\n\nturns to crime")
        assertEquals("Um de química\n\nvira para o crime.", (result as SynopsisResult.Translated).text)
    }

    @Test
    fun maskingKeepsLongerNamesWhole() {
        val (masked, placeholders) = SynopsisTranslationRepository.mask("Walter White Jr. and Walter White", listOf("Walter White", "Walter White Jr."))
        assertEquals("ZQX0XQZ and ZQX1XQZ", masked)
        assertEquals("Walter White Jr. and Walter White", SynopsisTranslationRepository.unmask(masked, placeholders))
    }
}
