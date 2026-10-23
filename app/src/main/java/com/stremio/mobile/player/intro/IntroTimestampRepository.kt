package com.stremio.mobile.player.intro

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToLong

/**
 * Opening timestamps, in order of priority:
 * 1. marks the user saved on this device (episode, then whole series);
 * 2. AniSkip (api.aniskip.com), the public community database of anime opening times, for
 *    episodes with Kitsu or MyAnimeList ids (Kitsu ids are mapped through Kitsu's public API).
 * Nothing else: without data the episode simply has no skip button.
 */
class IntroTimestampRepository(
    context: Context,
    private val httpGet: (String) -> String? = ::defaultHttpGet,
) {
    private val preferences = context.getSharedPreferences("mwplay_intro_skip", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<IntroSkipSettings> = _settings.asStateFlow()

    fun updateSettings(settings: IntroSkipSettings) {
        _settings.value = settings
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putBoolean(KEY_SHOW_BUTTON, settings.showButton)
            .putBoolean(KEY_AUTO_SKIP, settings.autoSkip)
            .apply()
    }

    private fun readSettings() = IntroSkipSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        showButton = preferences.getBoolean(KEY_SHOW_BUTTON, true),
        autoSkip = preferences.getBoolean(KEY_AUTO_SKIP, false),
    )

    // region manual marks -------------------------------------------------------------------

    fun manual(ref: EpisodeRef): List<IntroInterval> = listOfNotNull(
        readManual(MANUAL_EPISODE + ref.episodeKey, IntroSource.MANUAL_EPISODE),
        readManual(MANUAL_SERIES + ref.seriesKey, IntroSource.MANUAL_SERIES),
    )

    fun saveManual(ref: EpisodeRef, startMs: Long, endMs: Long, wholeSeries: Boolean) {
        val key = if (wholeSeries) MANUAL_SERIES + ref.seriesKey else MANUAL_EPISODE + ref.episodeKey
        preferences.edit().putString(key, "$startMs,$endMs").apply()
    }

    /** Removes the episode mark and the series mark. */
    fun clearManual(ref: EpisodeRef) {
        preferences.edit().remove(MANUAL_EPISODE + ref.episodeKey).remove(MANUAL_SERIES + ref.seriesKey).apply()
    }

    private fun readManual(key: String, source: IntroSource): IntroInterval? {
        val (start, end) = preferences.getString(key, null)?.split(',')?.mapNotNull { it.toLongOrNull() }
            ?.takeIf { it.size == 2 } ?: return null
        return IntroInterval(start, end, source)
    }

    // region automatic data -----------------------------------------------------------------

    /** All candidates for [ref]; [IntroSkipPolicy.select] picks the one matching the video. */
    suspend fun candidates(ref: EpisodeRef): List<IntroInterval> = manual(ref) + aniSkip(ref)

    private suspend fun aniSkip(ref: EpisodeRef): List<IntroInterval> = withContext(Dispatchers.IO) {
        val (provider, showId, episode) = ref.animeIds ?: return@withContext emptyList()
        val malId = if (provider == "mal") showId else malIdForKitsu(showId) ?: return@withContext emptyList()
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode?types%5B%5D=op&episodeLength=0"
        val body = runCatching { httpGet(url) }.onFailure { Timber.w(it, "AniSkip lookup failed") }.getOrNull()
        body?.let(::parseAniSkip).orEmpty()
    }

    private fun malIdForKitsu(kitsuId: String): String? {
        val cacheKey = KITSU_TO_MAL + kitsuId
        preferences.getString(cacheKey, null)?.let { return it.ifBlank { null } }
        val url = "https://kitsu.io/api/edge/anime/${URLEncoder.encode(kitsuId, "UTF-8")}/mappings" +
            "?filter%5BexternalSite%5D=myanimelist%2Fanime"
        val body = runCatching { httpGet(url) }.onFailure { Timber.w(it, "Kitsu mapping lookup failed") }.getOrNull()
            ?: return null // Network problem: try again next time.
        val malId = parseKitsuMalId(body)
        preferences.edit().putString(cacheKey, malId.orEmpty()).apply()
        return malId
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SHOW_BUTTON = "show_button"
        private const val KEY_AUTO_SKIP = "auto_skip"
        private const val MANUAL_EPISODE = "manual_episode:"
        private const val MANUAL_SERIES = "manual_series:"
        private const val KITSU_TO_MAL = "kitsu_mal:"

        /** Opening ("op") intervals from an AniSkip v2 response, in milliseconds. */
        fun parseAniSkip(body: String): List<IntroInterval> = runCatching {
            val json = JSONObject(body)
            if (!json.optBoolean("found")) return@runCatching emptyList()
            val results = json.optJSONArray("results") ?: return@runCatching emptyList()
            (0 until results.length()).mapNotNull { index ->
                val result = results.optJSONObject(index) ?: return@mapNotNull null
                if (result.optString("skipType") != "op") return@mapNotNull null
                val interval = result.optJSONObject("interval") ?: return@mapNotNull null
                val start = interval.optDouble("startTime", Double.NaN)
                val end = interval.optDouble("endTime", Double.NaN)
                val length = result.optDouble("episodeLength", Double.NaN)
                if (start.isNaN() || end.isNaN() || length.isNaN()) return@mapNotNull null
                IntroInterval(
                    startMs = (start * 1000).roundToLong(),
                    endMs = (end * 1000).roundToLong(),
                    source = IntroSource.ANISKIP,
                    episodeLengthMs = (length * 1000).roundToLong(),
                )
            }
        }.getOrDefault(emptyList())

        /** MyAnimeList id from Kitsu's mappings endpoint, null when the show has none. */
        fun parseKitsuMalId(body: String): String? = runCatching {
            val data = JSONObject(body).optJSONArray("data") ?: return@runCatching null
            (0 until data.length()).asSequence()
                .mapNotNull { data.optJSONObject(it)?.optJSONObject("attributes") }
                .firstOrNull { it.optString("externalSite") == "myanimelist/anime" }
                ?.optString("externalId")?.takeIf { id -> id.isNotBlank() && id.all(Char::isDigit) }
        }.getOrNull()

        private fun defaultHttpGet(url: String): String? {
            val connection = URL(url).openConnection() as HttpURLConnection
            return try {
                connection.connectTimeout = 6_000
                connection.readTimeout = 6_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "MW Play (Android)")
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    null // 404 from AniSkip means "no times for this episode".
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
