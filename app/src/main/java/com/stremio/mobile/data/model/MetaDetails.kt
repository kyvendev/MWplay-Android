package com.stremio.mobile.data.model

data class MetaDetails(
    val item: CatalogItem,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val runtime: String? = null,
    val year: String? = null,
    val trailer: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** The synopsis is being translated to Portuguese; [description] still holds the original. */
    val translatingDescription: Boolean = false,
    /** [description] was translated automatically. */
    val descriptionTranslated: Boolean = false,
)
