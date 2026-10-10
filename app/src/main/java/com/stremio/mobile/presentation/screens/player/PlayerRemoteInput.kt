package com.stremio.mobile.presentation.screens.player

/** Remote shortcuts must never replace navigation or activation of a focused control. */
internal enum class PlayerRemoteKey {
    NAVIGATION, ACTIVATE, PLAY_PAUSE, PLAY, PAUSE, REWIND, FAST_FORWARD, NEXT, OTHER,
}

internal enum class PlayerRemoteAction {
    HAND_OFF, SHOW_CONTROLS, TOGGLE_PLAYBACK, PLAY, PAUSE, SEEK_BACK, SEEK_FORWARD, PLAY_NEXT,
}

internal fun playerRemoteAction(
    key: PlayerRemoteKey,
    controlsVisible: Boolean,
    overlayVisible: Boolean,
    isKeyDown: Boolean,
    isRepeat: Boolean = false,
    hasNextVideo: Boolean = false,
): PlayerRemoteAction {
    if (overlayVisible || !isKeyDown) return PlayerRemoteAction.HAND_OFF

    return when (key) {
        PlayerRemoteKey.NAVIGATION, PlayerRemoteKey.ACTIVATE ->
            if (controlsVisible) PlayerRemoteAction.HAND_OFF else PlayerRemoteAction.SHOW_CONTROLS
        PlayerRemoteKey.PLAY_PAUSE ->
            if (isRepeat) PlayerRemoteAction.HAND_OFF else PlayerRemoteAction.TOGGLE_PLAYBACK
        PlayerRemoteKey.PLAY -> PlayerRemoteAction.PLAY
        PlayerRemoteKey.PAUSE -> PlayerRemoteAction.PAUSE
        PlayerRemoteKey.REWIND -> PlayerRemoteAction.SEEK_BACK
        PlayerRemoteKey.FAST_FORWARD -> PlayerRemoteAction.SEEK_FORWARD
        PlayerRemoteKey.NEXT ->
            if (hasNextVideo && !isRepeat) PlayerRemoteAction.PLAY_NEXT else PlayerRemoteAction.HAND_OFF
        PlayerRemoteKey.OTHER -> PlayerRemoteAction.HAND_OFF
    }
}
