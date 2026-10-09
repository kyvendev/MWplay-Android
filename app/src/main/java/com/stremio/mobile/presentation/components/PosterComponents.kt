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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.CardFallback
import com.stremio.mobile.core.theme.HairlineBorder
import com.stremio.mobile.core.theme.SurfaceHigh
import com.stremio.mobile.core.theme.SurfaceLow
import com.stremio.mobile.core.theme.SurfaceMid
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf

enum class ShelfMode { Continue, Movie, Series }

private val TvGutter = 30.dp
private val TvShelfSpacing = 18.dp
/** Matches the content start padding applied by TvNavigationScaffold. */
internal val TvRailReservedWidth = 122.dp
private val TvPosterWidth = 142.dp
private val TvPosterRadius = 16.dp

data class PosterShelfFocusRequest(val itemKey: String, val sequence: Int)

internal fun CatalogItem.posterKey(): String = "$type-$id"

@Composable
fun PosterShelf(
    shelf: CatalogShelf,
    mode: ShelfMode,
    onItemClick: (CatalogItem) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    onItemLongClick: ((CatalogItem) -> Unit)? = null,
    focusRestoreRequest: PosterShelfFocusRequest? = null,
) {
    val listState = rememberLazyListState()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    LaunchedEffect(focusRestoreRequest, onItemLongClick != null) {
        if (onItemLongClick == null) return@LaunchedEffect
        val target = focusRestoreRequest ?: return@LaunchedEffect
        val index = shelf.items.indexOfFirst { it.posterKey() == target.itemKey }
        if (index < 0) return@LaunchedEffect
        listState.scrollToItem(index)
        // The requested poster may need to be composed after scrolling and closing the dialog.
        withFrameNanos { }
        withFrameNanos { }
        runCatching { focusRequesters[target.itemKey]?.requestFocus() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = TvGutter, end = TvGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shelf.title,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 21.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onSeeAllClick != null && shelf.seeAllRequest != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.05f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(SurfaceMid)
                        .clickable(onClick = onSeeAllClick)
                        .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text("Ver tudo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ChevronRight, "Mostrar tudo", tint = Color.White, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                }
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(start = TvGutter, end = TvGutter),
            horizontalArrangement = Arrangement.spacedBy(TvShelfSpacing),
        ) {
            when {
                shelf.isLoading -> items(6, contentType = { "skeleton" }) { PosterSkeleton() }
                shelf.items.isEmpty() -> item(contentType = "empty") {
                    Text(shelf.error ?: "Nenhum item disponível", color = MutedText, modifier = Modifier.padding(start = 18.dp))
                }
                else -> items(shelf.items, key = { it.posterKey() }, contentType = { "poster" }) { item ->
                    PosterTile(
                        item = item,
                        mode = mode,
                        onClick = { onItemClick(item) },
                        onLongClick = onItemLongClick?.let { callback -> { callback(item) } },
                        modifier = if (onItemLongClick != null) {
                            Modifier.focusRequester(focusRequesters.getOrPut(item.posterKey()) { FocusRequester() })
                        } else Modifier,
                    )
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
    modifier: Modifier = Modifier,
) {
    val interactionModifier = if (onLongClick != null) {
        Modifier
            .tvPosterMenuInput(onClick, onLongClick)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Opções de continuar assistindo",
            )
    } else Modifier.clickable(onClick = onClick)
    Box(
        modifier = modifier
            .width(TvPosterWidth)
            .aspectRatio(0.66f)
            .tvFocusTarget(cornerRadius = TvPosterRadius, focusedScale = 1.08f)
            .clip(RoundedCornerShape(TvPosterRadius))
            .background(Brush.verticalGradient(listOf(SurfaceHigh, CardFallback)))
            .border(1.dp, HairlineBorder, RoundedCornerShape(TvPosterRadius))
            .then(interactionModifier),
    ) {
        // Shown until (or instead of) the artwork, so missing posters still identify the title.
        Text(
            text = item.name,
            modifier = Modifier.align(Alignment.Center).padding(12.dp),
            color = MutedText,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.poster)
                .size(256, 388)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(0f to Color.Transparent, .70f to Color.Transparent, 1f to Color(0xB8000000)))))
        when (mode) {
            ShelfMode.Continue -> {
                val progress = item.progress ?: progressFor(item.id)
                if (item.watched) CircleBadge(Icons.Outlined.Check, Modifier.align(Alignment.TopStart).padding(9.dp))
                ProgressBar(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 8.dp), progress)
            }
            ShelfMode.Movie -> if (item.inCinema) CinemaBadge(Modifier.align(Alignment.TopCenter).padding(top = 9.dp))
            ShelfMode.Series -> Unit
        }
        if (mode == ShelfMode.Continue && item.type == "series" && (item.remainingEpisodes ?: 0) > 0) {
            AddBadge("+${item.remainingEpisodes}", Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 10.dp))
        }
    }
}

@Composable private fun PosterSkeleton() {
    Box(Modifier.width(TvPosterWidth).aspectRatio(0.66f).clip(RoundedCornerShape(TvPosterRadius)).background(Brush.verticalGradient(listOf(SurfaceHigh, SurfaceLow))))
}
@Composable private fun CircleBadge(imageVector: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.size(22.dp).clip(CircleShape).background(AccentPurple), contentAlignment = Alignment.Center) { Icon(imageVector, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
}
@Composable private fun AddBadge(text: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(Modifier.offset(x = (-2).dp, y = 2.dp).height(19.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFC0B5FA)).padding(horizontal = 6.dp)) { Text(text, color = Color.Transparent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Row(Modifier.height(19.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xDDEFEAFF)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    }
}
@Composable private fun CinemaBadge(modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xCC202631)).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.Movie, null, tint = Color(0xFFC9C8D8), modifier = Modifier.size(11.dp)); Text("NO CINEMA", color = Color(0xFFE8E6F0), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}
@Composable private fun ProgressBar(modifier: Modifier = Modifier, progress: Float) {
    Box(modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x66FFFFFF))) { Box(Modifier.fillMaxWidth(progress).height(5.dp).clip(RoundedCornerShape(8.dp)).background(AccentPurple)) }
}
private fun progressFor(id: String): Float { val bucket = kotlin.math.abs(id.hashCode() % 46); return (bucket + 28) / 100f }


/** Columns that keep grid posters at shelf size on any TV resolution (rail + gutters excluded). */
@Composable
fun rememberPosterGridColumns(): Int {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return remember(screenWidth) {
        val available = screenWidth - TvRailReservedWidth.value - TvGutter.value * 2
        ((available + TvShelfSpacing.value) / (TvPosterWidth.value + TvShelfSpacing.value)).toInt().coerceIn(3, 10)
    }
}

/** One row of a catalog grid, aligned with the shelves' gutter and spacing. */
@Composable
fun PosterGridRow(
    items: List<CatalogItem>,
    columns: Int,
    onItemClick: (CatalogItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvGutter),
        horizontalArrangement = Arrangement.spacedBy(TvShelfSpacing),
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
