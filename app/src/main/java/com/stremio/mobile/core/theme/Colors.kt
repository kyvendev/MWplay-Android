package com.stremio.mobile.core.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared horizontal screen gutter so Home/Discover/Library/Search align to the same left edge. */
val ScreenGutter = 30.dp

/** Space reserved at the bottom of scrollable content so the last row clears the floating bottom bar. */
val BottomBarSpace = 96.dp

// MW Play visual foundation: deep cinema ink with a violet signature and electric-blue support.
val StremioBackground = Color(0xFF06070D)
val SearchBackground = Color(0xFF161925)
val CardFallback = Color(0xFF141725)
val AccentPurple = Color(0xFF8A63FF)
val MutedText = Color(0xFFB3B6C7)
val GlassSurface = Color(0xCC1A1D2B)
val AccentGreen = Color(0xFF4E8CFF)

// Layered surfaces: each step is slightly lighter so cards read on large dark panels.
val SurfaceLow = Color(0xFF0D0F18)
val SurfaceMid = Color(0xFF151826)
val SurfaceHigh = Color(0xFF1E2233)
val HairlineBorder = Color(0x1FFFFFFF)
val SubtleText = Color(0xFF7F8399)
val AccentGlow = Color(0xFF6F4BFF)

/** Opaque TV navigation surface; no backdrop blur so weak TV GPUs stay smooth. */
val TvRailBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFA090A12), Color(0xF20B0C16)),
)

val StremioBackgroundBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF0E0B1F),
        Color(0xFF08090F),
        Color(0xFF06070D),
    ),
)

/** Settings rows stay readable on wide TVs instead of stretching across the whole panel. */
val SettingsMaxWidth = 880.dp
