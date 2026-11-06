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
    val news = all.copy(path = all.path.copy(extra = listOf(ExtraValue("genre", "Notícias"))))

    fun channel(id: String, name: String, vararg genres: String) =
        CatalogItem(id, "tv", name, null, null, null, null, genres = genres.toList())

    val globo = channel("bc:globo", "Globo", "Abertos")
    val sportv = channel("bc:sportv", "SporTV", "Esportes")
    val espn = channel("bc:espn", "ESPN", "Esportes")
    val cnn = channel("bc:cnn", "CNN Brasil", "Notícias")
    val channels = listOf(globo, sportv, espn, cnn)

    fun filters(sportsSelected: Boolean = false) = CatalogWithFilters(
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
                        CatalogWithFilters.SelectableExtraOption(null, !sportsSelected, all),
                        CatalogWithFilters.SelectableExtraOption("Notícias", false, news),
                    ),
                ),
            ),
        ),
        catalog = Catalog(),
    )

    fun shelf(items: List<CatalogItem> = channels) = CatalogShelf(title = "TV", items = items, isLoading = false)
}
