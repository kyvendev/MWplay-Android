package com.stremio.mobile.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.core.theme.CardFallback
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.core.theme.StremioBackground

@Composable
fun FeaturedHero(item: CatalogItem?, onClick: (CatalogItem) -> Unit) {
    FeaturedHeroCard(item = item, onClick = onClick)
}

/** Hero height scales with the TV's aspect ratio instead of a fixed phone-sized banner. */
@Composable
private fun rememberHeroHeight(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    return remember(screenHeight) { (screenHeight * 0.46f).dp.coerceIn(240.dp, 420.dp) }
}

@Composable
fun FeaturedHeroPager(
    items: List<CatalogItem>,
    onClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    if (items.size == 1) {
        FeaturedHeroCard(item = items.first(), onClick = onClick, modifier = modifier)
        return
    }
    val pagerState = rememberPagerState(pageCount = { items.size })
    var heroFocused by remember { mutableStateOf(false) }
    val heroHeight = rememberHeroHeight()
    LaunchedEffect(items) {
        while (true) {
            kotlinx.coroutines.delay(6000)
            if (!heroFocused && !pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .onFocusChanged { heroFocused = it.hasFocus }
            .focusGroup(),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            FeaturedHeroCard(item = items[page], onClick = onClick)
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ScreenGutter + 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { iteration ->
                val selected = pagerState.currentPage == iteration
                val width = animateDpAsState(if (selected) 22.dp else 7.dp, label = "width")
                Box(
                    Modifier
                        .height(7.dp)
                        .width(width.value)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.38f)),
                )
            }
        }
    }
}

@Composable
fun FeaturedHeroCard(
    item: CatalogItem?,
    onClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item == null) return
    val heroHeight = rememberHeroHeight()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .padding(start = ScreenGutter, end = ScreenGutter)
            .tvFocusTarget(cornerRadius = 24.dp, focusedScale = 1.015f)
            .clip(RoundedCornerShape(24.dp))
            .background(CardFallback)
            .clickable { onClick(item) },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(item.background ?: item.poster).size(1280, 720).memoryCachePolicy(CachePolicy.ENABLED).crossfade(true).build(),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Two cheap gradient scrims keep text legible at distance on bright artwork.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(colorStops = arrayOf(0.0f to Color(0xF206070D), 0.45f to Color(0x9906070D), 0.8f to Color(0x1406070D)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(0.0f to Color.Transparent, 0.6f to Color.Transparent, 1.0f to Color(0xE606070D)))))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.58f)
                .padding(start = 32.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassPill(text = "Em destaque")
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 36.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                item.releaseInfo,
                item.imdbRating?.let { "IMDb $it" },
                when (item.type) { "movie" -> "Filme"; "series" -> "Série"; else -> null },
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString("   •   "),
                    color = Color(0xFFE2E3EE),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Visual call to action only: the whole card is the single focus/click target.
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = StremioBackground, modifier = Modifier.size(20.dp))
                Text("Ver detalhes", color = StremioBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
