package com.stremio.mobile.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CastPlaybackStateTest {
    private val remoteUrl = "https://media.example/movie.mp4"
    private val localProxyUrl = "http://127.0.0.1:11470/proxy/movie"
    private val casting = CastPlaybackState(
        ready = true, connected = true, mediaUrl = remoteUrl, localUri = localProxyUrl,
    )

    @Test fun originalRemoteUrlCanOwnItsSeparateLocalProxyPlayback() {
        assertTrue(casting.owns(localProxyUrl))
        assertFalse(casting.owns(remoteUrl))
    }

    @Test fun selectingAnotherVideoDoesNotPauseItOrUseOldCastProgress() {
        assertFalse(casting.owns("https://media.example/another.mp4"))
        assertFalse(casting.owns(null))
    }

    @Test fun suspendedConnectionKeepsLocalPlaybackPausedUntilReconnectOrSessionEnd() {
        assertTrue(casting.copy(connected = false, suspended = true, connecting = true).owns(localProxyUrl))
        assertTrue(casting.copy(connecting = true).owns(localProxyUrl))
    }

    @Test fun endedSessionAllowsThePhoneToResume() {
        assertFalse(casting.copy(connected = false, suspended = false).owns(localProxyUrl))
        assertFalse(CastPlaybackState(localResume = CastLocalResume(localProxyUrl, 120000, 3600000, true)).owns(localProxyUrl))
    }

    @Test fun anEmptyOrUnownedReceiverSessionCannotHideTheLocalPlayer() {
        assertFalse(casting.copy(mediaUrl = null).owns(localProxyUrl))
        assertFalse(casting.copy(localUri = null).owns(localProxyUrl))
        assertFalse(CastPlaybackState(connected = true).owns(localProxyUrl))
    }

    @Test fun reattachingCanCaptureReceiverPositionBeforeItsNextProgressCallback() {
        val receiver = casting.copy(localUri = null, positionMs = 120000, durationMs = 3600000, playing = true)
        assertEquals(
            CastLocalResume(localProxyUrl, 120000, 3600000, true),
            receiver.snapshotForLocalPlayback(localProxyUrl),
        )
    }

    @Test fun sessionEndWhileThePhoneIsLockedKeepsResumePendingUntilForeground() {
        val resume = CastLocalResume(localProxyUrl, 120000, 3600000, true)
        val stopped = CastPlaybackState(localResume = resume)
        assertNull(stopped.pendingLocalResume(localProxyUrl, isForeground = false, allowBackgroundPlayback = false))
        assertSame(resume, stopped.localResume)
        assertSame(resume, stopped.pendingLocalResume(localProxyUrl, isForeground = true, allowBackgroundPlayback = false))
    }

    @Test fun backgroundResumeRequiresThePreferenceAndStillMatchesTheSelectedVideo() {
        val resume = CastLocalResume(localProxyUrl, 120000, 3600000, true)
        val stopped = CastPlaybackState(localResume = resume)
        assertSame(resume, stopped.pendingLocalResume(localProxyUrl, isForeground = false, allowBackgroundPlayback = true))
        assertNull(stopped.pendingLocalResume(remoteUrl, isForeground = true, allowBackgroundPlayback = true))
    }
}
