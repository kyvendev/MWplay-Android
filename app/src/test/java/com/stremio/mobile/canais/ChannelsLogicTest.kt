package com.stremio.mobile.canais

import com.stremio.mobile.canais.ChannelsFixtures.channels
import com.stremio.mobile.canais.ChannelsFixtures.cnn
import com.stremio.mobile.canais.ChannelsFixtures.espn
import com.stremio.mobile.canais.ChannelsFixtures.globo
import com.stremio.mobile.canais.ChannelsFixtures.sportv
import com.stremio.mobile.data.model.CatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelsLogicTest {
    private fun visible(
        showFavorites: Boolean = false,
        sports: Boolean = false,
        listed: Boolean = false,
        query: String = "",
        favorites: List<CatalogItem> = emptyList(),
    ) = ChannelList.visible(channels + globo, favorites, showFavorites, sports, listed, query)

    @Test
    fun allChannelsOnceEach() = assertEquals(channels, visible())

    @Test
    fun sportsShowOnlySportsChannels() = assertEquals(listOf(sportv, espn), visible(sports = true))

    @Test
    fun favoritesShowTheSavedChannels() = assertEquals(listOf(cnn), visible(showFavorites = true, favorites = listOf(cnn)))

    @Test
    fun searchIgnoresAccentsCaseAndWordOrder() {
        assertEquals(listOf(cnn), visible(query = "brasil cnn"))
        assertEquals(listOf(globo), visible(query = "GLÓBO"))
        assertEquals(emptyList<CatalogItem>(), visible(query = "hbo"))
    }

    @Test
    fun zappingWrapsAroundTheList() {
        assertEquals(sportv, ChannelZapping.neighbour(channels, globo, 1))
        assertEquals(cnn, ChannelZapping.neighbour(channels, globo, -1))
        assertEquals(globo, ChannelZapping.neighbour(channels, cnn, 1))
        assertNull("single channel", ChannelZapping.neighbour(listOf(globo), globo, 1))
        assertNull("not in the list", ChannelZapping.neighbour(listOf(sportv, espn), globo, 1))
    }
}
