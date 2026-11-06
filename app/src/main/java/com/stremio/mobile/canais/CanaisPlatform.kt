package com.stremio.mobile.canais

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.stremio.mobile.presentation.components.rememberIsTelevision
import com.stremio.mobile.presentation.components.tvFocusTarget

/** TV build of MW Play Canais: remote-friendly layout and focus. */
@Composable
internal fun isTvDevice(): Boolean = rememberIsTelevision()

/** The app's TV focus ring (white border, fill and a light lift). */
@Composable
internal fun Modifier.channelFocus(cornerRadius: Dp, focusedScale: Float): Modifier = tvFocusTarget(cornerRadius = cornerRadius, focusedScale = focusedScale)
