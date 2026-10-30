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

/** One HTTP answer; [body] is only read for 200. Network errors are thrown, not returned. */
data class IntroHttpResponse(val code: Int, val body: String?)

/**
 * Opening timestamps from public community databases, asked one at a time and only until one
 * gives a valid answer for the playing video:
 * - anime ids (Kitsu / MyAnimeList): AniSkip (api.aniskip.com), Kitsu ids mapped through Kitsu's
 *   public API;
 * - IMDb episode ids: TheIntroDB (api.introdb.app/segments), then SkipDB
 *   (api.skipdb.tv/api/segments, with the stream duration so it can match the release).
 * A source that is down or rate limited is skipped. Answers (including "no opening") are kept so
 * the same episode is not asked again; network failures are not kept.
 */
class IntroTimestampRepository(
    context: Context,
    private val httpGet: (String) -> IntroHttpResponse = ::defaultHttpGet,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences("mwplay_intro_skip", Context.MODE_PRIVATE)
    private val memory = HashMap<String, IntroInterval?>()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<IntroSkipSettings> = _settings.asStateFlow()

    init {
        dropLegacyManualMarks()
    }

    fun updateSettings(settings: IntroSkipSettings) {
        _settings.value = settings
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putBoolean(KEY_AUTO_SKIP, settings.autoSkip)
            .apply()
    }

    private fun readSettings() = IntroSkipSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        autoSkip = preferences.getBoolean(KEY_AUTO_SKIP, false),
    )

    /** The manual "Marcar abertura" feature was replaced by the databases below. */
    private fun dropLegacyManualMarks() {
        val legacy = preferences.all.keys.filter {
            it.startsWith("manual_episode:") || it.startsWith("manual_series:") || it == "show_button"
        }
        if (legacy.isEmpty()) return
        preferences.edit().apply { legacy.forEach(::remove) }.apply()
    }

    /**
     * The opening of [ref] for a video of [durationMs], or null when no source has valid data.
     * Never throws; runs on the IO dispatcher.
     */
    suspend fun find(ref: EpisodeRef, durationMs: Long): IntroInterval? = withContext(Dispatchers.IO) {
        if (durationMs <= 0) return@withContext null
        val cacheKey = "${ref.episodeKey}|${durationMs / DURATION_BUCKET_MS}"
        if (memory.containsKey(cacheKey)) return@withContext memory[cacheKey]
        readCached(cacheKey)?.let { cached ->
            memory[cacheKey] = cached.interval
            return@withContext cached.interval
        }

        var anySourceFailed = false
        var result: IntroInterval? = null
        for (source in sourcesFor(ref)) {
            val answer = try {
                query(source, ref, durationMs)
            } catch (error: Exception) {
                Timber.w(error, "Intro lookup: %s unavailable for %s", source.label, ref.episodeKey)
                anySourceFailed = true
                continue
            }
            Timber.d("Intro lookup: %s -> %s for %s", source.label, answer, ref.episodeKey)
            when (answer) {
                is SourceAnswer.Found -> {
                    result = answer.interval
                    break
                }
                // The community confirmed there is no opening: nothing to skip, stop here.
                SourceAnswer.ConfirmedNone -> break
                SourceAnswer.NoData -> Unit
            }
        }
        memory[cacheKey] = result
        // A down source may have had the answer: only remember a miss when every source replied.
        if (result != null || !anySourceFailed) writeCached(cacheKey, result)
        result
    }

    private fun sourcesFor(ref: EpisodeRef): List<IntroSource> = when {
        ref.animeIds != null -> listOf(IntroSource.ANISKIP)
        ref.imdbEpisode != null -> listOf(IntroSource.INTRODB, IntroSource.SKIPDB)
        else -> emptyList()
    }

    private fun query(source: IntroSource, ref: EpisodeRef, durationMs: Long): SourceAnswer = when (source) {
        IntroSource.INTRODB -> {
            val (imdb, season, episode) = ref.imdbEpisode!!
            val response = httpGet("https://api.introdb.app/segments?imdb_id=$imdb&season=$season&episode=$episode")
            when (response.code) {
                HttpURLConnection.HTTP_OK -> parseIntroDb(response.body.orEmpty(), imdb, season, episode)
                    .validFor(durationMs)
                HttpURLConnection.HTTP_NOT_FOUND -> SourceAnswer.NoData
                else -> throw IllegalStateException("HTTP ${response.code}")
            }
        }
        IntroSource.SKIPDB -> {
            val (imdb, season, episode) = ref.imdbEpisode!!
            val seconds = durationMs / 1000
            val response = httpGet(
                "https://api.skipdb.tv/api/segments?imdb_id=$imdb&season=$season&episode=$episode&type=intro&duration=$seconds"
            )
            when (response.code) {
                HttpURLConnection.HTTP_OK -> parseSkipDb(response.body.orEmpty(), imdb, season, episode)
                    .validFor(durationMs)
                HttpURLConnection.HTTP_NOT_FOUND -> SourceAnswer.NoData
                else -> throw IllegalStateException("HTTP ${response.code}")
            }
        }
        IntroSource.ANISKIP -> {
            val (provider, showId, episode) = ref.animeIds!!
            val malId = if (provider == "mal") showId else malIdForKitsu(showId)
            if (malId == null) {
                SourceAnswer.NoData
            } else {
                val response = httpGet("https://api.aniskip.com/v2/skip-times/$malId/$episode?types%5B%5D=op&episodeLength=0")
                when (response.code) {
                    HttpURLConnection.HTTP_OK -> IntroSkipPolicy.select(parseAniSkip(response.body.orEmpty()), durationMs)
                        ?.let { SourceAnswer.Found(it) } ?: SourceAnswer.NoData
                    // AniSkip answers 404 for "no times for this episode".
                    HttpURLConnection.HTTP_NOT_FOUND -> SourceAnswer.NoData
                    else -> throw IllegalStateException("HTTP ${response.code}")
                }
            }
        }
    }

    private fun SourceAnswer.validFor(durationMs: Long): SourceAnswer =
        if (this is SourceAnswer.Found && !IntroSkipPolicy.isReliable(interval, durationMs)) SourceAnswer.NoData else this

    private fun malIdForKitsu(kitsuId: String): String? {
        val cacheKey = KITSU_TO_MAL + kitsuId
        preferences.getString(cacheKey, null)?.let { return it.ifBlank { null } }
        val url = "https://kitsu.io/api/edge/anime/${URLEncoder.encode(kitsuId, "UTF-8")}/mappings" +
            "?filter%5BexternalSite%5D=myanimelist%2Fanime"
        val response = httpGet(url)
        if (response.code != HttpURLConnection.HTTP_OK) throw IllegalStateException("Kitsu HTTP ${response.code}")
        val malId = parseKitsuMalId(response.body.orEmpty())
        preferences.edit().putString(cacheKey, malId.orEmpty()).apply()
        return malId
    }

    // region cache ----------------------------------------------------------------------------

    private class Cached(val interval: IntroInterval?)

    private fun readCached(key: String): Cached? {
        val raw = preferences.getString(CACHE + key, null) ?: return null
        val parts = raw.split(',')
        val savedAt = parts.getOrNull(0)?.toLongOrNull() ?: return null
        if (parts.getOrNull(1) == "none") {
            return if (now() - savedAt < MISS_TTL_MS) Cached(null) else null
        }
        if (now() - savedAt >= HIT_TTL_MS || parts.size != 6) return null
        val source = runCatching { IntroSource.valueOf(parts[1]) }.getOrNull() ?: return null
        val start = parts[2].toLongOrNull() ?: return null
        val end = parts[3].toLongOrNull() ?: return null
        return Cached(IntroInterval(start, end, source, parts[4].toLongOrNull(), parts[5] == "1"))
    }

    private fun writeCached(key: String, interval: IntroInterval?) {
        val value = if (interval == null) {
            "${now()},none"
        } else {
            listOf(now(), interval.source.name, interval.startMs, interval.endMs, interval.episodeLengthMs ?: "", if (interval.durationMatched) 1 else 0)
                .joinToString(",")
        }
        preferences.edit().putString(CACHE + key, value).apply()
    }

    sealed interface SourceAnswer {
        data class Found(val interval: IntroInterval) : SourceAnswer
        /** The source knows the episode and says it has no opening. */
        data object ConfirmedNone : SourceAnswer
        data object NoData : SourceAnswer
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_SKIP = "auto_skip"
        private const val KITSU_TO_MAL = "kitsu_mal:"
        private const val CACHE = "lookup:"
        private const val DURATION_BUCKET_MS = 10_000L
        private const val HIT_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val MISS_TTL_MS = 12L * 60 * 60 * 1000

        /** Lowest SkipDB confidence (0–1) accepted. */
        const val SKIPDB_MIN_CONFIDENCE = 0.5

        private fun JSONObject.matches(imdb: String, season: Int, episode: Int): Boolean =
            optString("imdb_id") == imdb && optInt("season", -1) == season && optInt("episode", -1) == episode

        /**
         * TheIntroDB `/segments`: `intro` {start_ms, end_ms} or null; `no_intro` true when the
         * community confirmed the episode has none. The echoed ids must be the requested ones.
         */
        fun parseIntroDb(body: String, imdb: String, season: Int, episode: Int): SourceAnswer = runCatching {
            val json = JSONObject(body)
            if (!json.matches(imdb, season, episode)) return@runCatching SourceAnswer.NoData
            val intro = json.optJSONObject("intro")
            if (intro == null) {
                return@runCatching if (json.optBoolean("no_intro")) SourceAnswer.ConfirmedNone else SourceAnswer.NoData
            }
            val start = intro.optLong("start_ms", -1)
            val end = intro.optLong("end_ms", -1)
            if (start < 0 || end <= start) SourceAnswer.NoData
            else SourceAnswer.Found(IntroInterval(start, end, IntroSource.INTRODB))
        }.getOrDefault(SourceAnswer.NoData)

        /**
         * SkipDB `/api/segments`: `segments.intro` {start_ms, end_ms, match, confidence}. Only
         * "exact" or "shifted" matches against the duration we sent are used; "out-of-range"
         * comes from a different release.
         */
        fun parseSkipDb(body: String, imdb: String, season: Int, episode: Int): SourceAnswer = runCatching {
            val json = JSONObject(body)
            if (!json.matches(imdb, season, episode)) return@runCatching SourceAnswer.NoData
            val intro = json.optJSONObject("segments")?.optJSONObject("intro") ?: return@runCatching SourceAnswer.NoData
            val match = intro.optString("match")
            if (match != "exact" && match != "shifted") return@runCatching SourceAnswer.NoData
            if (intro.optDouble("confidence", 0.0) < SKIPDB_MIN_CONFIDENCE) return@runCatching SourceAnswer.NoData
            val start = intro.optDouble("start_ms", Double.NaN)
            val end = intro.optDouble("end_ms", Double.NaN)
            if (start.isNaN() || end.isNaN() || end <= start) SourceAnswer.NoData
            else SourceAnswer.Found(IntroInterval(start.roundToLong(), end.roundToLong(), IntroSource.SKIPDB, durationMatched = true))
        }.getOrDefault(SourceAnswer.NoData)

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

        private fun defaultHttpGet(url: String): IntroHttpResponse {
            val connection = URL(url).openConnection() as HttpURLConnection
            return try {
                connection.connectTimeout = 6_000
                connection.readTimeout = 6_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "MW Play (Android)")
                val code = connection.responseCode
                val body = if (code == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
                IntroHttpResponse(code, body)
            } finally {
                connection.disconnect()
            }
        }
    }
}
