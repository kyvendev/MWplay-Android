package com.stremio.mobile.canais

import com.stremio.core.models.Catalog
import com.stremio.core.models.CatalogWithFilters
import com.stremio.core.types.addon.ExtraValue
import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf

/** A BestCine-like TV catalog: "Canais" with a genre filter and a "Canais | Esportes" catalog. */
internal object ChannelsFixtures {
    private const val BASE = "https://bestcine.example/manifest.json"
    val all = ResourceRequest(BASE, ResourcePath("catalog", "tv", "canais"))
    val sports = ResourceRequest(BASE, ResourcePath("catalog", "tv", "canais-esportes"))
    val news = all.copy(path = all.path.copy(extra = listOf(ExtraValue("genre", "Canais | Notícias"))))
    /** A loose genre of the source: not a "Canais | …" group, so it is not a tag. */
    val anime = all.copy(path = all.path.copy(extra = listOf(ExtraValue("genre", "Anime"))))

    fun channel(id: String, name: String, vararg genres: String) =
        CatalogItem(id, "tv", name, null, null, null, null, genres = genres.toList())

    val globo = channel("bc:globo", "Globo", "Abertos")
    val sportv = channel("bc:sportv", "SporTV", "Esportes")
    val espn = channel("bc:espn", "ESPN", "Esportes")
    val cnn = channel("bc:cnn", "CNN Brasil", "Notícias")
    val channels = listOf(globo, sportv, espn, cnn)

    fun filters(sportsSelected: Boolean = false, animeSelected: Boolean = false) = CatalogWithFilters(
        selected = CatalogWithFilters.Selected(if (sportsSelected) sports else all),
        selectable = CatalogWithFilters.Selectable(
            types = listOf(CatalogWithFilters.SelectableType("tv", true, all)),
            catalogs = listOf(
                CatalogWithFilters.SelectableCatalog("Canais", !sportsSelected, all),
                CatalogWithFilters.SelectableCatalog("Canais | Esportes", sportsSelected, sports),
            ),
            extra = listOf(
                CatalogWithFilters.SelectableExtra(
                    "genre",
                    false,
                    listOf(
                        CatalogWithFilters.SelectableExtraOption(null, !sportsSelected && !animeSelected, all),
                        CatalogWithFilters.SelectableExtraOption("Anime", animeSelected, anime),
                        CatalogWithFilters.SelectableExtraOption("Canais | Notícias", false, news),
                    ),
                ),
            ),
        ),
        catalog = Catalog(),
    )

    fun shelf(items: List<CatalogItem> = channels) = CatalogShelf(title = "TV", items = items, isLoading = false)
}
