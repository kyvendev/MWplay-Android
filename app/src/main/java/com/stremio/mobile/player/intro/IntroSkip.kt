package com.stremio.mobile.player.intro

import kotlin.math.abs

/** Where an opening interval came from. Manual marks win over automatic data. */
enum class IntroSource { MANUAL_EPISODE, MANUAL_SERIES, ANISKIP }

/**
 * An opening interval for one episode. [episodeLengthMs] is the length of the release the
 * timestamps were measured on (AniSkip reports it); it lets us reject data from another cut.
 */
data class IntroInterval(
    val startMs: Long,
    val endMs: Long,
    val source: IntroSource,
    val episodeLengthMs: Long? = null,
)

/** "Pular abertura" preferences. Automatic skipping is opt-in. */
data class IntroSkipSettings(
    val enabled: Boolean = true,
    val showButton: Boolean = true,
    val autoSkip: Boolean = false,
)

/**
 * The episode being played. Movies never get an [EpisodeRef]. Anime addons use ids such as
 * `kitsu:12:5` or `mal:21:5` (provider, show id, episode), which map to AniSkip.
 */
data class EpisodeRef(val type: String, val itemId: String, val videoId: String) {
    val episodeKey: String get() = "$type|$itemId|$videoId"
    val seriesKey: String get() = "$type|$itemId"

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

    /** The button disappears this close to the end of the opening. */
    const val END_MARGIN_MS = 1_000L

    /** True when [interval] makes sense for a video of [durationMs] (0 = unknown). */
    fun isReliable(interval: IntroInterval, durationMs: Long): Boolean {
        val length = interval.endMs - interval.startMs
        if (interval.startMs < 0 || length !in MIN_INTRO_MS..MAX_INTRO_MS) return false
        if (durationMs > 0 && interval.endMs > durationMs) return false
        if (interval.source == IntroSource.ANISKIP) {
            // Community timestamps are tied to one release: only use them when it matches.
            val measured = interval.episodeLengthMs ?: return false
            if (durationMs <= 0) return false
            if (abs(measured - durationMs) > EPISODE_LENGTH_TOLERANCE_MS) return false
        }
        return true
    }

    /** First reliable candidate, manual marks first (they come first from the repository). */
    fun select(candidates: List<IntroInterval>, durationMs: Long): IntroInterval? {
        val reliable = candidates.filter { isReliable(it, durationMs) }
        return reliable.firstOrNull { it.source != IntroSource.ANISKIP }
            ?: reliable.filter { it.source == IntroSource.ANISKIP }
                .minByOrNull { abs((it.episodeLengthMs ?: 0L) - durationMs) }
    }
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
        return if (settings.showButton) IntroSkipDecision.ShowButton(interval.endMs) else IntroSkipDecision.None
    }

    /** Records a skip; returns false when it was already done (double tap, repeated event). */
    fun markSkipped(automatic: Boolean): Boolean {
        if (skippedInside) return false
        skippedInside = true
        if (automatic) autoSkipUsed = true
        return true
    }
}

/** Opening data for the episode on screen: its reference plus every candidate interval found. */
data class PlaybackIntro(val ref: EpisodeRef, val candidates: List<IntroInterval>)
