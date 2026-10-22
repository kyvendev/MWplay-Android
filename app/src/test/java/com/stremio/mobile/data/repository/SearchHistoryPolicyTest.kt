package com.stremio.mobile.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryPolicyTest {
    @Test
    fun addsMostRecentFirstWithoutDuplicates() {
        var history = emptyList<String>()
        history = SearchHistoryPolicy.add(history, "Matrix")
        history = SearchHistoryPolicy.add(history, "  Breaking   Bad ")
        history = SearchHistoryPolicy.add(history, "matrix")
        assertEquals(listOf("matrix", "Breaking Bad"), history)
    }

    @Test
    fun ignoresBlankAndSingleLetterSearches() {
        assertEquals(emptyList<String>(), SearchHistoryPolicy.add(emptyList(), "   "))
        assertEquals(emptyList<String>(), SearchHistoryPolicy.add(emptyList(), "a"))
    }

    @Test
    fun keepsOnlyTheLatestEntries() {
        var history = emptyList<String>()
        repeat(SearchHistoryPolicy.MAX_ENTRIES + 5) { history = SearchHistoryPolicy.add(history, "busca $it") }
        assertEquals(SearchHistoryPolicy.MAX_ENTRIES, history.size)
        assertEquals("busca ${SearchHistoryPolicy.MAX_ENTRIES + 4}", history.first())
    }

    @Test
    fun removesOneEntry() {
        assertEquals(listOf("Dune"), SearchHistoryPolicy.remove(listOf("Matrix", "Dune"), "Matrix"))
    }

    @Test
    fun survivesStorageRoundTrip() {
        val history = listOf("Matrix", "Breaking Bad", "Duna: Parte 2")
        assertEquals(history, SearchHistoryPolicy.decode(SearchHistoryPolicy.encode(history)))
        assertEquals(emptyList<String>(), SearchHistoryPolicy.decode(null))
    }
}
