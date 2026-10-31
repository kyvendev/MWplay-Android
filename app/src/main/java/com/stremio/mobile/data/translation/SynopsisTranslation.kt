package com.stremio.mobile.data.translation

import timber.log.Timber

/** Detects the language of a text; returns an ISO 639-1 code such as "en" or "pt", null when unsure. */
fun interface LanguageDetector {
    suspend fun detect(text: String): String?
}

/** Translates [text] from [sourceLanguage] to Portuguese. Throws when the service is unavailable. */
fun interface TextTranslator {
    suspend fun toPortuguese(text: String, sourceLanguage: String): String
}

/** Stores translations; values survive app restarts. */
interface TranslationCache {
    fun get(key: String): CachedTranslation?
    fun put(key: String, value: CachedTranslation)
}

data class CachedTranslation(val text: String, val sourceLanguage: String, val savedAtMs: Long)

/** What the details screen shows for a synopsis. */
sealed interface SynopsisResult {
    /** Already Portuguese, empty, or the language could not be told: shown as is. */
    data class Original(val text: String?) : SynopsisResult
    data class Translated(val text: String, val sourceLanguage: String) : SynopsisResult
    /** The translation failed (offline, service unavailable): the original is shown. */
    data class Failed(val text: String) : SynopsisResult
}

/**
 * Synopses in Portuguese (pt-BR) for every content: the addons send the metadata in their own
 * language (Cinemeta sends English and the Stremio protocol has no language parameter), so a
 * synopsis that is not Portuguese is translated once and cached.
 *
 * - Key: content type + id + the original text's hash + target language, so a changed synopsis
 *   is translated again and a cached one is never reused for another text.
 * - Entries older than [refreshAfterMs] are translated again.
 * - Proper names the app knows (title, cast, directors) are replaced by placeholders before
 *   translating and put back after, so they are never translated; if the translator mangles a
 *   placeholder, the text is translated without them instead.
 * - Paragraphs are translated one by one and the line breaks are kept.
 * - Never throws: on failure the original is returned.
 */
class SynopsisTranslationRepository(
    private val detector: LanguageDetector,
    private val translator: TextTranslator,
    private val cache: TranslationCache,
    private val now: () -> Long = System::currentTimeMillis,
    private val refreshAfterMs: Long = REFRESH_AFTER_MS,
) {
    /** The cached translation for this synopsis, if any (no detection, no translation). */
    fun cached(type: String, id: String, text: String?): SynopsisResult.Translated? {
        if (text.isNullOrBlank()) return null
        val entry = cache.get(keyOf(type, id, text)) ?: return null
        if (now() - entry.savedAtMs >= refreshAfterMs) return null
        return SynopsisResult.Translated(entry.text, entry.sourceLanguage)
    }

    suspend fun translate(type: String, id: String, text: String?, protectedNames: List<String> = emptyList()): SynopsisResult {
        if (text.isNullOrBlank()) return SynopsisResult.Original(text)
        cached(type, id, text)?.let { return it }
        val language = runCatching { detector.detect(text) }
            .onFailure { Timber.w(it, "Synopsis language detection failed") }
            .getOrNull()
            ?.lowercase()
        if (language == null || language == PORTUGUESE) return SynopsisResult.Original(text)
        return try {
            val translated = translateKeepingNames(text, language, protectedNames)
            cache.put(keyOf(type, id, text), CachedTranslation(translated, language, now()))
            SynopsisResult.Translated(translated, language)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Timber.w(error, "Synopsis translation failed (%s)", language)
            SynopsisResult.Failed(text)
        }
    }

    private suspend fun translateKeepingNames(text: String, language: String, names: List<String>): String {
        val (masked, placeholders) = mask(text, names)
        if (placeholders.isNotEmpty()) {
            val translated = translateParagraphs(masked, language)
            if (placeholders.keys.all { translated.contains(it) }) return unmask(translated, placeholders)
        }
        return translateParagraphs(text, language)
    }

    private suspend fun translateParagraphs(text: String, language: String): String =
        text.split('\n').map { line -> if (line.isBlank()) line else translator.toPortuguese(line.trim(), language) }
            .joinToString("\n")

    companion object {
        const val PORTUGUESE = "pt"
        const val TARGET = "pt-BR"
        /** Translations are refreshed after six months. */
        const val REFRESH_AFTER_MS = 180L * 24 * 60 * 60 * 1000

        fun keyOf(type: String, id: String, text: String): String =
            "$TARGET|$type|$id|${text.length}:${text.hashCode().toUInt()}"

        /** Replaces every known name found in [text] by a token the translator leaves alone. */
        fun mask(text: String, names: List<String>): Pair<String, Map<String, String>> {
            var masked = text
            val placeholders = linkedMapOf<String, String>()
            names.map { it.trim() }.filter { it.length >= 2 }.distinct()
                // Longer names first, so "Walter White Jr." is not split by "Walter White".
                .sortedByDescending { it.length }
                .forEach { name ->
                    if (!masked.contains(name)) return@forEach
                    val token = "ZQX${placeholders.size}XQZ"
                    placeholders[token] = name
                    masked = masked.replace(name, token)
                }
            return masked to placeholders
        }

        fun unmask(text: String, placeholders: Map<String, String>): String =
            placeholders.entries.fold(text) { acc, (token, name) -> acc.replace(token, name) }
    }
}
