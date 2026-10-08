package com.stremio.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastMediaPolicyTest {
    @Test
    fun `signed URLs retain the original case escaping parameters and fragment`() {
        val signedUrl = "HTTPS://Media.Example.com:8443/a%2Fb/VIDEO.MP4?token=a%2Bb&part=1&part=2#t=30"

        val decision = CastMediaPolicy.evaluate(" \t$signedUrl\n")

        assertTrue(decision.supported)
        assertEquals(signedUrl, decision.url)
        assertEquals("video/mp4", decision.contentType)
        assertNull(decision.rejection)
    }

    @Test
    fun `content type follows the video path rather than query values`() {
        val cases = mapOf(
            "video.mp4" to "video/mp4",
            "playlist.M3U8" to "application/x-mpegURL",
            "stream.mpd" to "application/dash+xml",
            "video.webm" to "video/webm",
            "video.mkv" to "video/x-matroska",
            "watch" to "video/mp4",
        )

        cases.forEach { (path, mime) ->
            val decision = CastMediaPolicy.evaluate("https://media.example.com/$path?download=wrong.mkv")
            assertTrue(path, decision.supported)
            assertEquals(path, mime, decision.contentType)
        }
    }

    @Test
    fun `private and LAN addresses remain available to the receiver`() {
        val urls = listOf(
            "http://192.168.1.20:8090/video.mp4",
            "http://10.0.0.2/video.mp4",
            "http://172.16.0.10/video.mp4",
            "http://nas.local/video.mp4",
            "http://[fd00::1234]:8090/video.mp4",
            "http://[2001:db8::1]/video.mp4",
            "http://[::ffff:192.168.1.20]/video.mp4",
            "http://media.example.com:1/video.mp4",
            "http://media.example.com:65535/video.mp4",
        )

        urls.forEach { url -> assertTrue(url, CastMediaPolicy.isRemoteHttpUrl(url)) }
    }

    @Test
    fun `host aliases for the phone cannot be transmitted`() {
        val hosts = listOf(
            "localhost", "LOCALHOST.", "localhost.localdomain", "localhost.example.com",
            "video.localhost", "localdomain", "video.localdomain", "ip6-localhost", "ip6-loopback",
        )

        hosts.forEach { host -> assertRejected("http://$host/video.mp4") }
    }

    @Test
    fun `all common legacy IPv4 loopback forms are rejected`() {
        val hosts = listOf(
            "127.0.0.1", "127.255.255.255", "127.000.0.001", "00127.0.0.1",
            "127.1", "127.0.1", "2130706433", "2147483647",
            "0x7f000001", "0X7F000001", "0x7f.0.0.1", "0177.0.0.1",
            "0177.1", "0177.0.1", "017700000001",
        )

        hosts.forEach { host -> assertRejected("http://$host:11470/video.mp4") }
    }

    @Test
    fun `wildcard bind and IPv6 loopback addresses are rejected`() {
        val hosts = listOf(
            "0", "0.0.0.0", "0000000000", "0x00000000", "0.0", "0.0.0",
            "[::]", "[0:0:0:0:0:0:0:0]", "[::1]", "[0:0:0:0:0:0:0:1]",
            "[::ffff:127.0.0.1]", "[::FFFF:7f00:1]", "[0:0:0:0:0:ffff:7f00:1]",
            "[::ffff:0.0.0.0]",
        )

        hosts.forEach { host -> assertRejected("http://$host/video.mp4") }
    }

    @Test
    fun `non-network blank and malformed URLs are rejected`() {
        val urls = listOf(
            null, "", " \n ", "file:///sdcard/video.mp4", "content://media/video/1",
            "magnet:?xt=urn:btih:example", "ftp://example.com/video.mp4", "//example.com/video.mp4",
            "/video.mp4", "http:video.mp4", "https:///video.mp4", "https://", "https://?video=1",
            "https://exa mple.com/video.mp4", "https://example.com/bad%link", "https://.example.com/video.mp4",
            "https://[invalid]/video.mp4", "https://example.com\\video.mp4",
        )

        urls.forEach(::assertRejected)
    }

    @Test
    fun `credentials and invalid ports are rejected`() {
        val urls = listOf(
            "https://user:password@example.com/video.mp4", "https://user@example.com/video.mp4",
            "https://@example.com/video.mp4", "https://example.com:/video.mp4",
            "https://example.com:0/video.mp4", "https://example.com:-1/video.mp4",
            "https://example.com:65536/video.mp4", "https://example.com:abc/video.mp4",
            "https://example.com:2147483648/video.mp4", "https://[fd00::1]:/video.mp4",
            "https://[fd00::1]:65536/video.mp4",
        )

        urls.forEach(::assertRejected)
    }

    @Test
    fun `protected streams explain the default receiver header limitation`() {
        val decision = CastMediaPolicy.evaluate("https://example.com/video.mp4", requiresHeaders = true)

        assertFalse(decision.supported)
        assertNull(decision.url)
        assertNull(decision.contentType)
        assertTrue(decision.rejection.orEmpty().contains("cabeçalhos"))
        assertTrue(decision.rejection.orEmpty().contains("cookies"))
        assertTrue(CastMediaPolicy.isRemoteHttpUrl("https://example.com/video.mp4"))
    }

    private fun assertRejected(url: String?) {
        val decision = CastMediaPolicy.evaluate(url)
        assertFalse(url, decision.supported)
        assertFalse(url, CastMediaPolicy.isRemoteHttpUrl(url))
        assertNull(url, decision.url)
        assertNull(url, decision.contentType)
        assertFalse(url, decision.rejection.isNullOrBlank())
    }
}
