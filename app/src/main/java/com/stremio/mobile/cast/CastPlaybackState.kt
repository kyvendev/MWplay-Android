package com.stremio.mobile.cast

data class CastSubtitle(val id: Long, val label: String, val language: String?, val selected: Boolean)
data class CastLocalResume(val localUri: String, val positionMs: Long, val durationMs: Long, val playing: Boolean)

data class CastPlaybackState(
    val ready: Boolean = false,
    val initializing: Boolean = true,
    val devicesAvailable: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val suspended: Boolean = false,
    val deviceName: String? = null,
    val mediaUrl: String? = null,
    val localUri: String? = null,
    val title: String? = null,
    val loading: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val subtitles: List<CastSubtitle> = emptyList(),
    val error: String? = null,
    val localResume: CastLocalResume? = null,
) {
    fun owns(localPlaybackUri: String?): Boolean = localPlaybackUri != null &&
        localUri == localPlaybackUri && mediaUrl != null && (connected || suspended)
}

