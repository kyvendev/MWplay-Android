package com.stremio.mobile.data.model

import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.core.types.resource.StreamBehaviorHints
import com.stremio.core.types.resource.StreamDeepLinks
import com.stremio.mobile.core.CoreStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalStreamSelectionTest {
    private val last = LocalStreamSelection(key = "3-Best Cine-HD", addonTitle = "Best Cine", name = "Best Cine HD", description = "S01E02", quality = "1080p")

    private fun option(index: Int, addon: String, name: String, quality: String?) = StreamOption(
        key = "$index-$addon-$name",
        name = name,
        description = "S01E03",
        addonTitle = addon,
        quality = quality,
        core = CoreStream(
            stream = Stream(
                source = Stream.Source.Url(Stream.Url(url = "https://example.invalid/$index")),
                behaviorHints = StreamBehaviorHints(notWebReady = false),
                deepLinks = StreamDeepLinks(player = "", externalPlayer = StreamDeepLinks.ExternalPlayerLink()),
            ),
            streamRequest = ResourceRequest(base = "https://addon.example/manifest.json", path = ResourcePath(resource = "stream", type = "series", id = "tt1:1:3", extra = emptyList())),
            metaRequest = null,
            addonTitle = addon,
        ),
    )

    @Test
    fun newEpisodeResumesOnTheSameServerPreferringNameAndQuality() {
        val other = option(0, "Outro", "Outro 1080p", "1080p")
        val sameServer720 = option(1, "Best Cine", "Best Cine SD", "720p")
        val sameServer1080 = option(2, "Best Cine", "Best Cine FHD", "1080p")
        val exactName = option(3, "Best Cine", "Best Cine HD", "1080p")

        assertEquals(exactName, last.pickSameServer(listOf(other, sameServer720, sameServer1080, exactName)))
        assertEquals(sameServer1080, last.pickSameServer(listOf(other, sameServer720, sameServer1080)))
        assertEquals(sameServer720, last.pickSameServer(listOf(other, sameServer720)))
    }

    @Test
    fun noStreamFromThatServerMeansThePickerOpens() {
        assertNull(last.pickSameServer(listOf(option(0, "Outro", "Outro", "1080p"))))
    }
}
