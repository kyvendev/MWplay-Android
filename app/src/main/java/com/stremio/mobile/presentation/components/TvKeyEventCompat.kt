package com.stremio.mobile.presentation.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType

/**
 * Local key-event compatibility helpers for Android TV / D-pad navigation.
 *
 * Compose exposes these values as extensions in newer APIs. Keeping the
 * mapping here avoids depending on implicit imports and makes the remote
 * behavior explicit for the MW Play navigation bar.
 */
val KeyEvent.type: KeyEventType
    get() = if (nativeKeyEvent.action == AndroidKeyEvent.ACTION_UP) {
        KeyEventType.KeyUp
    } else {
        KeyEventType.KeyDown
    }

val KeyEvent.key: Key
    get() = when (nativeKeyEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_ENTER -> Key.Enter
        AndroidKeyEvent.KEYCODE_DPAD_CENTER -> Key.DirectionCenter
        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> Key.NumPadEnter
        else -> Key.Back
    }
