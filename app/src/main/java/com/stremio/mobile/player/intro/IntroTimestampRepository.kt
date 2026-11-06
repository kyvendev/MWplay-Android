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
 * Opening, recap and credits timestamps from public community databases, asked one at a time:
 * - anime ids (Kitsu / MyAnimeList): AniSkip (api.aniskip.com), Kitsu ids mapped through Kitsu's
 *   public API;
 * - IMDb episode ids: TheIntroDB (api.introdb.app/segments), then SkipDB
 *   (api.skipdb.tv/api/segments, with the stream duration so it can match the release).
 * Each source answers all segments in one request. The next source is asked only to fill what is
 * still missing (the opening or the credits), so an episode costs at most one request per source.
 * A source that is down or rate limited is skipped. Answers are kept so the same episode is not
 * asked again; network failures are not kept.
 */
class IntroTimestampRepository(
    context: Context,
    private val httpGet: (String) -> IntroHttpResponse = ::defaultHttpGet,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences("mwplay_intro_skip", Context.MODE_PRIVATE)
    private val memory = HashMap<String, EpisodeSegments>()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<IntroSkipSettings> = _settings.asStateFlow()

    init {
        dropLegacyEntries()
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

    /** The removed manual "Marcar abertura" marks and the opening-only cache of earlier versions. */
    private fun dropLegacyEntries() {
        val legacy = preferences.all.keys.filter {
            it.startsWith("manual_episode:") || it.startsWith("manual_series:") || it == "show_button" || it.startsWith("lookup:")
        }
        if (legacy.isEmpty()) return
        preferences.edit().apply { legacy.forEach(::remove) }.apply()
    }

    /**
     * The opening, recap and credits of [ref] for a video of [durationMs]; parts without valid
     * data are null. Never throws; runs on the IO dispatcher.
     */
    suspend fun find(ref: EpisodeRef, durationMs: Long): EpisodeSegments = withContext(Dispatchers.IO) {
        if (durationMs <= 0) return@withContext EpisodeSegments()
        val cacheKey = "${ref.episodeKey}|${durationMs / DURATION_BUCKET_MS}"
        memory[cacheKey]?.let { return@withContext it }
        readCached(cacheKey)?.let { cached ->
            memory[cacheKey] = cached
            return@withContext cached
        }

        var anySourceFailed = false
        var result = EpisodeSegments()
        var introSettled = false
        for (source in sourcesFor(ref)) {
            val answer = try {
                query(source, ref, durationMs)
            } catch (error: Exception) {
                Timber.w(error, "Intro lookup: %s unavailable for %s", source.label, ref.episodeKey)
                anySourceFailed = true
                continue
            }
            Timber.d("Intro lookup: %s -> %s for %s", source.label, answer, ref.episodeKey)
            // Earlier sources win; later ones only fill the gaps.
            for (kind in SegmentKind.entries) {
                if (result.get(kind) == null) answer.segments.get(kind)?.let { result = result.with(kind, it) }
            }
            // The community confirmed there is no opening: nothing to skip there.
            if (result.intro != null || answer.confirmedNoIntro) introSettled = true
            if (introSettled && result.credits != null) break
        }
        memory[cacheKey] = result
        // A down source may have had the answer: only remember a miss when every source replied.
        if (result != EpisodeSegments() || !anySourceFailed) writeCached(cacheKey, result)
        result
    }

    /** Opening only, for callers that do not need the other segments. */
    suspend fun findIntro(ref: EpisodeRef, durationMs: Long): IntroInterval? = find(ref, durationMs).intro

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
                HttpURLConnection.HTTP_OK -> parseIntroDb(response.body.orEmpty(), imdb, season, episode).validFor(durationMs)
                HttpURLConnection.HTTP_NOT_FOUND -> SourceAnswer.NONE
                else -> throw IllegalStateException("HTTP ${response.code}")
            }
        }
        IntroSource.SKIPDB -> {
            val (imdb, season, episode) = ref.imdbEpisode!!
            val seconds = durationMs / 1000
            val response = httpGet(
                "https://api.skipdb.tv/api/segments?imdb_id=$imdb&season=$season&episode=$episode&duration=$seconds"
            )
            when (response.code) {
                HttpURLConnection.HTTP_OK -> parseSkipDb(response.body.orEmpty(), imdb, season, episode).validFor(durationMs)
                HttpURLConnection.HTTP_NOT_FOUND -> SourceAnswer.NONE
                else -> throw IllegalStateException("HTTP ${response.code}")
            }
        }
        IntroSource.ANISKIP -> {
            val (provider, showId, episode) = ref.animeIds!!
            val malId = if (provider == "mal") showId else malIdForKitsu(showId)
            if (malId == null) {
                SourceAnswer.NONE
            } else {
                val response = httpGet(
                    "https://api.aniskip.com/v2/skip-times/$malId/$episode?types%5B%5D=op&types%5B%5D=ed&types%5B%5D=recap&episodeLength=0"
                )
                when (response.code) {
                    HttpURLConnection.HTTP_OK -> {
                        val candidates = parseAniSkipSegments(response.body.orEmpty())
                        SourceAnswer(
                            EpisodeSegments(
                                intro = IntroSkipPolicy.select(candidates[SegmentKind.INTRO].orEmpty(), durationMs, SegmentKind.INTRO),
                                recap = IntroSkipPolicy.select(candidates[SegmentKind.RECAP].orEmpty(), durationMs, SegmentKind.RECAP),
                                credits = IntroSkipPolicy.select(candidates[SegmentKind.CREDITS].orEmpty(), durationMs, SegmentKind.CREDITS),
                            ),
                        )
                    }
                    // AniSkip answers 404 for "no times for this episode".
                    HttpURLConnection.HTTP_NOT_FOUND -> SourceAnswer.NONE
                    else -> throw IllegalStateException("HTTP ${response.code}")
                }
            }
        }
    }

    /** Drops the parts that do not fit the playing video. */
    private fun SourceAnswer.validFor(durationMs: Long): SourceAnswer {
        var valid = segments
        for (kind in SegmentKind.entries) {
            val interval = valid.get(kind) ?: continue
            if (!IntroSkipPolicy.isReliable(interval, durationMs, kind)) valid = valid.with(kind, null)
        }
        return copy(segments = valid)
    }

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

    private fun readCached(key: String): EpisodeSegments? = runCatching {
        val json = JSONObject(preferences.getString(CACHE + key, null) ?: return null)
        val savedAt = json.getLong("at")
        val segments = EpisodeSegments(
            intro = json.optJSONObject("intro")?.toInterval(),
            recap = json.optJSONObject("recap")?.toInterval(),
            credits = json.optJSONObject("credits")?.toInterval(),
        )
        val ttl = if (segments == EpisodeSegments()) MISS_TTL_MS else HIT_TTL_MS
        segments.takeIf { now() - savedAt < ttl }
    }.getOrNull()

    private fun writeCached(key: String, segments: EpisodeSegments) {
        val json = JSONObject().put("at", now())
        segments.intro?.let { json.put("intro", it.toJson()) }
        segments.recap?.let { json.put("recap", it.toJson()) }
        segments.credits?.let { json.put("credits", it.toJson()) }
        preferences.edit().putString(CACHE + key, json.toString()).apply()
    }

    private fun IntroInterval.toJson() = JSONObject()
        .put("source", source.name).put("start", startMs).put("end", endMs)
        .put("length", episodeLengthMs ?: -1L).put("matched", durationMatched)

    private fun JSONObject.toInterval() = IntroInterval(
        startMs = getLong("start"),
        endMs = getLong("end"),
        source = IntroSource.valueOf(getString("source")),
        episodeLengthMs = optLong("length", -1L).takeIf { it >= 0 },
        durationMatched = optBoolean("matched"),
    )

    /** What one source said about the episode. */
    data class SourceAnswer(
        val segments: EpisodeSegments = EpisodeSegments(),
        /** The source knows the episode and says it has no opening. */
        val confirmedNoIntro: Boolean = false,
    ) {
        companion object {
            val NONE = SourceAnswer()
        }
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_SKIP = "auto_skip"
        private const val KITSU_TO_MAL = "kitsu_mal:"
        private const val CACHE = "segments:"
        private const val DURATION_BUCKET_MS = 10_000L
        private const val HIT_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val MISS_TTL_MS = 12L * 60 * 60 * 1000

        /** Lowest SkipDB confidence (0–1) accepted. */
        const val SKIPDB_MIN_CONFIDENCE = 0.5

        private fun JSONObject.matches(imdb: String, season: Int, episode: Int): Boolean =
            optString("imdb_id") == imdb && optInt("season", -1) == season && optInt("episode", -1) == episode

        private fun JSONObject.msInterval(source: IntroSource): IntroInterval? {
            val start = optDouble("start_ms", Double.NaN)
            val end = optDouble("end_ms", Double.NaN)
            if (start.isNaN() || end.isNaN() || start < 0 || end <= start) return null
            return IntroInterval(start.roundToLong(), end.roundToLong(), source, durationMatched = source == IntroSource.SKIPDB)
        }

        /**
         * TheIntroDB `/segments`: `intro`, `recap` and `outro` {start_ms, end_ms} or null;
         * `no_intro` true when the community confirmed the episode has none. The echoed ids must
         * be the requested ones.
         */
        fun parseIntroDb(body: String, imdb: String, season: Int, episode: Int): SourceAnswer = runCatching {
            val json = JSONObject(body)
            if (!json.matches(imdb, season, episode)) return@runCatching SourceAnswer.NONE
            SourceAnswer(
                segments = EpisodeSegments(
                    intro = json.optJSONObject("intro")?.msInterval(IntroSource.INTRODB),
                    recap = json.optJSONObject("recap")?.msInterval(IntroSource.INTRODB),
                    credits = json.optJSONObject("outro")?.msInterval(IntroSource.INTRODB),
                ),
                confirmedNoIntro = json.optJSONObject("intro") == null && json.optBoolean("no_intro"),
            )
        }.getOrDefault(SourceAnswer.NONE)

        /**
         * SkipDB `/api/segments`: `segments.intro|recap|outro` {start_ms, end_ms, match,
         * confidence}. Only "exact" or "shifted" matches against the duration we sent are used;
         * "out-of-range" comes from a different release.
         */
        fun parseSkipDb(body: String, imdb: String, season: Int, episode: Int): SourceAnswer = runCatching {
            val json = JSONObject(body)
            if (!json.matches(imdb, season, episode)) return@runCatching SourceAnswer.NONE
            val segments = json.optJSONObject("segments") ?: return@runCatching SourceAnswer.NONE
            fun part(name: String): IntroInterval? {
                val segment = segments.optJSONObject(name) ?: return null
                val match = segment.optString("match")
                if (match != "exact" && match != "shifted") return null
                if (segment.optDouble("confidence", 0.0) < SKIPDB_MIN_CONFIDENCE) return null
                return segment.msInterval(IntroSource.SKIPDB)
            }
            SourceAnswer(EpisodeSegments(intro = part("intro"), recap = part("recap"), credits = part("outro")))
        }.getOrDefault(SourceAnswer.NONE)

        /** AniSkip v2 intervals by kind: "op" opening, "recap", "ed" ending (credits), in ms. */
        fun parseAniSkipSegments(body: String): Map<SegmentKind, List<IntroInterval>> = runCatching {
            val json = JSONObject(body)
            if (!json.optBoolean("found")) return@runCatching emptyMap()
            val results = json.optJSONArray("results") ?: return@runCatching emptyMap()
            (0 until results.length()).mapNotNull { index ->
                val result = results.optJSONObject(index) ?: return@mapNotNull null
                val kind = when (result.optString("skipType")) {
                    "op" -> SegmentKind.INTRO
                    "recap" -> SegmentKind.RECAP
                    "ed" -> SegmentKind.CREDITS
                    else -> return@mapNotNull null
                }
                val interval = result.optJSONObject("interval") ?: return@mapNotNull null
                val start = interval.optDouble("startTime", Double.NaN)
                val end = interval.optDouble("endTime", Double.NaN)
                val length = result.optDouble("episodeLength", Double.NaN)
                if (start.isNaN() || end.isNaN() || length.isNaN()) return@mapNotNull null
                kind to IntroInterval(
                    startMs = (start * 1000).roundToLong(),
                    endMs = (end * 1000).roundToLong(),
                    source = IntroSource.ANISKIP,
                    episodeLengthMs = (length * 1000).roundToLong(),
                )
            }.groupBy({ it.first }, { it.second })
        }.getOrDefault(emptyMap())

        /** Opening ("op") intervals from an AniSkip v2 response, in milliseconds. */
        fun parseAniSkip(body: String): List<IntroInterval> = parseAniSkipSegments(body)[SegmentKind.INTRO].orEmpty()

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
