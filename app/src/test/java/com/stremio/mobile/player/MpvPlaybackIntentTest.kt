package com.stremio.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvPlaybackIntentTest {
    @Test
    fun `pause before surface initialization remains requested when file loads`() {
        val intent = MpvPlaybackIntent()
        intent.prepareLoad(12_000L)
        intent.requestPaused(true)
        val applied = mutableListOf<Long>()
        intent.applyPendingSeek(applied::add)
        assertTrue(intent.desiredPaused)
        assertEquals(listOf(12_000L), applied)
    }

    @Test
    fun `handback seek before FILE_LOADED overrides the original start`() {
        val intent = MpvPlaybackIntent()
        intent.prepareLoad(12_000L)
        intent.requestSeek(45_000L)
        val applied = mutableListOf<Long>()
        intent.applyPendingSeek(applied::add)
        assertEquals(listOf(45_000L), applied)
        assertNull(intent.pendingSeekMs)
    }

    @Test
    fun `explicit zero seek overrides a nonzero original start`() {
        val intent = MpvPlaybackIntent()
        intent.prepareLoad(12_000L)
        intent.requestSeek(0L)
        val applied = mutableListOf<Long>()
        intent.applyPendingSeek(applied::add)
        assertEquals(listOf(0L), applied)
    }

    @Test
    fun `latest pause play and seek requests win during loading`() {
        val intent = MpvPlaybackIntent()
        intent.prepareLoad(0L)
        intent.requestPaused(true)
        intent.requestSeek(10_000L)
        intent.requestPaused(false)
        intent.requestSeek(20_000L)
        val applied = mutableListOf<Long>()
        intent.applyPendingSeek(applied::add)
        assertFalse(intent.desiredPaused)
        assertEquals(listOf(20_000L), applied)
    }

    @Test
    fun `retry preserves paused handback intent despite a stale native position`() {
        val intent = MpvPlaybackIntent()
        intent.prepareLoad(12_000L)
        intent.requestPaused(true)
        intent.requestSeek(45_000L)
        intent.prepareRetry(0L)
        val applied = mutableListOf<Long>()
        intent.applyPendingSeek(applied::add)
        assertTrue(intent.desiredPaused)
        assertEquals(listOf(45_000L), applied)
    }

    @Test
    fun `new load clears commands from the previous video and restores autoplay`() {
        val intent = MpvPlaybackIntent()
        intent.prepareLoad(12_000L)
        intent.requestPaused(true)
        intent.requestSeek(45_000L)
        intent.prepareLoad(3_000L)
        val applied = mutableListOf<Long>()
        intent.applyPendingSeek(applied::add)
        assertFalse(intent.desiredPaused)
        assertEquals(listOf(3_000L), applied)
    }

    @Test
    fun `loaded position is applied once and retry resumes at the current position`() {
        val intent = MpvPlaybackIntent()
        intent.prepareLoad(12_000L)
        val applied = mutableListOf<Long>()
        intent.applyPendingSeek(applied::add)
        intent.applyPendingSeek(applied::add)
        intent.prepareRetry(30_000L)
        intent.applyPendingSeek(applied::add)
        assertEquals(listOf(12_000L, 30_000L), applied)
    }

    @Test
    fun `native view recreation preserves paused zero seek instead of old playback position`() {
        val intent = MpvPlaybackIntent()
        intent.prepareLoad(12_000L)
        intent.requestPaused(true)
        intent.requestSeek(0L)
        intent.prepareRetry(30_000L)
        val applied = mutableListOf<Long>()
        intent.applyPendingSeek(applied::add)
        assertTrue(intent.desiredPaused)
        assertEquals(listOf(0L), applied)
    }

    @Test
    fun `native seek failure keeps its pending request for retry`() {
        val intent = MpvPlaybackIntent()
        intent.prepareLoad(0L)
        intent.requestSeek(45_000L)
        runCatching { intent.applyPendingSeek { error("MPV not ready") } }
        intent.prepareRetry(0L)
        val applied = mutableListOf<Long>()
        intent.applyPendingSeek(applied::add)
        assertEquals(listOf(45_000L), applied)
    }
}

