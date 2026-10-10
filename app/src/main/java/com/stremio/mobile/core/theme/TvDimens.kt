package com.stremio.mobile.core.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// TV density scale. Most Android TVs report about 960x540dp, where the first redesign pass read as
// zoomed in. Each category is tuned on its own (roughly 10-15% more compact), never a global scale,
// and small labels stay at their legible minimum.

// Navigation rail
val TvRailWidth = 100.dp
val TvRailItemWidth = 88.dp
val TvRailItemHeight = 58.dp
/** Content start padding next to the rail, so opening it never reflows the catalog. */
val TvRailReservedWidth = 106.dp

// Catalog
val TvPosterWidth = 124.dp
val TvShelfSpacing = 16.dp
/** Vertical gap between Home/Discover/Library sections. */
val TvSectionSpacing = 22.dp

// Controls
val TvButtonHeight = 48.dp
val TvIconSize = 22.dp

// Typography
val TvTextDisplay = 34.sp
val TvTextHero = 31.sp
val TvTextHeadline = 24.sp
val TvTextTitle = 18.sp
val TvTextBodyLarge = 15.sp
val TvTextBody = 14.sp
val TvTextLabel = 13.sp
