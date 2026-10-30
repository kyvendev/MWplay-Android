package com.stremio.mobile.data.model

import java.text.Normalizer

/**
 * Which TV channels are sports channels, for the "Esportes" section of the TV tab.
 *
 * A channel is sports only through metadata, never because its name contains a sports word:
 * 1. the explicit lists below (fixes for channels a source classifies wrongly);
 * 2. the category it was listed under: a sports catalog (BestCine's "Canais | Esportes") or the
 *    addon's genre filter (e.g. "Esportes");
 * 3. its own genres from the addon's metadata.
 * Category names are compared whole (accents and case ignored), so "Futebol" matches but
 * "Canal Esportivo da Cidade" as a channel name does not.
 */
object SportsChannelPolicy {
    /** Category / genre names that mean sport (Portuguese and the English used by addons). */
    val SPORTS_CATEGORIES: Set<String> = setOf(
        "Esportes", "Esporte", "Esportes em geral", "Outros esportes", "Futebol", "Automobilismo",
        "Basquete", "Lutas", "Tênis", "Sports", "Sport", "Football", "Soccer", "Motorsport",
        "Basketball", "Fighting", "Tennis",
    ).mapTo(mutableSetOf(), ::normalize)

    /** TV catalogs that list only sports channels, by name. */
    val SPORTS_CATALOG_NAMES: Set<String> = setOf("Canais | Esportes").mapTo(mutableSetOf(), ::normalize)

    /** Channel ids always treated as sports, whatever the source says. */
    val FORCED_SPORTS_IDS: Set<String> = emptySet()

    /** Channel ids never treated as sports (a source listed them under sports by mistake). */
    val NOT_SPORTS_IDS: Set<String> = emptySet()

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")

    fun isSportsCategory(name: String?): Boolean = name != null && normalize(name) in SPORTS_CATEGORIES

    /**
     * A sports catalog: listed in [SPORTS_CATALOG_NAMES], or named "<group> | <sports category>"
     * like "Canais | Esportes" (each part compared whole).
     */
    fun isSportsCatalog(name: String?): Boolean {
        if (name == null) return false
        if (normalize(name) in SPORTS_CATALOG_NAMES) return true
        val parts = name.split('|')
        return parts.size > 1 && isSportsCategory(parts.last())
    }

    /**
     * Whether [id] with [genres] is a sports channel. [listedUnderSports] = it came from a
     * sports category of the source.
     */
    fun isSportsChannel(
        id: String,
        genres: List<String>,
        listedUnderSports: Boolean,
        forced: Set<String> = FORCED_SPORTS_IDS,
        excluded: Set<String> = NOT_SPORTS_IDS,
    ): Boolean = when {
        id in excluded -> false
        id in forced -> true
        listedUnderSports -> true
        else -> genres.any(::isSportsCategory)
    }

    /** The sports channels of [items], each once, in the source's order. */
    fun <T> sportsChannels(
        items: List<T>,
        id: (T) -> String,
        genres: (T) -> List<String>,
        listedUnderSports: Boolean,
        forced: Set<String> = FORCED_SPORTS_IDS,
        excluded: Set<String> = NOT_SPORTS_IDS,
    ): List<T> = items
        .filter { isSportsChannel(id(it), genres(it), listedUnderSports, forced, excluded) }
        .distinctBy(id)
}

/**
 * The category row of the TV tab: "Todos os canais", "Esportes" and the source's other
 * categories, unchanged and in its order. Esportes opens the source's sports catalog
 * ("Canais | Esportes"), otherwise its sports genre, otherwise every channel filtered by
 * metadata. [R] is the request that loads a category.
 */
object TvCategories {
    data class Option<R>(val value: String?, val selected: Boolean, val request: R)

    enum class Kind { ALL, SPORTS, OTHER }

    data class Chip<R>(val label: String, val kind: Kind, val request: R, val selected: Boolean)

    data class Row<R>(
        val chips: List<Chip<R>>,
        /** Sports catalogs or categories of the source, shown under "Esportes" when there are several. */
        val sportsSubcategories: List<Chip<R>>,
        /** The Esportes section is open. */
        val sportsOpen: Boolean,
        /** The channels on screen come from a sports catalog or category of the source. */
        val listedUnderSports: Boolean,
        /** The source has no sports catalog or category: Esportes filters every channel by its metadata. */
        val sportsFromMetadata: Boolean,
    )

    /**
     * [genres]: the selected catalog's genre options. [catalogs]: the TV catalogs (value = name).
     * [sportsByMetadata] = the user opened Esportes on a source without sports catalog/category.
     * [allRequest] loads every channel when nothing better exists (the selected catalog).
     */
    fun <R> build(
        genres: List<Option<R>>,
        sportsByMetadata: Boolean,
        label: (String) -> String,
        allRequest: R? = null,
        catalogs: List<Option<R>> = emptyList(),
    ): Row<R> {
        val sportsCatalogs = catalogs.filter { SportsChannelPolicy.isSportsCatalog(it.value) }
        val inSportsCatalog = sportsCatalogs.any { it.selected }
        val sportsGenres = genres.filter { SportsChannelPolicy.isSportsCategory(it.value) }
        val selectedSportsGenre = sportsGenres.firstOrNull { it.selected }
        val sportsFromMetadata = sportsCatalogs.isEmpty() && sportsGenres.isEmpty()
        val sportsOpen = inSportsCatalog || selectedSportsGenre != null || (sportsByMetadata && sportsFromMetadata)

        val allGenre = genres.firstOrNull { it.value == null }
        // Inside the sports catalog, "Todos os canais" goes back to the first other TV catalog.
        val all = if (inSportsCatalog) {
            catalogs.firstOrNull { it !in sportsCatalogs }?.request
        } else {
            allGenre?.request ?: allRequest
        }
        val allSelected = !sportsOpen && (allGenre?.selected ?: genres.none { it.selected })

        val chips = buildList<Chip<R>> {
            if (all != null) add(Chip("Todos os canais", Kind.ALL, all, allSelected))
            val sportsRequest = sportsCatalogs.firstOrNull()?.request ?: sportsGenres.firstOrNull()?.request ?: all
            if (sportsRequest != null) add(Chip("Esportes", Kind.SPORTS, sportsRequest, sportsOpen))
            // The other categories belong to the catalog on screen; the sports catalog has none.
            if (!inSportsCatalog) {
                genres.filter { it.value != null && it !in sportsGenres }
                    .forEach { add(Chip(label(it.value!!), Kind.OTHER, it.request, it.selected && !sportsOpen)) }
            }
        }
        val sub = when {
            sportsCatalogs.size > 1 -> sportsCatalogs.map { Chip(it.value.orEmpty(), Kind.SPORTS, it.request, it.selected) }
            sportsCatalogs.isEmpty() && sportsGenres.size > 1 -> sportsGenres.map { Chip(label(it.value!!), Kind.SPORTS, it.request, it.selected) }
            else -> emptyList()
        }
        return Row(
            chips = chips,
            sportsSubcategories = sub,
            sportsOpen = sportsOpen,
            listedUnderSports = inSportsCatalog || selectedSportsGenre != null,
            sportsFromMetadata = sportsFromMetadata,
        )
    }
}
