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
        "recap":null,"outro":{"start_sec":3631.5,"end_sec":3699.5,"start_ms":3631500,"end_ms":3699500,"confidence":1,"submission_count":2},
        "post_credits":null,"no_intro":false,"no_recap":true,"no_outro":false}"""
    private val introDbIntroOnly = """{"imdb_id":"tt0944947","media_type":"tv","is_movie":false,"season":1,"episode":1,
        "intro":{"start_ms":437000,"end_ms":531000},"recap":null,"outro":null,"post_credits":null,"no_intro":false,"no_recap":false,"no_outro":false}"""
    private val introDbNoIntro = """{"imdb_id":"tt0903747","media_type":"tv","is_movie":false,"season":1,"episode":1,
        "intro":null,"recap":null,"outro":{"start_sec":3431,"end_sec":3500,"start_ms":3431000,"end_ms":3500000},"post_credits":null,
        "no_intro":true,"no_recap":true,"no_outro":false}"""
    private val introDbUntimed = """{"imdb_id":"tt0944947","media_type":"tv","is_movie":false,"season":1,"episode":1,
        "intro":null,"recap":null,"outro":null,"post_credits":null,"no_intro":false,"no_recap":false,"no_outro":false}"""
    private val skipDbGot = """{"imdb_id":"tt0944947","season":1,"episode":1,"segments":{
        "intro":{"start_ms":447800,"end_ms":521400,"adjusted":false,"offset_ms":0,"match":"exact","confidence":0.75},
        "recap":{"start_ms":0,"end_ms":59400,"adjusted":false,"offset_ms":0,"match":"exact","confidence":0.75},
        "outro":{"start_ms":3630900,"end_ms":3702100,"adjusted":false,"offset_ms":0,"match":"exact","confidence":0.77},
        "preview":null},"intro_length_estimate_ms":null}"""
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
    fun theIntroDbAnswersOpeningAndCreditsSoSkipDbIsNotAsked() = runBlocking {
        val http = FakeHttp(mapOf("introdb" to { ok(introDbGot) }, "skipdb" to { ok(skipDbGot) }))
        val segments = IntroTimestampRepository(context, http).find(got, gotDuration)
        assertEquals(IntroInterval(437_000, 531_000, IntroSource.INTRODB), segments.intro)
        assertEquals(IntroInterval(3_631_500, 3_699_500, IntroSource.INTRODB), segments.credits)
        assertNull(segments.recap)
        assertEquals(listOf("https://api.introdb.app/segments?imdb_id=tt0944947&season=1&episode=1"), http.requested)
    }

    @Test
    fun skipDbFillsWhatIsMissingAndReceivesTheDuration() = runBlocking {
        val http = FakeHttp(mapOf("introdb" to { ok(introDbIntroOnly) }, "skipdb" to { ok(skipDbGot) }))
        val segments = IntroTimestampRepository(context, http).find(got, gotDuration)
        // The opening stays TheIntroDB's; SkipDB brings the recap and the credits.
        assertEquals(IntroSource.INTRODB, segments.intro?.source)
        assertEquals(IntroInterval(0, 59_400, IntroSource.SKIPDB, durationMatched = true), segments.recap)
        assertEquals(IntroInterval(3_630_900, 3_702_100, IntroSource.SKIPDB, durationMatched = true), segments.credits)
        assertTrue(http.requested.last().endsWith("imdb_id=tt0944947&season=1&episode=1&duration=3702"))
    }

    @Test
    fun skipDbIsTheFallbackForTheOpening() = runBlocking {
        val http = FakeHttp(mapOf("introdb" to { ok(introDbUntimed) }, "skipdb" to { ok(skipDbGot) }))
        assertEquals(IntroInterval(447_800, 521_400, IntroSource.SKIPDB, durationMatched = true), IntroTimestampRepository(context, http).find(got, gotDuration).intro)
    }

    @Test
    fun aniSkipAnswersForAnimeWithEndingAsCreditsAndTheMappingIsCached() = runBlocking {
        val http = FakeHttp(mapOf(
            "kitsu.io/api/edge/anime/12/mappings" to { ok(kitsuOnePiece) },
            "api.aniskip.com/v2/skip-times/21/1?" to { ok(aniSkipOnePieceEp1) },
        ))
        val repository = IntroTimestampRepository(context, http)
        val ref = EpisodeRef("series", "kitsu:12", "kitsu:12:1")
        val segments = repository.find(ref, 1_500_000)
        assertEquals(IntroInterval(28_783, 118_783, IntroSource.ANISKIP, episodeLengthMs = 1_500_040), segments.intro)
        assertEquals(IntroInterval(1_387_996, 1_500_000, IntroSource.ANISKIP, episodeLengthMs = 1_500_146), segments.credits)
        assertTrue(http.requested.last().contains("types%5B%5D=op&types%5B%5D=ed&types%5B%5D=recap"))
        // Cached: no new request for the same episode and release.
        repository.find(ref, 1_500_000)
        assertEquals(segments, IntroTimestampRepository(context, http).find(ref, 1_500_000))
        assertEquals(1, http.count("kitsu.io"))
        assertEquals(1, http.count("aniskip"))
        assertEquals(0, http.count("introdb") + http.count("skipdb"))
    }

    @Test
    fun noSourceHasDataMeansNoButton() = runBlocking {
        val http = FakeHttp(mapOf("introdb" to { notFound }, "skipdb" to { ok(skipDbOutOfRange) }))
        assertEquals(EpisodeSegments(), IntroTimestampRepository(context, http).find(got, gotDuration))
        assertEquals(2, http.requested.size)
    }

    @Test
    fun aConfirmedMissingOpeningWithCreditsStopsTheLookup() = runBlocking {
        val http = FakeHttp(mapOf("introdb" to { ok(introDbNoIntro) }, "skipdb" to { ok(skipDbGot) }))
        val segments = IntroTimestampRepository(context, http).find(EpisodeRef("series", "tt0903747", "tt0903747:1:1"), 3_500_000)
        assertNull(segments.intro)
        assertEquals(3_431_000L, segments.credits?.startMs)
        assertEquals(0, http.count("skipdb"))
    }

    @Test
    fun anUnavailableSourceIsSkippedAndTheMissIsNotRemembered() = runBlocking {
        var introDbDown = true
        val http = FakeHttp(mapOf(
            "introdb" to { if (introDbDown) throw java.io.IOException("offline") else ok(introDbGot) },
            "skipdb" to { IntroHttpResponse(429, null) },
        ))
        assertEquals(EpisodeSegments(), IntroTimestampRepository(context, http).find(got, gotDuration))
        assertEquals(2, http.requested.size)
        introDbDown = false
        assertEquals(437_000L, IntroTimestampRepository(context, http).find(got, gotDuration).intro?.startMs)
    }

    @Test
    fun invalidIdentifiersMakeNoRequests() = runBlocking {
        val http = FakeHttp(emptyMap())
        val repository = IntroTimestampRepository(context, http)
        assertEquals(EpisodeSegments(), repository.find(EpisodeRef("series", "tt0944947", "tt0944947:x:1"), gotDuration))
        assertEquals(EpisodeSegments(), repository.find(EpisodeRef("series", "foo", "foo:1:1"), gotDuration))
        assertEquals(EpisodeSegments(), repository.find(EpisodeRef("tv", "bestcine:canal", "bestcine:canal:1"), gotDuration))
        assertEquals("duration unknown", EpisodeSegments(), repository.find(got, 0))
        assertTrue(http.requested.isEmpty())
    }

    @Test
    fun dataIncompatibleWithTheDurationIsRejected() = runBlocking {
        // A 20-minute cut cannot have an opening ending at 8:51 or credits at 1:00:31.
        val http = FakeHttp(mapOf("introdb" to { ok(introDbGot) }, "skipdb" to { notFound }))
        assertEquals(EpisodeSegments(), IntroTimestampRepository(context, http).find(got, 500_000))
        assertEquals(1, http.count("skipdb"))
    }

    @Test
    fun answersForAnotherEpisodeAreIgnored() {
        val none = IntroTimestampRepository.SourceAnswer.NONE
        assertEquals(none, IntroTimestampRepository.parseIntroDb(introDbGot, "tt0944947", 1, 2))
        assertEquals(none, IntroTimestampRepository.parseSkipDb(skipDbGot, "tt0944947", 2, 1))
        assertEquals(EpisodeSegments(), IntroTimestampRepository.parseSkipDb(skipDbOutOfRange, "tt0944947", 1, 1).segments)
        assertEquals(none, IntroTimestampRepository.parseIntroDb("not json", "tt0944947", 1, 1))
    }

    @Test
    fun parsesAniSkipKinds() {
        val kinds = IntroTimestampRepository.parseAniSkipSegments(aniSkipOnePieceEp1)
        assertEquals(1, kinds[SegmentKind.INTRO]?.size)
        assertEquals(1, kinds[SegmentKind.CREDITS]?.size)
        assertNull(kinds[SegmentKind.RECAP])
        assertTrue(IntroTimestampRepository.parseAniSkip("""{"found":false,"results":[]}""").isEmpty())
        assertEquals("21", IntroTimestampRepository.parseKitsuMalId(kitsuOnePiece))
    }

    @Test
    fun settingsDefaultToManualAndOldEntriesAreDropped() {
        context.getSharedPreferences("mwplay_intro_skip", 0).edit()
            .putString("manual_episode:series|tt1|tt1:1:1", "1,2").putString("lookup:x", "1,none").putBoolean("show_button", false).commit()
        val repository = IntroTimestampRepository(context, FakeHttp(emptyMap()))
        assertEquals(IntroSkipSettings(enabled = true, autoSkip = false), repository.settings.value)
        val prefs = context.getSharedPreferences("mwplay_intro_skip", 0)
        assertFalse(prefs.contains("manual_episode:series|tt1|tt1:1:1"))
        assertFalse(prefs.contains("lookup:x"))
        repository.updateSettings(IntroSkipSettings(enabled = true, autoSkip = true))
        assertTrue(IntroTimestampRepository(context, FakeHttp(emptyMap())).settings.value.autoSkip)
    }
}
