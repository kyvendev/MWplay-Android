package com.stremio.mobile.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.stremio.core.models.StreamingServer
import com.stremio.core.types.profile.Profile
import timber.log.Timber

/**
 * MW Play defaults for a brand-new installation, applied once through the existing settings
 * paths (Core profile settings and the streaming server's own settings).
 *
 * The fresh-install decision is taken the first time this runs and stored, so app updates and
 * re-opens never re-apply it and existing users (who updated rather than installed) are left
 * untouched. Each half is marked done only after it was actually applied, so a failed attempt
 * (e.g. server not reachable) is retried on the next start instead of being lost.
 */
class FirstRunDefaults(
    private val prefs: SharedPreferences,
    isFreshInstall: () -> Boolean,
) {
    init {
        if (!readBoolean(KEY_DECIDED, false)) {
            val fresh = runCatching(isFreshInstall).getOrElse {
                Timber.w(it, "Unable to tell whether this is a fresh install; keeping current settings")
                false
            }
            prefs.edit()
                .putBoolean(KEY_DECIDED, true)
                .putBoolean(KEY_PROFILE_PENDING, fresh)
                .putBoolean(KEY_SERVER_PENDING, fresh)
                .commit()
        }
    }

    val profilePending: Boolean get() = readBoolean(KEY_PROFILE_PENDING, false)
    val serverPending: Boolean get() = readBoolean(KEY_SERVER_PENDING, false)

    fun applyTo(settings: Profile.Settings): Profile.Settings = settings.copy(
        interfaceLanguage = INTERFACE_LANGUAGE,
        audioLanguage = AUDIO_LANGUAGE,
        subtitlesLanguage = SUBTITLES_LANGUAGE,
    )

    fun applyTo(settings: StreamingServer.Settings): StreamingServer.Settings = settings.copy(
        cacheSize = CACHE_SIZE_BYTES,
        btMaxConnections = BT_MAX_CONNECTIONS_ULTRA,
    )

    fun markProfileApplied() {
        prefs.edit().putBoolean(KEY_PROFILE_PENDING, false).apply()
    }

    fun markServerApplied() {
        prefs.edit().putBoolean(KEY_SERVER_PENDING, false).apply()
    }

    /** Tolerates a corrupted value (wrong type) instead of crashing startup. */
    private fun readBoolean(key: String, default: Boolean): Boolean =
        runCatching { prefs.getBoolean(key, default) }.getOrDefault(default)

    companion object {
        const val PREFS_NAME = "mwplay_first_run_defaults"
        private const val KEY_DECIDED = "decided_v1"
        private const val KEY_PROFILE_PENDING = "profile_pending_v1"
        private const val KEY_SERVER_PENDING = "server_pending_v1"

        /** Existing "Português" option of the interface language list. */
        const val INTERFACE_LANGUAGE = "por"
        /** Audio tracks are tagged ISO 639-2 "por"; "pob" would never match a real track. */
        const val AUDIO_LANGUAGE = "por"
        /** "Português Brazil" in the subtitle language list (addons tag pt-BR subtitles "pob"). */
        const val SUBTITLES_LANGUAGE = "pob"
        /** "5 GB" option of the server cache list (bytes). */
        const val CACHE_SIZE_BYTES = 5_368_709_120.0
        /** "Ultra rápido (sem limite)" option of the torrent profile list. */
        const val BT_MAX_CONNECTIONS_ULTRA = 800L
        /** Streaming server "Seeding" is off by default. */
        const val SEEDING_ENABLED = false

        fun create(context: Context): FirstRunDefaults {
            val app = context.applicationContext
            return FirstRunDefaults(app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)) {
                @Suppress("DEPRECATION")
                val info = app.packageManager.getPackageInfo(app.packageName, 0)
                val neverUpdated = info.firstInstallTime == info.lastUpdateTime
                // A saved session means this is not a first run (e.g. restored from a backup).
                val hasSession = !app.getSharedPreferences("stremio_account", Context.MODE_PRIVATE)
                    .getString("authKey", null).isNullOrBlank()
                neverUpdated && !hasSession
            }
        }
    }
}
