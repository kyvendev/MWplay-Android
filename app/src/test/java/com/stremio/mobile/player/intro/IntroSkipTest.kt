package com.stremio.mobile.player.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroSkipTest {
    private val duration = 1_440_000L // 24 min
    private val manual = IntroInterval(startMs = 60_000, endMs = 150_000, source = IntroSource.MANUAL_EPISODE)
    private val manualButton = IntroSkipSettings(enabled = true, showButton = true, autoSkip = false)

    private fun tracker(key: String = "series|tt1|tt1:1:1") = IntroSkipTracker().apply { bind(key) }

    @Test
    fun buttonAppearsOnlyInsideTheOpening() {
        val tracker = tracker()
        assertEquals(IntroSkipDecision.None, tracker.decide(59_000, duration, manual, manualButton))
        assertEquals(IntroSkipDecision.ShowButton(150_000), tracker.decide(60_000, duration, manual, manualButton))
        assertEquals(IntroSkipDecision.ShowButton(150_000), tracker.decide(120_000, duration, manual, manualButton))
        // Hidden in the last second and after the end.
        assertEquals(IntroSkipDecision.None, tracker.decide(149_500, duration, manual, manualButton))
        assertEquals(IntroSkipDecision.None, tracker.decide(200_000, duration, manual, manualButton))
    }

    @Test
    fun buttonDisappearsAfterTheSkipAndNeverSkipsTwice() {
        val tracker = tracker()
        assertTrue(tracker.decide(70_000, duration, manual, manualButton) is IntroSkipDecision.ShowButton)
        assertTrue(tracker.markSkipped(automatic = false))
        // A late position update from before the seek must not bring the button back.
        assertEquals(IntroSkipDecision.None, tracker.decide(71_000, duration, manual, manualButton))
        // A second tap / repeated event is ignored.
        assertFalse(tracker.markSkipped(automatic = false))
        assertEquals(IntroSkipDecision.None, tracker.decide(150_000, duration, manual, manualButton))
    }

    @Test
    fun seekingBackIntoTheOpeningShowsTheButtonAgain() {
        val tracker = tracker()
        tracker.decide(70_000, duration, manual, manualButton)
        tracker.markSkipped(automatic = false)
        tracker.decide(150_500, duration, manual, manualButton) // after the jump
        assertEquals(IntroSkipDecision.ShowButton(150_000), tracker.decide(90_000, duration, manual, manualButton))
    }

    @Test
    fun automaticSkipRespectsTheSettingAndHappensOnce() {
        val auto = manualButton.copy(autoSkip = true)
        val tracker = tracker()
        assertEquals(IntroSkipDecision.AutoSkip(150_000), tracker.decide(60_500, duration, manual, auto))
        tracker.markSkipped(automatic = true)
        tracker.decide(150_200, duration, manual, auto)
        // The user went back on purpose: offer the button instead of jumping again.
        assertEquals(IntroSkipDecision.ShowButton(150_000), tracker.decide(80_000, duration, manual, auto))
        // With automatic skipping off, only the button is offered.
        assertEquals(IntroSkipDecision.ShowButton(150_000), tracker().decide(60_500, duration, manual, manualButton))
    }

    @Test
    fun disabledFeatureOrHiddenButtonShowsNothing() {
        assertEquals(IntroSkipDecision.None, tracker().decide(70_000, duration, manual, manualButton.copy(enabled = false)))
        assertEquals(IntroSkipDecision.None, tracker().decide(70_000, duration, manual, manualButton.copy(showButton = false)))
    }

    @Test
    fun episodesWithoutTimestampsAreNeverSkipped() {
        val auto = manualButton.copy(autoSkip = true)
        assertEquals(IntroSkipDecision.None, tracker().decide(70_000, duration, null, auto))
        assertNull(IntroSkipPolicy.select(emptyList(), duration))
    }

    @Test
    fun moviesHaveNoEpisodeAndSoNoButton() {
        assertNull(EpisodeRef.of("movie", "tt0133093", "tt0133093"))
        assertNull(EpisodeRef.of("series", "tt1", "tt1"))
        assertEquals("tt1:1:2", EpisodeRef.of("series", "tt1", "tt1:1:2")?.videoId)
    }

    @Test
    fun changingEpisodeClearsThePreviousState() {
        val tracker = tracker("series|tt1|tt1:1:1")
        tracker.decide(70_000, duration, manual, manualButton)
        tracker.markSkipped(automatic = true)
        tracker.bind("series|tt1|tt1:1:2")
        assertEquals(IntroSkipDecision.AutoSkip(150_000), tracker.decide(70_000, duration, manual, manualButton.copy(autoSkip = true)))
    }

    @Test
    fun aniSkipDataMustMatchThePlayingRelease() {
        val aniSkip = IntroInterval(28_783, 118_783, IntroSource.ANISKIP, episodeLengthMs = 1_500_040)
        assertTrue(IntroSkipPolicy.isReliable(aniSkip, 1_500_000))
        // Another cut of the episode (e.g. 2 minutes longer): not trusted.
        assertFalse(IntroSkipPolicy.isReliable(aniSkip, 1_620_000))
        // Unknown duration yet: wait instead of guessing.
        assertFalse(IntroSkipPolicy.isReliable(aniSkip, 0))
        assertFalse(IntroSkipPolicy.isReliable(aniSkip.copy(episodeLengthMs = null), 1_500_000))
    }

    @Test
    fun implausibleIntervalsAreRejected() {
        assertFalse(IntroSkipPolicy.isReliable(manual.copy(endMs = 62_000), duration)) // 2 s
        assertFalse(IntroSkipPolicy.isReliable(manual.copy(endMs = 60_000 + 6 * 60_000), duration)) // 6 min
        assertFalse(IntroSkipPolicy.isReliable(manual.copy(startMs = 90_000, endMs = 60_000), duration))
        assertFalse(IntroSkipPolicy.isReliable(manual.copy(endMs = 2_000_000), duration)) // past the end
    }

    @Test
    fun manualMarksWinAndTheClosestAniSkipReleaseIsChosen() {
        val far = IntroInterval(10_000, 100_000, IntroSource.ANISKIP, episodeLengthMs = 1_508_000)
        val close = IntroInterval(20_000, 110_000, IntroSource.ANISKIP, episodeLengthMs = 1_500_500)
        assertEquals(close, IntroSkipPolicy.select(listOf(far, close), 1_500_000))
        val series = IntroInterval(30_000, 120_000, IntroSource.MANUAL_SERIES)
        assertEquals(series, IntroSkipPolicy.select(listOf(series, far, close), 1_500_000))
    }

    @Test
    fun animeIdsAreParsedForAniSkip() {
        assertEquals(Triple("kitsu", "12", 5), EpisodeRef("series", "kitsu:12", "kitsu:12:5").animeIds)
        assertEquals(Triple("mal", "21", 1), EpisodeRef("anime", "mal:21", "mal:21:1").animeIds)
        assertNull(EpisodeRef("series", "tt1", "tt1:1:2").animeIds)
    }
}
