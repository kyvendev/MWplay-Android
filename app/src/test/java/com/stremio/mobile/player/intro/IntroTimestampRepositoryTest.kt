package com.stremio.mobile.player.intro

import android.app.Application
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** HTTP is a test double; responses are trimmed copies of real AniSkip/Kitsu answers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IntroTimestampRepositoryTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private val aniSkipOnePieceEp1 = """
        {"found":true,"results":[
          {"interval":{"startTime":28.783,"endTime":118.783},"skipType":"op","skipId":"a","episodeLength":1500.04},
          {"interval":{"startTime":1387.996,"endTime":1500},"skipType":"ed","skipId":"b","episodeLength":1500.146}
        ],"message":"Successfully found skip times","statusCode":200}
    """.trimIndent()
    private val kitsuOnePiece = """{"data":[{"id":"1175","type":"mappings","attributes":{"externalSite":"myanimelist/anime","externalId":"21"}}]}"""

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("mwplay_intro_skip", 0).edit().clear().commit()
    }

    @Test
    fun parsesOnlyOpeningsFromAniSkip() {
        val intervals = IntroTimestampRepository.parseAniSkip(aniSkipOnePieceEp1)
        assertEquals(listOf(IntroInterval(28_783, 118_783, IntroSource.ANISKIP, episodeLengthMs = 1_500_040)), intervals)
        assertTrue(IntroTimestampRepository.parseAniSkip("""{"found":false,"results":[]}""").isEmpty())
        assertTrue(IntroTimestampRepository.parseAniSkip("not json").isEmpty())
    }

    @Test
    fun parsesTheMyAnimeListIdFromKitsu() {
        assertEquals("21", IntroTimestampRepository.parseKitsuMalId(kitsuOnePiece))
        assertNull(IntroTimestampRepository.parseKitsuMalId("""{"data":[]}"""))
    }

    @Test
    fun kitsuEpisodesAreMappedToMyAnimeListAndTheMappingIsCached() = runBlocking {
        val requested = mutableListOf<String>()
        val repository = IntroTimestampRepository(context) { url ->
            requested += url
            when {
                url.startsWith("https://kitsu.io/api/edge/anime/12/mappings") -> kitsuOnePiece
                url.startsWith("https://api.aniskip.com/v2/skip-times/21/1?") -> aniSkipOnePieceEp1
                else -> null
            }
        }
        val ref = EpisodeRef("series", "kitsu:12", "kitsu:12:1")
        assertEquals(1, repository.candidates(ref).size)
        repository.candidates(ref)
        assertEquals(1, requested.count { it.contains("kitsu.io") })
        assertEquals(2, requested.count { it.contains("aniskip") })
    }

    @Test
    fun nonAnimeEpisodesMakeNoRequestsAndHaveNoData() = runBlocking {
        var requests = 0
        val repository = IntroTimestampRepository(context) { requests++; null }
        assertTrue(repository.candidates(EpisodeRef("series", "tt0903747", "tt0903747:1:1")).isEmpty())
        assertEquals(0, requests)
    }

    @Test
    fun networkFailuresMeanNoSkip() = runBlocking {
        val repository = IntroTimestampRepository(context) { throw java.io.IOException("offline") }
        assertTrue(repository.candidates(EpisodeRef("series", "mal:21", "mal:21:1")).isEmpty())
    }

    @Test
    fun manualMarksComeFirstAndCanBeRemoved() = runBlocking {
        val repository = IntroTimestampRepository(context) { url -> if (url.contains("aniskip")) aniSkipOnePieceEp1 else null }
        val ep1 = EpisodeRef("series", "mal:21", "mal:21:1")
        val ep2 = EpisodeRef("series", "mal:21", "mal:21:2")
        repository.saveManual(ep1, 30_000, 120_000, wholeSeries = false)
        repository.saveManual(ep1, 25_000, 115_000, wholeSeries = true)

        val candidates = repository.candidates(ep1)
        assertEquals(IntroSource.MANUAL_EPISODE, candidates.first().source)
        assertEquals(IntroSource.MANUAL_SERIES, candidates[1].source)
        // The series mark also covers the other episodes.
        assertEquals(IntroInterval(25_000, 115_000, IntroSource.MANUAL_SERIES), repository.manual(ep2).single())

        repository.clearManual(ep1)
        assertTrue(repository.manual(ep1).isEmpty())
    }

    @Test
    fun settingsDefaultToButtonOnlyAndPersist() {
        val repository = IntroTimestampRepository(context) { null }
        assertEquals(IntroSkipSettings(enabled = true, showButton = true, autoSkip = false), repository.settings.value)
        repository.updateSettings(IntroSkipSettings(enabled = true, showButton = false, autoSkip = true))
        val reopened = IntroTimestampRepository(context) { null }
        assertTrue(reopened.settings.value.autoSkip)
        assertFalse(reopened.settings.value.showButton)
    }
}
