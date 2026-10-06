package com.stremio.mobile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.CardFallback
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf

enum class ShelfMode { Continue, Movie, Series }

private val TvGutter = 30.dp
private val TvPosterWidth = 142.dp
private val TvPosterRadius = 16.dp

@Composable
fun PosterShelf(
    shelf: CatalogShelf,
    mode: ShelfMode,
    onItemClick: (CatalogItem) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = TvGutter, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shelf.title,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onSeeAllClick != null && shelf.seeAllRequest != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .tvFocusTarget(cornerRadius = 10.dp, focusedScale = 1.06f)
                        .clickable(onClick = onSeeAllClick)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("MOSTRAR TUDO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Icon(Icons.Outlined.ChevronRight, "Mostrar tudo", tint = Color.White, modifier = Modifier.padding(start = 5.dp).size(20.dp))
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(start = TvGutter, end = TvGutter),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                shelf.isLoading -> items(6, contentType = { "skeleton" }) { PosterSkeleton() }
                shelf.items.isEmpty() -> item(contentType = "empty") {
                    Text(shelf.error ?: "Nenhum item disponível", color = MutedText, modifier = Modifier.padding(start = 18.dp))
                }
                else -> items(shelf.items, key = { "${it.type}-${it.id}" }, contentType = { "poster" }) { item ->
                    PosterTile(item = item, mode = mode, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
fun PosterTile(item: CatalogItem, mode: ShelfMode, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(TvPosterWidth)
            .aspectRatio(0.66f)
            .tvFocusTarget(cornerRadius = TvPosterRadius, focusedScale = 1.08f)
            .clip(RoundedCornerShape(TvPosterRadius))
            .background(CardFallback)
            .clickable(onClick = onClick),
    ) {
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
                ProgressBar(Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp, vertical = 5.dp), progress)
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
    Box(Modifier.width(TvPosterWidth).aspectRatio(0.66f).clip(RoundedCornerShape(TvPosterRadius)).background(Brush.verticalGradient(listOf(Color(0xFF222231), Color(0xFF11111C)))))
}
@Composable private fun CircleBadge(imageVector: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.size(19.dp).clip(CircleShape).background(AccentPurple), contentAlignment = Alignment.Center) { Icon(imageVector, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
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
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE8E8EE))) { Box(Modifier.fillMaxWidth(progress).height(4.dp).background(AccentPurple)) }
}
private fun progressFor(id: String): Float { val bucket = kotlin.math.abs(id.hashCode() % 46); return (bucket + 28) / 100f }
