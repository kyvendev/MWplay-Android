package com.stremio.mobile.di

import android.content.Context
import timber.log.Timber
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.player.PlaybackManager
import com.stremio.mobile.server.StubStreamingServerController
import com.stremio.mobile.server.StreamingServerController

import com.stremio.mobile.data.repository.AddonRepository
import com.stremio.mobile.data.repository.AuthRepository
import com.stremio.mobile.data.repository.BoardRepository
import com.stremio.mobile.data.repository.CatalogRepository
import com.stremio.mobile.data.repository.PlaybackRepository
import com.stremio.mobile.update.ApkInstaller
import com.stremio.mobile.update.UpdateRepository

class AppContainer(context: Context) {
    val playbackManager = PlaybackManager(context.applicationContext)
    val core: StremioCore = StremioCore(context.applicationContext).apply {
        try {
            initialize()
        } catch (e: Throwable) {
            Timber.e(e, "Failed to initialize stremio-core")
        }
    }

    val authRepository = AuthRepository(core, context.applicationContext)
    val boardRepository = BoardRepository(core)
    val catalogRepository = CatalogRepository(core)
    val addonRepository = AddonRepository(core)
    val playbackRepository = PlaybackRepository(core, playbackManager)
    val updateRepository = UpdateRepository(context.applicationContext)
    val apkInstaller = ApkInstaller(context.applicationContext)

    // MW Play Canais plays live channels straight from their HTTP(S)/HLS links and never needs
    // the local torrent server. Starting it here would take port 11470 from MW Play when both
    // apps are installed ("A porta 11470 já está em uso"), so it stays off in this app.
    val serverController: StreamingServerController = StubStreamingServerController()

}
