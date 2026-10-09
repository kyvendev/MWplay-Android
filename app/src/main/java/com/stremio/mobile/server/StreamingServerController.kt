package com.stremio.mobile.server

import kotlinx.coroutines.flow.StateFlow

sealed interface StreamingServerState {
    data object Stopped : StreamingServerState
    data object Starting : StreamingServerState
    data class Ready(val baseUrl: String) : StreamingServerState
    data class Failed(val message: String) : StreamingServerState
}

interface StreamingServerController {
    val state: StateFlow<StreamingServerState>

    /** False when the native server library is missing from this build (stub fallback). */
    val isNativeServerAvailable: Boolean get() = true

    suspend fun start()

    suspend fun stop()
}
