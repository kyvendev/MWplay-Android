package com.stremio.mobile.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.stremio.mobile.core.theme.CardFallback
import com.stremio.mobile.core.theme.HairlineBorder
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.core.theme.StremioBackground
import com.stremio.mobile.data.model.CatalogItem

@Composable
fun FeaturedHero(
    item: CatalogItem?,
    onClick: (CatalogItem) -> Unit,
) {
    FeaturedHeroCard(item = item, onClick = onClick)
}

/**
 * Hero height follows the screen width (about 16:10) on phones and tablets, and is capped by the
 * screen height in landscape so the shelves stay visible without scrolling.
 */
@Composable
private fun rememberHeroHeight(): Dp {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val byWidth = ((configuration.screenWidthDp - ScreenGutter.value * 2) * 0.62f).dp
        min(byWidth, (configuration.screenHeightDp * 0.55f).dp).coerceIn(210.dp, 420.dp)
    }
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
    val heroHeight = rememberHeroHeight()

    LaunchedEffect(key1 = items) {
        while (true) {
            kotlinx.coroutines.delay(6000)
            if (!pagerState.isScrollInProgress) {
                val nextPage = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            FeaturedHeroCard(
                item = items[page],
                onClick = onClick,
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ScreenGutter + 18.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                val width = animateDpAsState(targetValue = if (isSelected) 18.dp else 6.dp, label = "width")
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width.value)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.38f))
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
    if (item == null) {
        return
    }
    val heroHeight = rememberHeroHeight()
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .padding(start = ScreenGutter, end = ScreenGutter)
            .clip(shape)
            .background(CardFallback)
            .border(1.dp, HairlineBorder, shape)
            .clickable(role = Role.Button, onClickLabel = "Ver detalhes") { onClick(item) },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.background ?: item.poster)
                .size(1080, 620)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Two cheap gradient scrims keep the text legible on bright artwork.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(colorStops = arrayOf(0.0f to Color(0xE606070D), 0.55f to Color(0x8006070D), 1.0f to Color(0x1006070D)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(0.0f to Color.Transparent, 0.5f to Color.Transparent, 1.0f to Color(0xF206070D)))))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 18.dp, end = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassPill(text = "Em destaque")
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 30.sp,
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
                    text = meta.joinToString("  •  "),
                    color = Color(0xFFE2E3EE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Visual call to action; the whole card is the single touch target.
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = StremioBackground, modifier = Modifier.size(18.dp))
                Text("Ver detalhes", color = StremioBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
