package com.stremio.mobile.player.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroSkipTest {
    private val duration = 1_440_000L // 24 min
    private val opening = IntroInterval(startMs = 60_000, endMs = 150_000, source = IntroSource.INTRODB)
    private val manualMode = IntroSkipSettings(enabled = true, autoSkip = false)
    private val autoMode = IntroSkipSettings(enabled = true, autoSkip = true)

    private fun tracker(key: String = "series|tt1|tt1:1:1") = IntroSkipTracker().apply { bind(key) }

    @Test
    fun defaultsAreOnAndManual() {
        assertEquals(IntroSkipSettings(enabled = true, autoSkip = false), IntroSkipSettings())
    }

    @Test
    fun buttonAppearsOnlyInsideTheOpening() {
        val tracker = tracker()
        assertEquals(IntroSkipDecision.None, tracker.decide(59_000, duration, opening, manualMode))
        assertEquals(IntroSkipDecision.ShowButton(150_000), tracker.decide(60_000, duration, opening, manualMode))
        assertEquals(IntroSkipDecision.ShowButton(150_000), tracker.decide(120_000, duration, opening, manualMode))
        // Hidden in the last second and after the end.
        assertEquals(IntroSkipDecision.None, tracker.decide(149_500, duration, opening, manualMode))
        assertEquals(IntroSkipDecision.None, tracker.decide(200_000, duration, opening, manualMode))
    }

    @Test
    fun manualSkipHidesTheButtonAndNeverSkipsTwice() {
        val tracker = tracker()
        assertTrue(tracker.decide(70_000, duration, opening, manualMode) is IntroSkipDecision.ShowButton)
        assertTrue(tracker.markSkipped(automatic = false))
        assertEquals(IntroSkipDecision.None, tracker.decide(71_000, duration, opening, manualMode))
        assertFalse("double tap", tracker.markSkipped(automatic = false))
        // Seeking back into the opening offers the button again.
        tracker.decide(150_500, duration, opening, manualMode)
        assertEquals(IntroSkipDecision.ShowButton(150_000), tracker.decide(90_000, duration, opening, manualMode))
    }

    @Test
    fun automaticModeSkipsOncePerEpisode() {
        val tracker = tracker()
        assertEquals(IntroSkipDecision.AutoSkip(150_000), tracker.decide(61_000, duration, opening, autoMode))
        assertTrue(tracker.markSkipped(automatic = true))
        tracker.decide(150_200, duration, opening, autoMode)
        // The user went back on purpose: offer the button instead of jumping again.
        assertEquals(IntroSkipDecision.ShowButton(150_000), tracker.decide(80_000, duration, opening, autoMode))
    }

    @Test
    fun disabledOrMissingDataNeverShowsAnything() {
        assertEquals(IntroSkipDecision.None, tracker().decide(70_000, duration, opening, manualMode.copy(enabled = false)))
        assertEquals(IntroSkipDecision.None, tracker().decide(70_000, duration, null, autoMode))
    }

    @Test
    fun episodeChangeStartsClean() {
        val tracker = tracker("series|tt1|tt1:1:1")
        tracker.decide(70_000, duration, opening, autoMode)
        tracker.markSkipped(automatic = true)
        tracker.bind("series|tt1|tt1:1:2")
        assertEquals(IntroSkipDecision.AutoSkip(150_000), tracker.decide(70_000, duration, opening, autoMode))
    }

    @Test
    fun rejectsIntervalsIncompatibleWithTheVideo() {
        assertTrue(IntroSkipPolicy.isReliable(opening, duration))
        assertFalse("unknown duration", IntroSkipPolicy.isReliable(opening, 0))
        assertFalse("too short", IntroSkipPolicy.isReliable(opening.copy(endMs = 62_000), duration))
        assertFalse("too long", IntroSkipPolicy.isReliable(opening.copy(endMs = 400_000), duration))
        assertFalse("past the end", IntroSkipPolicy.isReliable(opening.copy(startMs = 1_400_000, endMs = 1_460_000), duration))
        assertFalse("second half", IntroSkipPolicy.isReliable(opening.copy(startMs = 800_000, endMs = 880_000), duration))
        val aniSkip = IntroInterval(28_783, 118_783, IntroSource.ANISKIP, episodeLengthMs = 1_500_040)
        assertTrue(IntroSkipPolicy.isReliable(aniSkip, 1_500_000))
        assertFalse("another release", IntroSkipPolicy.isReliable(aniSkip, 1_620_000))
        assertFalse("no release length", IntroSkipPolicy.isReliable(aniSkip.copy(episodeLengthMs = null), 1_500_000))
        assertEquals(IntroSkipDecision.None, tracker().decide(400_000, 600_000, opening.copy(endMs = 590_000), manualMode))
    }

    @Test
    fun picksTheClosestRelease() {
        val far = IntroInterval(10_000, 100_000, IntroSource.ANISKIP, episodeLengthMs = 1_508_000)
        val close = IntroInterval(20_000, 110_000, IntroSource.ANISKIP, episodeLengthMs = 1_500_500)
        assertEquals(close, IntroSkipPolicy.select(listOf(far, close), 1_500_000))
        assertNull(IntroSkipPolicy.select(listOf(far, close), 2_000_000))
    }

    @Test
    fun identifiesEpisodesById() {
        assertNull("movie", EpisodeRef.of("movie", "tt9", "tt9"))
        assertNull("no episode", EpisodeRef.of("series", "tt1", "tt1"))
        assertEquals(Triple("tt0944947", 1, 1), EpisodeRef.of("series", "tt0944947", "tt0944947:1:1")!!.imdbEpisode)
        assertEquals("specials", Triple("tt0944947", 0, 3), EpisodeRef.of("series", "tt0944947", "tt0944947:0:3")!!.imdbEpisode)
        assertNull("other show", EpisodeRef.of("series", "tt0944947", "tt0903747:1:1")!!.imdbEpisode)
        assertNull("invalid id", EpisodeRef.of("series", "tt1", "tt1:x:1")!!.imdbEpisode)
        assertNull("bad imdb", EpisodeRef.of("series", "foo", "foo:1:1")!!.imdbEpisode)
        assertEquals(Triple("kitsu", "12", 5), EpisodeRef.of("series", "kitsu:12", "kitsu:12:5")!!.animeIds)
        assertNull(EpisodeRef.of("series", "kitsu:12", "kitsu:12:5")!!.imdbEpisode)
    }
}
