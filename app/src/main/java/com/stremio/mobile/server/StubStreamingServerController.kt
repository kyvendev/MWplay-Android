package com.stremio.mobile.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fallback used when the optional native streaming server is not packaged for the
 * current ABI. Keep the app usable for direct HTTP(S)/HLS/DASH playback instead
 * of turning the missing native component into a fatal startup error.
 */
class StubStreamingServerController : StreamingServerController {
    private val mutableState = MutableStateFlow<StreamingServerState>(StreamingServerState.Stopped)

    override val state: StateFlow<StreamingServerState> = mutableState

    override val isNativeServerAvailable: Boolean = false

    override suspend fun start() {
        // Deliberately remain stopped. Callers that actually require the local
        // server will receive the normal "did not start" playback error, while
        // login, catalog browsing and direct streams remain available.
        mutableState.value = StreamingServerState.Stopped
    }

    override suspend fun stop() {
        mutableState.value = StreamingServerState.Stopped
    }
}
