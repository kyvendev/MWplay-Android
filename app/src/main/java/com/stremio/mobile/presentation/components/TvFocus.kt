package com.stremio.mobile.presentation.components

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stremio.mobile.core.theme.AccentPurple

/** True on Android TV / Google TV devices. */
@Composable
fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}

/**
 * Strong, predictable focus treatment for remote/D-pad navigation.
 *
 * Mobile keeps its existing visuals. On TV the focused target grows and receives
 * an opaque purple focus plate plus a white outline so focus can never be invisible.
 */
@Composable
fun Modifier.tvFocusTarget(
    enabled: Boolean = true,
    cornerRadius: Dp = 14.dp,
    focusedScale: Float = 1.08f,
): Modifier {
    if (!rememberIsTelevision() || !enabled) return this

    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        label = "mwTvFocusScale",
    )
    val shape = RoundedCornerShape(cornerRadius)

    return this
        .scale(scale)
        .clip(shape)
        .background(if (focused) AccentPurple.copy(alpha = 0.72f) else Color.Transparent)
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = shape,
        )
        .onFocusChanged { focused = it.isFocused }
        .focusable(enabled)
}
