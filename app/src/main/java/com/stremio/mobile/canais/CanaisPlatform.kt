package com.stremio.mobile.canais

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Phone build of MW Play Canais: no TV remote layer. */
@Composable
internal fun isTvDevice(): Boolean = false

/** TV focus ring on the TV build; nothing on phones. */
@Composable
internal fun Modifier.channelFocus(cornerRadius: Dp, focusedScale: Float): Modifier = this
