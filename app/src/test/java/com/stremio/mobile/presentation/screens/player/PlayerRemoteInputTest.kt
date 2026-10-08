package com.stremio.mobile.presentation.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRemoteInputTest {
    @Test
    fun `confirm and arrows reach focused controls while controls are visible`() {
        for (key in listOf(PlayerRemoteKey.NAVIGATION, PlayerRemoteKey.ACTIVATE)) {
            assertEquals(PlayerRemoteAction.HAND_OFF, action(key, visible = true))
        }
    }

    @Test
    fun `confirm and arrows first reveal hidden controls without changing playback`() {
        for (key in listOf(PlayerRemoteKey.NAVIGATION, PlayerRemoteKey.ACTIVATE)) {
            assertEquals(PlayerRemoteAction.SHOW_CONTROLS, action(key, visible = false))
        }
    }

    @Test
    fun `dialogs and playback error overlays retain all input`() {
        for (key in PlayerRemoteKey.entries) {
            assertEquals(PlayerRemoteAction.HAND_OFF, action(key, overlay = true))
        }
    }

    @Test
    fun `media shortcuts work with hidden or visible controls`() {
        for (visible in listOf(false, true)) {
            assertEquals(PlayerRemoteAction.TOGGLE_PLAYBACK, action(PlayerRemoteKey.PLAY_PAUSE, visible))
            assertEquals(PlayerRemoteAction.SEEK_BACK, action(PlayerRemoteKey.REWIND, visible))
            assertEquals(PlayerRemoteAction.SEEK_FORWARD, action(PlayerRemoteKey.FAST_FORWARD, visible))
        }
    }

    @Test
    fun `held play pause does not repeatedly toggle but held seek continues`() {
        assertEquals(PlayerRemoteAction.HAND_OFF, action(PlayerRemoteKey.PLAY_PAUSE, repeat = true))
        assertEquals(PlayerRemoteAction.SEEK_FORWARD, action(PlayerRemoteKey.FAST_FORWARD, repeat = true))
    }

    @Test
    fun `release never activates a shortcut and next requires a video`() {
        assertEquals(PlayerRemoteAction.HAND_OFF, action(PlayerRemoteKey.PLAY_PAUSE, down = false))
        assertEquals(PlayerRemoteAction.HAND_OFF, action(PlayerRemoteKey.NEXT))
        assertEquals(PlayerRemoteAction.PLAY_NEXT, action(PlayerRemoteKey.NEXT, next = true))
    }

    private fun action(
        key: PlayerRemoteKey,
        visible: Boolean = true,
        overlay: Boolean = false,
        down: Boolean = true,
        repeat: Boolean = false,
        next: Boolean = false,
    ) = playerRemoteAction(key, visible, overlay, down, repeat, next)
}
