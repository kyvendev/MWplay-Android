package com.stremio.mobile.presentation.components

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 * Strong visual treatment for an existing focusable/clickable target on Android TV.
 *
 * IMPORTANT: this modifier intentionally does not add its own focusable() node.
 * Compose clickable/Button components already participate in focus traversal. Adding
 * another focusable node here creates duplicate D-pad stops and was one of the causes
 * of apparently stuck/invisible remote navigation.
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
        .onFocusChanged { focused = it.hasFocus }
        .scale(scale)
        .clip(shape)
        .background(if (focused) AccentPurple.copy(alpha = 0.82f) else Color.Transparent)
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = shape,
        )
}
