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

/**
 * HTTP is a test double; the bodies are trimmed copies of real answers from TheIntroDB, SkipDB,
 * AniSkip and Kitsu (October 2026).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IntroTimestampRepositoryTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    // Game of Thrones S01E01 (61:42).
    private val got = EpisodeRef("series", "tt0944947", "tt0944947:1:1")
    private val gotDuration = 3_702_000L
    private val introDbGot = """{"imdb_id":"tt0944947","media_type":"tv","is_movie":false,"season":1,"episode":1,
        "intro":{"start_sec":437,"end_sec":531,"start_ms":437000,"end_ms":531000,"confidence":1,"submission_count":2,"updated_at":"2026-06-16T02:49:39.823Z"},
        "recap":null,"outro":null,"post_credits":null,"no_intro":false,"no_recap":true,"no_outro":false}"""
    private val introDbNoIntro = """{"imdb_id":"tt0903747","media_type":"tv","is_movie":false,"season":1,"episode":1,
        "intro":null,"recap":null,"outro":{"start_sec":3431,"end_sec":3500,"start_ms":3431000,"end_ms":3500000},"post_credits":null,
        "no_intro":true,"no_recap":true,"no_outro":false}"""
    private val introDbUntimed = """{"imdb_id":"tt0944947","media_type":"tv","is_movie":false,"season":1,"episode":1,
        "intro":null,"recap":null,"outro":null,"post_credits":null,"no_intro":false,"no_recap":false,"no_outro":false}"""
    private val skipDbGotExact = """{"imdb_id":"tt0944947","season":1,"episode":1,"segments":{
        "intro":{"start_ms":447800,"end_ms":521400,"adjusted":false,"offset_ms":0,"match":"exact","confidence":0.75},
        "recap":null,"outro":null,"preview":null},"intro_length_estimate_ms":null}"""
    private val skipDbOutOfRange = """{"imdb_id":"tt0944947","season":1,"episode":1,"segments":{
        "intro":{"start_ms":229500,"end_ms":246500,"adjusted":false,"offset_ms":-680000,"match":"out-of-range","confidence":0.6},
        "recap":null,"outro":null,"preview":null},"intro_length_estimate_ms":17000}"""

    private val aniSkipOnePieceEp1 = """
        {"found":true,"results":[
          {"interval":{"startTime":28.783,"endTime":118.783},"skipType":"op","skipId":"a","episodeLength":1500.04},
          {"interval":{"startTime":1387.996,"endTime":1500},"skipType":"ed","skipId":"b","episodeLength":1500.146}
        ],"message":"Successfully found skip times","statusCode":200}
    """.trimIndent()
    private val kitsuOnePiece = """{"data":[{"id":"1175","type":"mappings","attributes":{"externalSite":"myanimelist/anime","externalId":"21"}}]}"""

    private fun ok(body: String) = IntroHttpResponse(200, body)
    private val notFound = IntroHttpResponse(404, null)

    /** Routes by host; unknown URLs answer 404. Records every request. */
    private class FakeHttp(private val routes: Map<String, () -> IntroHttpResponse>) : (String) -> IntroHttpResponse {
        val requested = mutableListOf<String>()
        override fun invoke(url: String): IntroHttpResponse {
            requested += url
            return routes.entries.firstOrNull { url.contains(it.key) }?.value?.invoke() ?: IntroHttpResponse(404, null)
        }
        fun count(host: String) = requested.count { it.contains(host) }
    }

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("mwplay_intro_skip", 0).edit().clear().commit()
    }

    @Test
    fun theIntroDbAnswersFirstAndSkipDbIsNotAsked() = runBlocking {
        val http = FakeHttp(mapOf("introdb" to { ok(introDbGot) }, "skipdb" to { ok(skipDbGotExact) }))
        val interval = IntroTimestampRepository(context, http).find(got, gotDuration)
        assertEquals(IntroInterval(437_000, 531_000, IntroSource.INTRODB), interval)
        assertEquals(listOf("https://api.introdb.app/segments?imdb_id=tt0944947&season=1&episode=1"), http.requested)
    }

    @Test
    fun skipDbIsTheFallbackAndReceivesTheDuration() = runBlocking {
        val http = FakeHttp(mapOf("introdb" to { ok(introDbUntimed) }, "skipdb" to { ok(skipDbGotExact) }))
        val interval = IntroTimestampRepository(context, http).find(got, gotDuration)
        assertEquals(IntroInterval(447_800, 521_400, IntroSource.SKIPDB, durationMatched = true), interval)
        assertTrue(http.requested.last().endsWith("imdb_id=tt0944947&season=1&episode=1&type=intro&duration=3702"))
    }

    @Test
    fun aniSkipAnswersForAnimeAndTheKitsuMappingIsCached() = runBlocking {
        val http = FakeHttp(mapOf(
            "kitsu.io/api/edge/anime/12/mappings" to { ok(kitsuOnePiece) },
            "api.aniskip.com/v2/skip-times/21/1?" to { ok(aniSkipOnePieceEp1) },
        ))
        val repository = IntroTimestampRepository(context, http)
        val ref = EpisodeRef("series", "kitsu:12", "kitsu:12:1")
        assertEquals(IntroInterval(28_783, 118_783, IntroSource.ANISKIP, episodeLengthMs = 1_500_040), repository.find(ref, 1_500_000))
        // The answer is cached: no new request for the same episode and release.
        repository.find(ref, 1_500_000)
        IntroTimestampRepository(context, http).find(ref, 1_500_000)
        assertEquals(1, http.count("kitsu.io"))
        assertEquals(1, http.count("aniskip"))
        assertEquals(0, http.count("introdb") + http.count("skipdb"))
    }

    @Test
    fun noSourceHasDataMeansNoButton() = runBlocking {
        val http = FakeHttp(mapOf("introdb" to { notFound }, "skipdb" to { ok(skipDbOutOfRange) }))
        assertNull(IntroTimestampRepository(context, http).find(got, gotDuration))
        assertEquals(2, http.requested.size)
    }

    @Test
    fun aConfirmedMissingOpeningStopsTheLookup() = runBlocking {
        val http = FakeHttp(mapOf("introdb" to { ok(introDbNoIntro) }, "skipdb" to { ok(skipDbGotExact) }))
        assertNull(IntroTimestampRepository(context, http).find(EpisodeRef("series", "tt0903747", "tt0903747:1:1"), 3_500_000))
        assertEquals(0, http.count("skipdb"))
    }

    @Test
    fun anUnavailableSourceIsSkippedAndTheMissIsNotRemembered() = runBlocking {
        var introDbDown = true
        val http = FakeHttp(mapOf(
            "introdb" to { if (introDbDown) throw java.io.IOException("offline") else ok(introDbGot) },
            "skipdb" to { IntroHttpResponse(429, null) },
        ))
        assertNull(IntroTimestampRepository(context, http).find(got, gotDuration))
        assertEquals(2, http.requested.size)
        // Back online: a fresh player session asks again and finds it.
        introDbDown = false
        assertEquals(437_000L, IntroTimestampRepository(context, http).find(got, gotDuration)?.startMs)
    }

    @Test
    fun invalidIdentifiersMakeNoRequests() = runBlocking {
        val http = FakeHttp(emptyMap())
        val repository = IntroTimestampRepository(context, http)
        assertNull(repository.find(EpisodeRef("series", "tt0944947", "tt0944947:x:1"), gotDuration))
        assertNull(repository.find(EpisodeRef("series", "foo", "foo:1:1"), gotDuration))
        assertNull(repository.find(EpisodeRef("tv", "bestcine:canal", "bestcine:canal:1"), gotDuration))
        assertNull("duration unknown", repository.find(got, 0))
        assertTrue(http.requested.isEmpty())
    }

    @Test
    fun dataIncompatibleWithTheDurationIsRejected() = runBlocking {
        // A 20-minute cut cannot have an opening that ends at 8:51.
        val http = FakeHttp(mapOf("introdb" to { ok(introDbGot) }, "skipdb" to { notFound }))
        assertNull(IntroTimestampRepository(context, http).find(got, 500_000))
        assertEquals(1, http.count("skipdb"))
    }

    @Test
    fun answersForAnotherEpisodeAreIgnored() {
        assertEquals(IntroTimestampRepository.SourceAnswer.NoData, IntroTimestampRepository.parseIntroDb(introDbGot, "tt0944947", 1, 2))
        assertEquals(IntroTimestampRepository.SourceAnswer.NoData, IntroTimestampRepository.parseSkipDb(skipDbGotExact, "tt0944947", 2, 1))
        assertEquals(IntroTimestampRepository.SourceAnswer.NoData, IntroTimestampRepository.parseSkipDb(skipDbOutOfRange, "tt0944947", 1, 1))
        assertEquals(IntroTimestampRepository.SourceAnswer.NoData, IntroTimestampRepository.parseIntroDb("not json", "tt0944947", 1, 1))
    }

    @Test
    fun parsesOnlyOpeningsFromAniSkip() {
        val intervals = IntroTimestampRepository.parseAniSkip(aniSkipOnePieceEp1)
        assertEquals(listOf(IntroInterval(28_783, 118_783, IntroSource.ANISKIP, episodeLengthMs = 1_500_040)), intervals)
        assertTrue(IntroTimestampRepository.parseAniSkip("""{"found":false,"results":[]}""").isEmpty())
        assertEquals("21", IntroTimestampRepository.parseKitsuMalId(kitsuOnePiece))
        assertNull(IntroTimestampRepository.parseKitsuMalId("""{"data":[]}"""))
    }

    @Test
    fun settingsDefaultToManualAndPersist() {
        context.getSharedPreferences("mwplay_intro_skip", 0).edit()
            .putString("manual_episode:series|tt1|tt1:1:1", "1,2").putBoolean("show_button", false).commit()
        val repository = IntroTimestampRepository(context, FakeHttp(emptyMap()))
        assertEquals(IntroSkipSettings(enabled = true, autoSkip = false), repository.settings.value)
        // Marks from the removed manual feature are dropped.
        assertFalse(context.getSharedPreferences("mwplay_intro_skip", 0).contains("manual_episode:series|tt1|tt1:1:1"))
        repository.updateSettings(IntroSkipSettings(enabled = true, autoSkip = true))
        assertTrue(IntroTimestampRepository(context, FakeHttp(emptyMap())).settings.value.autoSkip)
    }
}
