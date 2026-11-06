package com.stremio.mobile.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodeRulesTest {
    private val got = NewEpisodes("tt0944947", "series", "Game of Thrones", 1)
    private val bb = NewEpisodes("tt0903747", "series", "Breaking Bad", 0)

    @Test
    fun firstRunOnlyRecordsWithoutABurstOfNotifications() {
        val result = NewEpisodeRules.decide(listOf(got, bb), emptyMap(), firstRun = true)
        assertTrue(result.toNotify.isEmpty())
        assertEquals(mapOf("tt0944947" to 1L), result.remembered)
    }

    @Test
    fun aNewEpisodeIsAnnouncedOnce() {
        val first = NewEpisodeRules.decide(listOf(got), emptyMap(), firstRun = false)
        assertEquals(listOf(got), first.toNotify)
        val again = NewEpisodeRules.decide(listOf(got), first.remembered, firstRun = false)
        assertTrue(again.toNotify.isEmpty())
    }

    @Test
    fun anotherEpisodeOfTheSameSeriesIsAnnouncedAgain() {
        val result = NewEpisodeRules.decide(listOf(got.copy(count = 2)), mapOf("tt0944947" to 1L), firstRun = false)
        assertEquals(listOf(got.copy(count = 2)), result.toNotify)
    }

    @Test
    fun watchedEpisodesAreForgottenSoTheNextOneNotifies() {
        val watched = NewEpisodeRules.decide(listOf(got.copy(count = 0)), mapOf("tt0944947" to 1L), firstRun = false)
        assertTrue(watched.toNotify.isEmpty())
        assertTrue(watched.remembered.isEmpty())
        assertEquals(listOf(got), NewEpisodeRules.decide(listOf(got), watched.remembered, firstRun = false).toNotify)
    }
}
