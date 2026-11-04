package com.stremio.mobile.data.translation

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google ML Kit, on the device: no API key, no account, and the synopsis never leaves the phone.
 * The language model (~30 MB per source language) is downloaded once by ML Kit; English is
 * downloaded on any network, other languages only on Wi-Fi.
 */
class MlKitLanguageDetector : LanguageDetector {
    private val client = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.6f).build(),
    )

    override suspend fun detect(text: String): String? =
        client.identifyLanguage(text).await().takeUnless { it == "und" }
}

class MlKitTranslator : TextTranslator {
    private val translators = HashMap<String, Translator>()

    override suspend fun toPortuguese(text: String, sourceLanguage: String): String {
        val source = TranslateLanguage.fromLanguageTag(sourceLanguage)
            ?: throw IllegalArgumentException("Language not supported by ML Kit: $sourceLanguage")
        val translator = synchronized(translators) {
            translators.getOrPut(source) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(source)
                        .setTargetLanguage(TranslateLanguage.PORTUGUESE)
                        .build(),
                )
            }
        }
        val conditions = DownloadConditions.Builder().apply { if (source != TranslateLanguage.ENGLISH) requireWifi() }.build()
        translator.downloadModelIfNeeded(conditions).await()
        return translator.translate(text).await()
    }
}

/** SharedPreferences cache, one JSON entry per synopsis. */
class PreferencesTranslationCache(context: Context) : TranslationCache {
    private val preferences = context.getSharedPreferences("mwplay_synopsis_translations", Context.MODE_PRIVATE)

    override fun get(key: String): CachedTranslation? = runCatching {
        val json = JSONObject(preferences.getString(key, null) ?: return null)
        CachedTranslation(json.getString("text"), json.getString("source"), json.getLong("at"))
    }.getOrNull()

    override fun put(key: String, value: CachedTranslation) {
        val json = JSONObject().put("text", value.text).put("source", value.sourceLanguage).put("at", value.savedAtMs)
        preferences.edit().putString(key, json.toString()).apply()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
