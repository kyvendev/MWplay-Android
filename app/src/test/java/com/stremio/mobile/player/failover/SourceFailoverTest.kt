package com.stremio.mobile.player.failover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFailoverTest {
    private data class Source(val key: String, val addon: String, val seeds: Int = 0)

    private val torrentioA = Source("t-a", "Torrentio", seeds = 50)
    private val torrentioB = Source("t-b", "Torrentio", seeds = 10)
    private val frostA = Source("f-a", "FrostStream", seeds = 0)
    private val frostB = Source("f-b", "Frost Stream", seeds = 3)
    private val bestCine = Source("b-a", "Best Cine")
    private val other = Source("o-a", "Comet")
    private val available = listOf(torrentioA, torrentioB, frostA, frostB, bestCine, other)
    private val bySeeds = compareByDescending<Source> { it.seeds }

    private var clock = 0L
    private fun session(config: SourceFailoverConfig = SourceFailoverConfig()) =
        SourceFailoverSession<Source>(config, keyOf = { it.key }, addonOf = { it.addon }, now = { clock })

    @Test
    fun firstSourceStartsNormally() {
        val session = session().apply { begin(torrentioA) }
        clock = 8_000
        assertEquals(8_000L, session.onStarted())
        assertTrue(session.started)
        assertEquals(AttemptOutcome.STARTED, session.attempts.single().outcome)
    }

    @Test
    fun timeoutSwitchesToTheNextAddonAndItStarts() {
        val session = session().apply { begin(torrentioA) }
        clock = 30_000
        val next = session.onFailed(AttemptOutcome.TIMEOUT, "não iniciou em 30 s", available, bySeeds)
        // Next addon in the order, its best stream; not another Torrentio stream.
        assertEquals(frostB, next)
        clock = 38_000
        assertEquals(8_000L, session.onStarted())
        assertEquals(listOf(AttemptOutcome.TIMEOUT, AttemptOutcome.STARTED), session.attempts.map { it.outcome })
        assertEquals(30_000L, session.attempts.first().elapsedMs)
    }

    @Test
    fun definitiveFailureSwitchesImmediately() {
        val session = session().apply { begin(torrentioA) }
        clock = 2_000
        assertEquals(frostB, session.onFailed(AttemptOutcome.FAILED, "formato incompatível", available, bySeeds))
        assertEquals(2_000L, session.attempts.first().elapsedMs)
    }

    @Test
    fun everySourceFailsAndTheChainEnds() {
        val session = session().apply { begin(torrentioA) }
        assertEquals(frostB, session.onFailed(AttemptOutcome.TIMEOUT, null, available, bySeeds))
        assertEquals(bestCine, session.onFailed(AttemptOutcome.FAILED, null, available, bySeeds))
        assertNull(session.onFailed(AttemptOutcome.TIMEOUT, null, available, bySeeds))
        assertFalse(session.isWaiting)
        assertEquals(3, session.attempts.size)
        // Unregistered addons are never picked automatically.
        assertFalse(session.attempts.any { it.addon == "Comet" })
    }

    @Test
    fun aStartedPlaybackIsNeverInterrupted() {
        val session = session().apply { begin(torrentioA) }
        session.onStarted()
        assertNull(session.onFailed(AttemptOutcome.TIMEOUT, null, available, bySeeds))
        assertNull("second start", session.onStarted())
        assertEquals(1, session.attempts.size)
    }

    @Test
    fun noSourceIsTriedTwiceAndDuplicatesAreIgnored() {
        val session = session().apply { begin(frostA) }
        val next = session.onFailed(AttemptOutcome.FAILED, null, available + frostA, bySeeds)
        // Starting from FrostStream: Torrentio comes first, then BestCine; FrostStream is not repeated.
        assertEquals(torrentioA, next)
        assertEquals(bestCine, session.onFailed(AttemptOutcome.FAILED, null, available, bySeeds))
        assertNull(session.onFailed(AttemptOutcome.FAILED, null, available, bySeeds))
        assertEquals(listOf("f-a", "t-a", "b-a"), session.attempts.map { it.key })
        // A late duplicate failure report changes nothing.
        assertNull(session.onFailed(AttemptOutcome.FAILED, null, available, bySeeds))
        assertEquals(3, session.attempts.size)
    }

    @Test
    fun cancellingStopsTheSession() {
        val session = session().apply { begin(torrentioA) }
        session.cancel()
        assertFalse(session.isWaiting)
        assertEquals(AttemptOutcome.CANCELLED, session.attempts.single().outcome)
        assertNull(session.onFailed(AttemptOutcome.TIMEOUT, null, available, bySeeds))
    }

    @Test
    fun disabledOrCappedConfigNeverSwitches() {
        assertNull(session(SourceFailoverConfig(enabled = false)).apply { begin(torrentioA) }
            .onFailed(AttemptOutcome.TIMEOUT, null, available, bySeeds))
        val capped = session(SourceFailoverConfig(maxAttempts = 2)).apply { begin(torrentioA) }
        assertEquals(frostB, capped.onFailed(AttemptOutcome.TIMEOUT, null, available, bySeeds))
        assertNull(capped.onFailed(AttemptOutcome.TIMEOUT, null, available, bySeeds))
    }

    @Test
    fun addonsAreMatchedByNormalizedName() {
        val config = SourceFailoverConfig()
        assertEquals(0, config.rankOf("Torrentio Brazuca"))
        assertEquals(1, config.rankOf("Frost Stream"))
        assertEquals(2, config.rankOf("BEST-CINE"))
        assertNull(config.rankOf("Comet"))
        assertNull(config.rankOf(""))
        assertEquals(30_000L, config.startTimeoutMs)
    }

    @Test
    fun startNeedsRealProgressNotJustOpeningOrSeeking() {
        val detector = PlaybackStartDetector()
        // Player opened and buffering: not started.
        assertFalse(detector.onUpdate(isPlaying = false, isBuffering = true, positionMs = 0, hasError = false))
        // Resume seek jumps to 10 min: not progress.
        assertFalse(detector.onUpdate(isPlaying = true, isBuffering = false, positionMs = 0, hasError = false))
        assertFalse(detector.onUpdate(isPlaying = true, isBuffering = false, positionMs = 600_000, hasError = false))
        assertFalse(detector.onUpdate(isPlaying = true, isBuffering = false, positionMs = 600_500, hasError = false))
        // Buffering again resets the step tracking.
        assertFalse(detector.onUpdate(isPlaying = true, isBuffering = true, positionMs = 600_500, hasError = false))
        assertFalse(detector.onUpdate(isPlaying = true, isBuffering = false, positionMs = 600_600, hasError = false))
        assertTrue(detector.onUpdate(isPlaying = true, isBuffering = false, positionMs = 601_200, hasError = false))
        assertTrue(detector.started)
        assertFalse("reported once", detector.onUpdate(isPlaying = true, isBuffering = false, positionMs = 602_000, hasError = false))
        detector.reset()
        assertFalse(detector.started)
    }

    @Test
    fun resumeWithoutTheStreamSheetStillFindsTheOtherAddons() {
        // "Continuar assistindo": the sheet list is empty, the core has every addon's streams.
        data class Candidate(val source: Source, val video: String)
        val ep2 = "series/tt1:1:2"
        val fromCore = listOf(Candidate(torrentioA, ep2), Candidate(frostB, ep2), Candidate(bestCine, ep2))
        val candidates = sameVideoStreams(ep2, fromCore, emptyList(), { it.video }) { it.source.key }
        val session = SourceFailoverSession<Candidate>(SourceFailoverConfig(), { it.source.key }, { it.source.addon }, { clock })
        session.begin(candidates.first())
        clock = 30_000
        assertEquals(frostB, session.onFailed(AttemptOutcome.TIMEOUT, null, candidates, compareByDescending { it.source.seeds })?.source)
    }

    @Test
    fun nextEpisodeAutoplayNeverUsesThePreviousEpisodesStreams() {
        // The sheet still lists episode 1; the core already has episode 2.
        val fromSheet = listOf("series/tt1:1:1" to frostB, "series/tt1:1:1" to bestCine)
        val fromCore = listOf("series/tt1:1:2" to torrentioA, "series/tt1:1:2" to frostA, "series/tt1:1:2" to frostA)
        val candidates = sameVideoStreams("series/tt1:1:2", fromCore, fromSheet, { it.first }) { it.second.key }
        assertEquals(listOf(torrentioA, frostA), candidates.map { it.second })
    }
}
