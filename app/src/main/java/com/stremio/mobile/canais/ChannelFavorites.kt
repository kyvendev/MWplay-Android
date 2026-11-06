package com.stremio.mobile.canais

import android.content.Context
import com.stremio.mobile.data.model.CatalogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favourite channels, kept on the device with their name and logo so the "Favoritos" category
 * works whatever catalog is on screen. Newest favourite first.
 */
class ChannelFavorites(context: Context) {
    private val preferences = context.getSharedPreferences("mwplay_channel_favorites", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<CatalogItem>> = _items.asStateFlow()

    fun isFavorite(item: CatalogItem): Boolean = _items.value.any { it.sameChannel(item) }

    /** Adds or removes [item]; returns true when it is now a favourite. */
    fun toggle(item: CatalogItem): Boolean {
        val current = _items.value
        val next = if (current.any { it.sameChannel(item) }) current.filterNot { it.sameChannel(item) } else listOf(item) + current
        _items.value = next
        write(next)
        return next.any { it.sameChannel(item) }
    }

    private fun read(): List<CatalogItem> = runCatching {
        val array = JSONArray(preferences.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            CatalogItem(
                id = json.getString("id"),
                type = json.getString("type"),
                name = json.optString("name"),
                poster = json.optString("poster").ifBlank { null },
                background = json.optString("background").ifBlank { null },
                releaseInfo = null,
                imdbRating = null,
                genres = json.optJSONArray("genres")?.let { genres -> (0 until genres.length()).map(genres::getString) }.orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(items: List<CatalogItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("type", item.type)
                    .put("name", item.name)
                    .put("poster", item.poster.orEmpty())
                    .put("background", item.background.orEmpty())
                    .put("genres", JSONArray(item.genres)),
            )
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "channels"
    }
}

internal fun CatalogItem.sameChannel(other: CatalogItem) = id == other.id && type == other.type

/** Previous / next channel in the list the user played from, wrapping around. */
object ChannelZapping {
    fun neighbour(queue: List<CatalogItem>, current: CatalogItem?, delta: Int): CatalogItem? {
        if (queue.isEmpty() || current == null) return null
        val index = queue.indexOfFirst { it.sameChannel(current) }
        if (index < 0) return null
        if (queue.size == 1) return null
        return queue[(index + delta).mod(queue.size)]
    }
}
