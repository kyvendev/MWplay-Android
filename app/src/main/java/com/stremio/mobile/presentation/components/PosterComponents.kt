package com.stremio.mobile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.CardFallback
import com.stremio.mobile.core.theme.HairlineBorder
import com.stremio.mobile.core.theme.SurfaceHigh
import com.stremio.mobile.core.theme.SurfaceLow
import com.stremio.mobile.core.theme.TouchTarget
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf

enum class ShelfMode {
    Continue,
    Movie,
    Series,
}

@Composable
fun PosterShelf(
    shelf: CatalogShelf,
    mode: ShelfMode,
    onItemClick: (CatalogItem) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    onItemLongClick: ((CatalogItem) -> Unit)? = null,
) {
    val posterWidth = rememberShelfPosterWidth()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenGutter, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shelf.title,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onSeeAllClick != null && shelf.seeAllRequest != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = TouchTarget)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button, onClick = onSeeAllClick)
                        .padding(start = 12.dp, end = 8.dp),
                ) {
                    Text(
                        text = "Ver tudo",
                        color = SeeAllColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Ver tudo",
                        tint = SeeAllColor,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(18.dp),
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter),
            horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
        ) {
            when {
                shelf.isLoading -> {
                    items(5, contentType = { "skeleton" }) {
                        PosterSkeleton(posterWidth)
                    }
                }

                shelf.items.isEmpty() -> {
                    item(contentType = "empty") {
                        Text(
                            text = shelf.error ?: "Nenhum item disponível",
                            color = MutedText,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }

                else -> {
                    items(shelf.items, key = { "${it.type}-${it.id}" }, contentType = { "poster" }) { item ->
                        PosterTile(
                            item = item,
                            mode = mode,
                            onClick = { onItemClick(item) },
                            onLongClick = onItemLongClick?.let { { it(item) } },
                            modifier = Modifier.width(posterWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PosterTile(
    item: CatalogItem,
    mode: ShelfMode,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier.width(PhonePosterWidth),
) {
    Box(
        modifier = modifier
            .aspectRatio(0.66f)
            .clip(PosterShape)
            .background(Brush.verticalGradient(listOf(SurfaceHigh, CardFallback)))
            .border(1.dp, HairlineBorder, PosterShape)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClickLabel = "Ver detalhes",
                        onClick = onClick,
                        onLongClickLabel = "Opções de continuar assistindo",
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                },
            ),
    ) {
        // Shown until (or instead of) the artwork, so missing posters still identify the title.
        // Visual only: the poster image already announces the name to accessibility services.
        Text(
            text = item.name,
            modifier = Modifier.align(Alignment.Center).padding(10.dp).clearAndSetSemantics {},
            color = MutedText,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.poster)
                .size(224, 340)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1.0f to Color(0xAA000000),
                        ),
                    ),
                ),
        )

        when (mode) {
            ShelfMode.Continue -> {
                val progress = item.progress ?: progressFor(item.id)
                val isCompleted = item.watched

                if (isCompleted) {
                    CircleBadge(
                        imageVector = Icons.Outlined.Check,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    )
                }

                ProgressBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 7.dp, vertical = 7.dp),
                    progress = progress,
                )
            }

            ShelfMode.Movie -> {
                if (item.inCinema) {
                    CinemaBadge(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                    )
                }
            }

            ShelfMode.Series -> Unit
        }

        if (mode == ShelfMode.Continue && item.type == "series" && item.remainingEpisodes != null && item.remainingEpisodes > 0) {
            AddBadge(
                text = "+${item.remainingEpisodes}",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 10.dp),
            )
        }
    }
}

@Composable
private fun PosterSkeleton(width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(0.66f)
            .clip(PosterShape)
            .background(Brush.verticalGradient(colors = listOf(SurfaceHigh, SurfaceLow))),
    )
}

@Composable
private fun CircleBadge(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(AccentPurple),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun TextBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AccentPurple)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AddBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
    ) {
        // Bottom stacked card
        Box(
            modifier = Modifier
                .offset(x = (-2).dp, y = 2.dp)
                .height(17.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFC0B5FA)) // slightly darker/saturated lavender card for stack effect
                .padding(horizontal = 5.dp)
        ) {
            Text(
                text = text,
                color = Color.Transparent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Top foreground card
        Row(
            modifier = Modifier
                .height(17.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xDDEFEAFF))
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = AccentPurple,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CinemaBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC202631))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Movie,
            contentDescription = null,
            tint = Color(0xFFC9C8D8),
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = "NO CINEMA",
            color = Color(0xFFE8E6F0),
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x59FFFFFF)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AccentPurple),
        )
    }
}

private fun progressFor(id: String): Float {
    val bucket = kotlin.math.abs(id.hashCode() % 46)
    return (bucket + 28) / 100f
}


private val PosterShape = RoundedCornerShape(16.dp)
private val ShelfSpacing = 12.dp
private val PhonePosterWidth = 112.dp
private val SeeAllColor = Color(0xFFC9B8FF)

/** Shelf posters stay phone-sized on phones and grow a little on tablets / wide landscape. */
@Composable
fun rememberShelfPosterWidth(): Dp {
    val smallestWidth = LocalConfiguration.current.smallestScreenWidthDp
    return remember(smallestWidth) { if (smallestWidth >= 600) 140.dp else PhonePosterWidth }
}

/** Grid columns from the current width: 3 on a portrait phone, more in landscape and on tablets. */
@Composable
fun rememberPosterGridColumns(): Int {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return remember(screenWidth) {
        val available = screenWidth - ScreenGutter.value * 2
        val minCell = if (screenWidth >= 600) 130f else 100f
        ((available + ShelfSpacing.value) / (minCell + ShelfSpacing.value)).toInt().coerceIn(3, 8)
    }
}

/** One row of a catalog grid; posters fill their cell so the grid always spans the screen. */
@Composable
fun PosterGridRow(
    items: List<CatalogItem>,
    columns: Int,
    onItemClick: (CatalogItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter),
        horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
    ) {
        items.forEach { item ->
            Box(modifier = Modifier.weight(1f)) {
                PosterTile(
                    item = item,
                    mode = if (item.type == "series") ShelfMode.Series else ShelfMode.Movie,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        repeat(columns - items.size) {
            Box(modifier = Modifier.weight(1f))
        }
    }
}
