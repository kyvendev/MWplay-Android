package com.stremio.mobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentLabelsTest {
    @Test
    fun translatesContentTypes() {
        assertEquals("Filmes", ContentLabels.type("movie"))
        assertEquals("Séries", ContentLabels.type("series"))
        assertEquals("TV", ContentLabels.type("Tv"))
        assertEquals("Todos os tipos", ContentLabels.type(null))
        assertEquals("Podcasts", ContentLabels.type("podcasts"))
    }

    @Test
    fun translatesShelfTitlesAndCatalogs() {
        assertEquals("Séries populares", ContentLabels.shelfTitle("Popular", "series"))
        assertEquals("Filmes em destaque", ContentLabels.shelfTitle("Featured", "movie"))
        assertEquals("Populares", ContentLabels.shelfTitle("Popular", "anime"))
        assertEquals("Lançamentos", ContentLabels.catalog("New"))
        // Names the app doesn't know stay as the addon sends them.
        assertEquals("TNT HD (BC)", ContentLabels.catalog("TNT HD (BC)"))
    }

    @Test
    fun translatesGenresAndFilterOptions() {
        assertEquals("Ação", ContentLabels.genre("Action"))
        assertEquals("Ficção científica", ContentLabels.genre("Sci-Fi"))
        assertEquals("Terror", ContentLabels.genre("horror"))
        assertEquals("Todos os gêneros", ContentLabels.extraOption("genre", null))
        assertEquals("Comédia", ContentLabels.extraOption("genre", "Comedy"))
        assertEquals("2024", ContentLabels.extraOption("year", "2024"))
    }
}

class TvCatalogPolicyTest {
    private data class Catalog(val name: String, val url: String)

    private fun pick(catalogs: List<Catalog>, addonNames: Map<String, String> = emptyMap()) =
        TvCatalogPolicy.preferredCatalog(catalogs, { it.name }, { it.url }, { addonNames[it] })

    @Test
    fun recognisesTheTvType() {
        assertTrue(TvCatalogPolicy.isTv("tv"))
        assertTrue(TvCatalogPolicy.isTv("TV"))
        assertFalse(TvCatalogPolicy.isTv("movie"))
    }

    @Test
    fun prefersBestCineByCatalogNameUrlOrAddonName() {
        val other = Catalog("Canais", "https://other.example/manifest.json")
        val best = Catalog("Canais abertos", "https://addon.example/manifest.json")
        assertEquals(best, pick(listOf(other, best), mapOf(best.url to "Best Cine")))
        assertEquals(best, pick(listOf(other, best.copy(name = "BestCine TV")), emptyMap())?.copy(name = best.name))
        assertEquals(best, pick(listOf(other, best.copy(url = "https://best-cine.example/manifest.json")))?.copy(url = best.url))
    }

    @Test
    fun fallsBackToTheFirstTvCatalog() {
        val first = Catalog("Canais", "https://a.example/manifest.json")
        assertEquals(first, pick(listOf(first, Catalog("Esportes", "https://b.example/manifest.json"))))
        assertNull(pick(emptyList()))
    }
}
