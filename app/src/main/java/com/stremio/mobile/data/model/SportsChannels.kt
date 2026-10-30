package com.stremio.mobile.data.model

import java.text.Normalizer

/**
 * Which TV channels are sports channels, for the "Esportes" section of the TV tab.
 *
 * A channel is sports only through metadata, never because its name contains a sports word:
 * 1. the explicit lists below (fixes for channels a source classifies wrongly);
 * 2. the category it was listed under (the addon's genre filter, e.g. "Esportes");
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
 * The category row of the TV tab, built from the source's genre filter: "Todos os canais",
 * "Esportes" and the source's other categories, unchanged and in its order. [R] is the request
 * that loads a category.
 */
object TvCategories {
    data class Option<R>(val value: String?, val selected: Boolean, val request: R)

    enum class Kind { ALL, SPORTS, OTHER }

    data class Chip<R>(val label: String, val kind: Kind, val request: R, val selected: Boolean)

    data class Row<R>(
        val chips: List<Chip<R>>,
        /** Sports categories of the source, shown under "Esportes" when it has more than one. */
        val sportsSubcategories: List<Chip<R>>,
        /** The Esportes section is open. */
        val sportsOpen: Boolean,
        /** The channels on screen come from a sports category of the source. */
        val listedUnderSports: Boolean,
        /** The source has no sports category: Esportes filters every channel by its metadata. */
        val sportsFromMetadata: Boolean,
    )

    /**
     * [sportsByMetadata] = the user opened Esportes on a source without a sports category, so
     * the whole list is shown filtered by each channel's metadata. [allRequest] loads every
     * channel when the source's filter has no "all" option (or no genre filter at all).
     */
    fun <R> build(options: List<Option<R>>, sportsByMetadata: Boolean, label: (String) -> String, allRequest: R? = null): Row<R> {
        val allOption = options.firstOrNull { it.value == null }
        val all = allOption?.request ?: allRequest
        val sports = options.filter { SportsChannelPolicy.isSportsCategory(it.value) }
        val selectedSports = sports.firstOrNull { it.selected }
        val sportsOpen = selectedSports != null || (sportsByMetadata && sports.isEmpty())
        val allSelected = allOption?.selected ?: options.none { it.selected }
        val chips = buildList<Chip<R>> {
            if (all != null) add(Chip("Todos os canais", Kind.ALL, all, allSelected && !sportsOpen))
            val sportsRequest = sports.firstOrNull()?.request ?: all
            if (sportsRequest != null) add(Chip("Esportes", Kind.SPORTS, sportsRequest, sportsOpen))
            options.filter { it.value != null && it !in sports }
                .forEach { add(Chip(label(it.value!!), Kind.OTHER, it.request, it.selected && !sportsOpen)) }
        }
        val sub = if (sports.size > 1) sports.map { Chip(label(it.value!!), Kind.SPORTS, it.request, it.selected) } else emptyList()
        return Row(chips, sub, sportsOpen, listedUnderSports = selectedSports != null, sportsFromMetadata = sports.isEmpty())
    }
}
