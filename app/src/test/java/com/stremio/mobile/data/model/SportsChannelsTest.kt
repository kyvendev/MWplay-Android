package com.stremio.mobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsChannelsTest {
    private data class Channel(val id: String, val name: String, val genres: List<String> = emptyList())

    private val sportv = Channel("bc:sportv", "SporTV", listOf("Esportes"))
    private val espn = Channel("bc:espn", "ESPN", listOf("Futebol", "Esportes"))
    private val globo = Channel("bc:globo", "Globo", listOf("Abertos"))
    // A sports word in the name is not enough.
    private val novela = Channel("bc:esporte-novela", "Esporte Novelas", listOf("Novelas"))
    private val noMetadata = Channel("bc:band", "Band")

    private fun sports(items: List<Channel>, listed: Boolean, forced: Set<String> = emptySet(), excluded: Set<String> = emptySet()) =
        SportsChannelPolicy.sportsChannels(items, { it.id }, { it.genres }, listed, forced, excluded)

    @Test
    fun sportsChannelsAppearAndOthersDoNot() {
        assertEquals(listOf(sportv, espn), sports(listOf(sportv, globo, espn, novela, noMetadata), listed = false))
    }

    @Test
    fun categoriesAreComparedWholeIgnoringAccentsAndCase() {
        assertTrue(SportsChannelPolicy.isSportsCategory("TÊNIS"))
        assertTrue(SportsChannelPolicy.isSportsCategory(" esportes  em geral "))
        assertTrue(SportsChannelPolicy.isSportsCategory("Automobilismo"))
        assertFalse(SportsChannelPolicy.isSportsCategory("Canal Esportivo"))
        assertFalse(SportsChannelPolicy.isSportsCategory("Novelas"))
        assertFalse(SportsChannelPolicy.isSportsCategory(null))
    }

    @Test
    fun channelsAreNotDuplicated() {
        assertEquals(listOf(sportv, espn), sports(listOf(sportv, espn, sportv), listed = false))
    }

    @Test
    fun wrongClassificationsAreFixedByTheExplicitLists() {
        // Listed by the source under sports, but explicitly not a sports channel.
        assertEquals(listOf(sportv), sports(listOf(sportv, globo), listed = true, excluded = setOf("bc:globo")))
        // No sports metadata, explicitly a sports channel.
        assertEquals(listOf(noMetadata), sports(listOf(globo, noMetadata), listed = false, forced = setOf("bc:band")))
    }

    private fun row(options: List<TvCategories.Option<String>>, byMetadata: Boolean = false) =
        TvCategories.build(options, byMetadata, label = { it }, allRequest = "catalog")

    @Test
    fun categoryRowKeepsTheSourceCategoriesAndAddsEsportes() {
        val options = listOf(
            TvCategories.Option(null, true, "all"),
            TvCategories.Option("Abertos", false, "abertos"),
            TvCategories.Option("Esportes", false, "esportes"),
            TvCategories.Option("Futebol", false, "futebol"),
            TvCategories.Option("Notícias", false, "noticias"),
        )
        val row = row(options)
        assertEquals(listOf("Todos os canais", "Esportes", "Abertos", "Notícias"), row.chips.map { it.label })
        assertEquals("esportes", row.chips[1].request)
        assertTrue(row.chips[0].selected)
        assertFalse(row.sportsOpen)

        val inSports = row(options.map { it.copy(selected = it.value == "Futebol") })
        assertTrue(inSports.sportsOpen && inSports.listedUnderSports)
        assertTrue(inSports.chips.single { it.kind == TvCategories.Kind.SPORTS }.selected)
        assertEquals(listOf("Esportes", "Futebol"), inSports.sportsSubcategories.map { it.label })
        assertEquals("futebol", inSports.sportsSubcategories.single { it.selected }.request)
    }

    @Test
    fun withoutASportsCategoryEsportesFiltersByMetadata() {
        val options = listOf(TvCategories.Option(null, true, "all"), TvCategories.Option("Abertos", false, "abertos"))
        val closed = row(options)
        assertTrue(closed.sportsFromMetadata)
        assertEquals("all", closed.chips.single { it.kind == TvCategories.Kind.SPORTS }.request)
        val open = row(options, byMetadata = true)
        assertTrue(open.sportsOpen)
        assertFalse(open.listedUnderSports)
        assertFalse("Todos is not selected inside Esportes", open.chips.first().selected)
    }

    @Test
    fun aCatalogWithoutGenreFilterStillOffersTodosAndEsportes() {
        val row = row(emptyList())
        assertEquals(listOf("Todos os canais", "Esportes"), row.chips.map { it.label })
        assertEquals(listOf("catalog", "catalog"), row.chips.map { it.request })
    }
}
