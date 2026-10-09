package com.stremio.mobile.presentation.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stremio.mobile.core.theme.AccentGlow

/** True on Android TV / Google TV devices. */
@Composable
fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    val modeType = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK
    return remember(context, modeType) {
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        modeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }
}

/** Focus fill behind text/icon controls; posters cover it with artwork. */
internal val TvFocusFill = Color(0xFF7550F0)
internal val TvFocusRing = Color.White

/**
 * Strong, lightweight visual treatment for an existing focusable/clickable TV target.
 * No extra focus node is created and no per-card animation is run: this keeps D-pad
 * navigation predictable and avoids doing animation work across large catalog rows.
 *
 * The focused element is lifted with a hardware (RenderNode) shadow tinted with the brand
 * glow, which costs nothing while unfocused and needs no blur or offscreen layer.
 */
@Composable
fun Modifier.tvFocusTarget(
    enabled: Boolean = true,
    cornerRadius: Dp = 14.dp,
    focusedScale: Float = 1.08f,
): Modifier {
    if (!rememberIsTelevision() || !enabled) return this

    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadius)

    return this
        .onFocusChanged { focused = it.hasFocus }
        .graphicsLayer {
            val scale = if (focused) focusedScale else 1f
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = true
            shadowElevation = if (focused) 14.dp.toPx() else 0f
            ambientShadowColor = AccentGlow
            spotShadowColor = AccentGlow
        }
        .background(if (focused) TvFocusFill else Color.Transparent)
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) TvFocusRing else Color.Transparent,
            shape = shape,
        )
}

/** Opens the software keyboard with a remote's select key on an already focused field. */
@Composable
fun Modifier.tvTextInput(): Modifier {
    if (!rememberIsTelevision()) return this
    val keyboardController = LocalSoftwareKeyboardController.current
    return this
        .tvFocusTarget(cornerRadius = 12.dp, focusedScale = 1f)
        .onPreviewKeyEvent { event ->
            when (event.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.type == KeyEventType.KeyUp) keyboardController?.show()
                    true
                }
                else -> false
            }
        }
}

