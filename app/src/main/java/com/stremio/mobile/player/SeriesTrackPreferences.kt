package com.stremio.mobile.player

import android.content.Context
import org.json.JSONObject

/**
 * Audio and subtitle language the user picked on an episode of a series. The next episodes of
 * the same series start that way, without opening the menus again.
 */
data class SeriesTrackPreference(
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    /** The user turned subtitles off. */
    val subtitlesOff: Boolean = false,
)

/** One entry per series ("type|id"), on the device. */
class SeriesTrackPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("mwplay_series_tracks", Context.MODE_PRIVATE)

    fun get(seriesKey: String): SeriesTrackPreference? = runCatching {
        val json = JSONObject(preferences.getString(seriesKey, null) ?: return null)
        SeriesTrackPreference(
            audioLanguage = json.optString("audio").ifBlank { null },
            subtitleLanguage = json.optString("subtitle").ifBlank { null },
            subtitlesOff = json.optBoolean("subtitlesOff"),
        )
    }.getOrNull()

    fun saveAudio(seriesKey: String, language: String?) {
        if (language.isNullOrBlank()) return
        save(seriesKey, (get(seriesKey) ?: SeriesTrackPreference()).copy(audioLanguage = language))
    }

    /** [language] null = subtitles off. */
    fun saveSubtitle(seriesKey: String, language: String?) {
        val current = get(seriesKey) ?: SeriesTrackPreference()
        save(seriesKey, if (language == null) current.copy(subtitleLanguage = null, subtitlesOff = true) else current.copy(subtitleLanguage = language, subtitlesOff = false))
    }

    private fun save(seriesKey: String, value: SeriesTrackPreference) {
        val json = JSONObject()
            .put("audio", value.audioLanguage.orEmpty())
            .put("subtitle", value.subtitleLanguage.orEmpty())
            .put("subtitlesOff", value.subtitlesOff)
        preferences.edit().putString(seriesKey, json.toString()).apply()
    }
}

/** What to do with the subtitles when a video starts. */
sealed interface SubtitleDefault {
    data object Off : SubtitleDefault
    data class Select(val track: PlayerTrackOption) : SubtitleDefault
    /** The wanted track may still arrive (addon subtitles load later). */
    data object Wait : SubtitleDefault
}

/**
 * Track chosen when a video starts, in order: the track saved for this very stream (resuming the
 * same episode), the series preference, then the profile's languages.
 */
object TrackDefaults {
    fun audio(
        tracks: List<PlayerTrackOption>,
        savedTrackId: String?,
        series: SeriesTrackPreference?,
        profileLanguage: String?,
    ): PlayerTrackOption? {
        savedTrackId?.let { id -> tracks.firstOrNull { it.id == id } }?.let { return it }
        series?.audioLanguage?.let { language -> tracks.firstOrNull { it.matchesLanguage(language) } }?.let { return it }
        return tracks.firstOrNull { it.matchesLanguage(profileLanguage) }
    }

    fun subtitle(
        tracks: List<PlayerTrackOption>,
        savedId: String?,
        savedEmbedded: Boolean,
        savedLanguage: String?,
        series: SeriesTrackPreference?,
        settingsLanguage: String?,
        autoSelect: Boolean,
        originPriority: (PlayerTrackOption) -> Int,
    ): SubtitleDefault {
        val sameStream = savedId != null || savedLanguage != null
        if (!sameStream && series != null && (series.subtitlesOff || series.subtitleLanguage != null)) {
            if (series.subtitlesOff) return SubtitleDefault.Off
            return bestFor(tracks, series.subtitleLanguage, originPriority)?.let { SubtitleDefault.Select(it) } ?: SubtitleDefault.Wait
        }
        if (settingsLanguage == null || !autoSelect) return SubtitleDefault.Off
        if (tracks.isEmpty()) return SubtitleDefault.Wait
        val savedById = savedId?.let { id -> tracks.firstOrNull { it.id == id && it.embedded == savedEmbedded } }
        val track = savedById ?: bestFor(tracks, savedLanguage ?: settingsLanguage, originPriority)
        return track?.let { SubtitleDefault.Select(it) } ?: SubtitleDefault.Wait
    }

    private fun bestFor(tracks: List<PlayerTrackOption>, language: String?, originPriority: (PlayerTrackOption) -> Int): PlayerTrackOption? =
        tracks.filter { it.matchesLanguage(language) }
            .sortedWith(compareByDescending<PlayerTrackOption> { it.embedded }.thenBy(originPriority).thenBy { it.label })
            .firstOrNull()

    private fun PlayerTrackOption.matchesLanguage(language: String?) = LanguageCatalog.matches(languageCode ?: this.language, language)
}
