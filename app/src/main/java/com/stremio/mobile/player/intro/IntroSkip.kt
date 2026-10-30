package com.stremio.mobile.player.intro

import kotlin.math.abs

/** Which public database an opening interval came from. */
enum class IntroSource(val label: String) { INTRODB("TheIntroDB"), ANISKIP("AniSkip"), SKIPDB("SkipDB") }

/**
 * An opening interval for one episode. [episodeLengthMs] is the length of the release the
 * timestamps were measured on when the source reports it (AniSkip); it lets us reject data from
 * another cut. [durationMatched] is true when the source itself matched the playing duration
 * (SkipDB "exact"/"shifted").
 */
data class IntroInterval(
    val startMs: Long,
    val endMs: Long,
    val source: IntroSource,
    val episodeLengthMs: Long? = null,
    val durationMatched: Boolean = false,
)

/** "Pular abertura" preferences: on by default, manual (button) mode by default. */
data class IntroSkipSettings(
    val enabled: Boolean = true,
    val autoSkip: Boolean = false,
)

/**
 * The episode being played. Movies never get an [EpisodeRef].
 * - Cinemeta-style ids `tt0944947:1:1` give (IMDb id, season, episode) for TheIntroDB/SkipDB.
 * - Anime addon ids `kitsu:12:5` / `mal:21:5` give (provider, show id, episode) for AniSkip.
 */
data class EpisodeRef(val type: String, val itemId: String, val videoId: String) {
    val episodeKey: String get() = "$type|$itemId|$videoId"

    /** (IMDb id, season, episode) for IMDb episode ids, null otherwise. Season 0 = specials. */
    val imdbEpisode: Triple<String, Int, Int>? get() {
        val parts = videoId.split(':')
        if (parts.size != 3) return null
        val imdb = parts[0].takeIf { IMDB_ID.matches(it) } ?: return null
        // The episode must belong to the show being played.
        if (itemId != imdb) return null
        val season = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val episode = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return Triple(imdb, season, episode)
    }

    /** (provider, show id, episode number) for anime ids, null otherwise. */
    val animeIds: Triple<String, String, Int>? get() {
        val parts = videoId.split(':')
        if (parts.size < 3) return null
        val provider = parts[0].lowercase()
        if (provider != "kitsu" && provider != "mal") return null
        val episode = parts.last().toIntOrNull()?.takeIf { it > 0 } ?: return null
        val showId = parts[1].takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return null
        return Triple(provider, showId, episode)
    }

    companion object {
        private val IMDB_ID = Regex("tt\\d{5,10}")

        /** Null for movies and for items without a distinct episode id. */
        fun of(type: String?, itemId: String?, videoId: String?): EpisodeRef? {
            if (type.isNullOrBlank() || itemId.isNullOrBlank() || videoId.isNullOrBlank()) return null
            if (type.equals("movie", ignoreCase = true) || videoId == itemId) return null
            return EpisodeRef(type, itemId, videoId)
        }
    }
}

object IntroSkipPolicy {
    const val MIN_INTRO_MS = 5_000L
    const val MAX_INTRO_MS = 4 * 60_000L

    /** How far the measured release may differ from the playing video before data is rejected. */
    const val EPISODE_LENGTH_TOLERANCE_MS = 10_000L

    /** An opening must end at least this long before the video does. */
    const val MIN_TAIL_MS = 60_000L

    /** The button disappears this close to the end of the opening. */
    const val END_MARGIN_MS = 1_000L

    /** True when [interval] makes sense for a video of [durationMs]; unknown duration = unreliable. */
    fun isReliable(interval: IntroInterval, durationMs: Long): Boolean {
        val length = interval.endMs - interval.startMs
        if (interval.startMs < 0 || length !in MIN_INTRO_MS..MAX_INTRO_MS) return false
        if (durationMs <= 0 || interval.endMs > durationMs - MIN_TAIL_MS) return false
        // The opening sits in the first part of an episode, never in its second half.
        if (interval.startMs > durationMs / 2) return false
        interval.episodeLengthMs?.let { measured ->
            // Community timestamps are tied to one release: only use them when it matches.
            if (abs(measured - durationMs) > EPISODE_LENGTH_TOLERANCE_MS) return false
        }
        if (interval.source == IntroSource.ANISKIP && interval.episodeLengthMs == null) return false
        return true
    }

    /** The reliable candidate whose measured release is closest to the playing video. */
    fun select(candidates: List<IntroInterval>, durationMs: Long): IntroInterval? =
        candidates.filter { isReliable(it, durationMs) }
            .minByOrNull { candidate -> candidate.episodeLengthMs?.let { abs(it - durationMs) } ?: 0L }
}

sealed interface IntroSkipDecision {
    data object None : IntroSkipDecision
    data class ShowButton(val skipToMs: Long) : IntroSkipDecision
    data class AutoSkip(val skipToMs: Long) : IntroSkipDecision
}

/**
 * Per-episode state of the skip button, driven by the player's existing position updates (no
 * extra polling). After a skip the decision stays [IntroSkipDecision.None] until the position
 * leaves the opening, so late position updates never show the button again or jump twice. A
 * user seek back into the opening shows the button again; automatic skipping happens once.
 */
class IntroSkipTracker {
    private var key: String? = null
    private var skippedInside = false
    private var autoSkipUsed = false

    /** Clears everything when a different episode (or stream) starts. */
    fun bind(episodeKey: String?) {
        if (episodeKey == key) return
        key = episodeKey
        skippedInside = false
        autoSkipUsed = false
    }

    fun decide(positionMs: Long, durationMs: Long, interval: IntroInterval?, settings: IntroSkipSettings): IntroSkipDecision {
        if (!settings.enabled || interval == null || !IntroSkipPolicy.isReliable(interval, durationMs)) return IntroSkipDecision.None
        val inside = positionMs >= interval.startMs && positionMs < interval.endMs - IntroSkipPolicy.END_MARGIN_MS
        if (!inside) {
            skippedInside = false
            return IntroSkipDecision.None
        }
        if (skippedInside) return IntroSkipDecision.None
        if (settings.autoSkip && !autoSkipUsed) return IntroSkipDecision.AutoSkip(interval.endMs)
        return IntroSkipDecision.ShowButton(interval.endMs)
    }

    /** Records a skip; returns false when it was already done (double tap, repeated event). */
    fun markSkipped(automatic: Boolean): Boolean {
        if (skippedInside) return false
        skippedInside = true
        if (automatic) autoSkipUsed = true
        return true
    }
}

/**
 * Opening data for the episode on screen. [interval] stays null while the lookup runs (playback
 * is never held for it) and when no source had valid data.
 */
data class PlaybackIntro(val ref: EpisodeRef, val interval: IntroInterval? = null, val lookupDone: Boolean = false)
