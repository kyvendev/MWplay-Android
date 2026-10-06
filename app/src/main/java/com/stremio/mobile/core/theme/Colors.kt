package com.stremio.mobile.core.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared horizontal screen gutter so Home/Discover/Library/Search align to the same left edge. */
val ScreenGutter = 16.dp

/** Space reserved at the bottom of scrollable content so the last row clears the floating bottom bar. */
val BottomBarSpace = 96.dp

// MW Play visual foundation: cinema black with purple/electric-blue accents.
val StremioBackground = Color(0xFF070812)
val SearchBackground = Color(0xFF171925)
val CardFallback = Color(0xFF11131F)
val AccentPurple = Color(0xFF825CFF)
val MutedText = Color(0xFFB9B9C8)
val GlassSurface = Color(0xCC171925)
val AccentGreen = Color(0xFF4E8CFF)

val StremioBackgroundBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF05060C),
        Color(0xFF111329),
        Color(0xFF21134A),
    ),
)
