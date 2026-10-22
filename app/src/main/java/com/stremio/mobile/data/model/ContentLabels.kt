package com.stremio.mobile.data.model

import java.util.Locale

/**
 * Portuguese labels for what addons send in English (content types, catalog names and genres).
 * Unknown values are shown as they come, so new addon catalogs never disappear.
 */
object ContentLabels {
    private val ptBr = Locale.forLanguageTag("pt-BR")

    fun type(type: String?): String = when (val value = type?.trim()?.lowercase(Locale.ROOT)) {
        null, "" -> "Todos os tipos"
        "movie" -> "Filmes"
        "series" -> "Séries"
        "anime" -> "Anime"
        "channel" -> "Canais"
        "tv" -> "TV"
        "other" -> "Outros"
        "events" -> "Eventos"
        "documentary" -> "Documentários"
        else -> value.replaceFirstChar { it.titlecase(ptBr) }
    }

    private val catalogNames = mapOf(
        "popular" to "Populares",
        "featured" to "Em destaque",
        "new" to "Lançamentos",
        "newest" to "Lançamentos",
        "new releases" to "Lançamentos",
        "trending" to "Em alta",
        "top" to "Mais vistos",
        "top rated" to "Mais bem avaliados",
        "latest" to "Mais recentes",
        "last videos" to "Vídeos recentes",
        "recently added" to "Adicionados recentemente",
        "calendar" to "Calendário",
        "calendar-videos" to "Calendário",
        "year" to "Por ano",
        "by year" to "Por ano",
        "library" to "Biblioteca",
        "search" to "Busca",
        "discover" to "Descobrir",
        "channels" to "Canais",
        "live" to "Ao vivo",
        "live tv" to "TV ao vivo",
        "movies" to "Filmes",
        "series" to "Séries",
        "popular movies" to "Filmes populares",
        "popular series" to "Séries populares",
        "featured movies" to "Filmes em destaque",
        "featured series" to "Séries em destaque",
    )

    /** Catalog name as shown in shelves and the Discover/TV filters. */
    fun catalog(name: String?): String {
        val value = name?.trim().orEmpty()
        return catalogNames[value.lowercase(Locale.ROOT)] ?: value
    }

    /**
     * Home shelf title: Cinemeta's generic "Popular"/"Featured" catalogs get the content type, the
     * way the app always showed them ("Popular Movies"), now in Portuguese ("Filmes populares").
     */
    fun shelfTitle(catalogName: String?, catalogType: String?): String {
        val raw = catalogName?.trim().orEmpty()
        val typeLabel = when (catalogType) {
            "movie" -> "Filmes"
            "series" -> "Séries"
            else -> null
        }
        return when {
            typeLabel != null && raw.equals("popular", ignoreCase = true) -> "$typeLabel populares"
            typeLabel != null && raw.equals("featured", ignoreCase = true) -> "$typeLabel em destaque"
            else -> catalog(raw)
        }
    }

    private val genres = mapOf(
        "action" to "Ação",
        "adventure" to "Aventura",
        "animation" to "Animação",
        "anime" to "Anime",
        "biography" to "Biografia",
        "business" to "Negócios",
        "classic" to "Clássicos",
        "comedy" to "Comédia",
        "cooking" to "Culinária",
        "crime" to "Crime",
        "documentary" to "Documentário",
        "drama" to "Drama",
        "educational" to "Educativo",
        "entertainment" to "Entretenimento",
        "family" to "Família",
        "fantasy" to "Fantasia",
        "film-noir" to "Noir",
        "game-show" to "Game show",
        "general" to "Geral",
        "history" to "História",
        "horror" to "Terror",
        "isekai" to "Isekai",
        "kids" to "Infantil",
        "lifestyle" to "Estilo de vida",
        "martial arts" to "Artes marciais",
        "mecha" to "Mecha",
        "music" to "Música",
        "musical" to "Musical",
        "mystery" to "Mistério",
        "nature" to "Natureza",
        "news" to "Notícias",
        "psychological" to "Psicológico",
        "reality" to "Reality show",
        "reality-tv" to "Reality show",
        "religious" to "Religioso",
        "romance" to "Romance",
        "school" to "Escolar",
        "sci-fi" to "Ficção científica",
        "science fiction" to "Ficção científica",
        "short" to "Curta-metragem",
        "slice of life" to "Cotidiano",
        "sport" to "Esporte",
        "sports" to "Esportes",
        "supernatural" to "Sobrenatural",
        "talk-show" to "Talk show",
        "thriller" to "Suspense",
        "travel" to "Viagem",
        "tv movie" to "Filme para TV",
        "war" to "Guerra",
        "weather" to "Clima",
        "western" to "Faroeste",
    )

    fun genre(genre: String?): String {
        val value = genre?.trim().orEmpty()
        return genres[value.lowercase(Locale.ROOT)] ?: value
    }

    /** Value of a Discover extra filter (genre, year…); null means "all". */
    fun extraOption(extraName: String, value: String?): String = when {
        value == null && extraName.equals("genre", ignoreCase = true) -> "Todos os gêneros"
        value == null -> "Todos"
        extraName.equals("genre", ignoreCase = true) -> genre(value)
        else -> value
    }
}

/**
 * The TV tab shows the catalogs of type "tv" from the installed addons. Best Cine's catalog is
 * preferred; any other addon that offers TV catalogs appears in the same tab's catalog filter.
 */
object TvCatalogPolicy {
    const val TV_TYPE = "tv"

    private val preferredProviders = listOf(Regex("best\\s*-?\\s*cine", RegexOption.IGNORE_CASE))

    fun isTv(type: String?): Boolean = type.equals(TV_TYPE, ignoreCase = true)

    /**
     * The catalog to open first: one whose name, addon URL or addon name matches a preferred
     * provider, otherwise the first one.
     */
    fun <T> preferredCatalog(
        catalogs: List<T>,
        name: (T) -> String,
        transportUrl: (T) -> String,
        addonName: (String) -> String?,
    ): T? = catalogs.firstOrNull { catalog ->
        val haystack = listOfNotNull(name(catalog), transportUrl(catalog), addonName(transportUrl(catalog)))
        haystack.any { text -> preferredProviders.any { it.containsMatchIn(text) } }
    } ?: catalogs.firstOrNull()
}
