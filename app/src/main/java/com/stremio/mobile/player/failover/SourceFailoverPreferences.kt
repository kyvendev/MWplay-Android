package com.stremio.mobile.player.failover

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device preference for the automatic switch (on by default); the addon order is in [SourceFailoverConfig]. */
class SourceFailoverPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("mwplay_source_failover", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(SourceFailoverConfig(enabled = preferences.getBoolean(KEY_ENABLED, true)))
    val config: StateFlow<SourceFailoverConfig> = _config.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(enabled = enabled)
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
