package com.stremio.mobile.player

/** Playback requests survive MPV initialization and asynchronous file loading. */
internal class MpvPlaybackIntent {
    var desiredPaused: Boolean = false
        private set

    var pendingSeekMs: Long? = null
        private set

    fun prepareLoad(startPositionMs: Long) {
        desiredPaused = false
        pendingSeekMs = startPositionMs.coerceAtLeast(0L).takeIf { it > 0L }
    }

    fun prepareRetry(currentPositionMs: Long) {
        // The native position can still be zero while a requested seek is waiting for FILE_LOADED.
        if (pendingSeekMs == null) {
            pendingSeekMs = currentPositionMs.coerceAtLeast(0L).takeIf { it > 0L }
        }
    }

    fun requestPaused(paused: Boolean) {
        desiredPaused = paused
    }

    fun requestSeek(positionMs: Long) {
        // Zero is an explicit request and must override a previous nonzero start position.
        pendingSeekMs = positionMs.coerceAtLeast(0L)
    }

    fun applyPendingSeek(apply: (Long) -> Unit) {
        val positionMs = pendingSeekMs ?: return
        apply(positionMs)
        // Clear only after a successful native call; retain a newer request if one arrived.
        if (pendingSeekMs == positionMs) pendingSeekMs = null
    }
}

