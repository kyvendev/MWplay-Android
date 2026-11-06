package com.stremio.mobile.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Play Next" row follows "Continuar assistindo" and respects what the user removed. */
class WatchNextPublisherTest {
    private fun entry(id: String, position: Long = 600_000, duration: Long = 2_400_000, order: Int = 0) =
        WatchNextEntry(id, "series", "Série $id", null, position, duration, order)

    @Test
    fun newTitlesAreAddedAndKnownOnesUpdated() {
        val existing = mapOf(WatchNextPublisher.keyOf("series", "tt1") to WatchNextPublisher.Existing(7, browsable = true))
        val plan = WatchNextPublisher.plan(listOf(entry("tt1"), entry("tt2", order = 1)), existing)
        assertEquals(listOf("tt2"), plan.insert.map { it.id })
        assertEquals(mapOf(7L to entry("tt1")), plan.update)
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun titlesThatLeftTheListAreRemoved() {
        val existing = mapOf(WatchNextPublisher.keyOf("series", "tt9") to WatchNextPublisher.Existing(9, browsable = true))
        assertEquals(listOf(9L), WatchNextPublisher.plan(emptyList(), existing).delete)
    }

    @Test
    fun aTitleTheUserRemovedFromTheRowIsNotAddedBack() {
        val existing = mapOf(WatchNextPublisher.keyOf("series", "tt1") to WatchNextPublisher.Existing(7, browsable = false))
        val plan = WatchNextPublisher.plan(listOf(entry("tt1")), existing)
        assertTrue(plan.insert.isEmpty())
        assertTrue(plan.update.isEmpty())
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun onlyTitlesWithProgressAreShown() {
        val plan = WatchNextPublisher.plan(listOf(entry("tt1", position = 0), entry("tt2", duration = 0), entry("tt3")), emptyMap())
        assertEquals(listOf("tt3"), plan.insert.map { it.id })
    }
}
