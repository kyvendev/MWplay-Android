package com.stremio.mobile.data.model

data class LocalStreamSelection(
    val key: String,
    val addonTitle: String,
    val name: String,
    val description: String?,
    val quality: String?,
) {
    fun matches(option: StreamOption): Boolean {
        if (option.key == key) return true
        return option.addonTitle == addonTitle &&
            option.name == name &&
            option.description == description &&
            option.quality == quality
    }

    /**
     * For an episode never played before: a stream from the same server (addon) as last time,
     * preferring the same name and quality.
     */
    fun pickSameServer(options: List<StreamOption>): StreamOption? {
        val sameServer = options.filter { it.addonTitle == addonTitle }
        return sameServer.firstOrNull { it.name == name && it.quality == quality }
            ?: sameServer.firstOrNull { quality != null && it.quality == quality }
            ?: sameServer.firstOrNull()
    }
}
