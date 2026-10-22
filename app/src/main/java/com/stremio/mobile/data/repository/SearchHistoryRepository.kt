package com.stremio.mobile.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** List rules for the recent searches, kept free of Android types so they can be unit tested. */
object SearchHistoryPolicy {
    const val MAX_ENTRIES = 20
    private const val MIN_LENGTH = 2
    private val whitespace = Regex("\\s+")

    fun normalize(query: String): String = query.trim().replace(whitespace, " ")

    /** Most recent first; repeating a search (any letter case) moves it to the top. */
    fun add(history: List<String>, query: String): List<String> {
        val entry = normalize(query)
        if (entry.length < MIN_LENGTH) return history
        return (listOf(entry) + history.filterNot { it.equals(entry, ignoreCase = true) }).take(MAX_ENTRIES)
    }

    fun remove(history: List<String>, query: String): List<String> = history.filterNot { it == query }

    // Entries are single-line (normalize collapses whitespace), so a newline is a safe separator.
    fun encode(history: List<String>): String = history.joinToString("\n")

    fun decode(raw: String?): List<String> =
        raw.orEmpty().split('\n').map(::normalize).filter { it.length >= MIN_LENGTH }.distinct().take(MAX_ENTRIES)
}

/** Recent searches, stored on this device only. */
class SearchHistoryRepository(context: Context) {
    private val preferences = context.getSharedPreferences("mwplay_search_history", Context.MODE_PRIVATE)
    private val _history = MutableStateFlow(SearchHistoryPolicy.decode(preferences.getString(KEY, null)))
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun add(query: String) = update(SearchHistoryPolicy.add(_history.value, query))

    fun remove(query: String) = update(SearchHistoryPolicy.remove(_history.value, query))

    private fun update(next: List<String>) {
        if (next == _history.value) return
        _history.value = next
        preferences.edit().putString(KEY, SearchHistoryPolicy.encode(next)).apply()
    }

    private companion object {
        const val KEY = "entries"
    }
}
