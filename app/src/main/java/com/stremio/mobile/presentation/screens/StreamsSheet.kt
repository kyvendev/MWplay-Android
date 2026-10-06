package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.GlassSurface
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.StremioBackgroundBrush
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSortCriterion
import com.stremio.mobile.data.model.parseSeedCount
import com.stremio.mobile.data.model.parseSizeBytes
import com.stremio.mobile.data.model.qualityScore
import com.stremio.mobile.presentation.components.ThemedCard
import com.stremio.mobile.presentation.components.ThemedChip
import com.stremio.mobile.presentation.components.ThemedIconButton
import com.stremio.mobile.presentation.components.tvFocusTarget
import com.stremio.mobile.presentation.state.StreamsUiState
import kotlinx.coroutines.delay

@Composable
fun StreamsSheet(
    state: StreamsUiState,
    preferredQuality: String,
    onBack: () -> Unit,
    onSelect: (StreamOption) -> Unit,
    onSelectEpisode: (EpisodeOption) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectProvider: (String?) -> Unit,
    onSelectSortCriterion: (StreamSortCriterion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstContentFocus = remember { FocusRequester() }

    Column(
        modifier = modifier.fillMaxSize().background(StremioBackgroundBrush)
            .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemedIconButton(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
                modifier = Modifier.size(44.dp).tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.12f),
                containerColor = GlassSurface,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(if (state.showingEpisodes) "Episódios" else "Fontes", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                state.forItem?.let {
                    Text(buildString { append(it.name); state.selectedEpisodeLabel?.let { label -> append(" · $label") } }, color = MutedText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                state.releaseDateLabel?.takeIf { it.isNotBlank() }?.let {
                    Text("Lançado em $it", color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.isResolving) CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(24.dp))
        }

        Spacer(Modifier.height(4.dp))

        when {
            state.error != null && state.streams.isEmpty() && state.episodes.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(state.error, color = Color(0xFFFFC66D), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            state.isLoading && state.streams.isEmpty() && state.episodes.isEmpty() -> LoadingStreams(if (state.isSeries) "Carregando episódios…" else "Buscando fontes…")
            state.showingEpisodes -> {
                val filteredEpisodes = state.episodes.filter { state.selectedSeason == null || it.season == state.selectedSeason }
                val targetIndex = remember(filteredEpisodes) {
                    filteredEpisodes.indexOfFirst { it.isCurrent }.takeIf { it >= 0 }
                        ?: filteredEpisodes.indexOfLast { it.watched }.takeIf { it >= 0 }
                        ?: 0
                }
                val listState = rememberLazyListState()

                if (state.seasons.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(state.seasons) { season ->
                            val selected = season == state.selectedSeason
                            Box(Modifier.tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.08f)) {
                                ThemedChip(selected = selected, onClick = { onSelectSeason(season) }) {
                                    Text("Temporada $season", color = if (selected) Color.White else MutedText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(state.selectedSeason, filteredEpisodes, targetIndex) {
                    if (filteredEpisodes.isNotEmpty()) {
                        listState.scrollToItem(targetIndex.coerceIn(filteredEpisodes.indices))
                        delay(100)
                        runCatching { firstContentFocus.requestFocus() }
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(filteredEpisodes, key = { it.videoId }) { episode ->
                        val index = filteredEpisodes.indexOf(episode)
                        EpisodeRow(
                            episode = episode,
                            onClick = { onSelectEpisode(episode) },
                            modifier = if (index == targetIndex) Modifier.focusRequester(firstContentFocus) else Modifier,
                        )
                    }
                }
            }
            else -> {
                if (state.isLoading) {
                    LoadingStreams("Buscando fontes…")
                } else {
                    val providers = remember(state.streams) { state.streams.map { it.addonTitle }.distinct() }
                    val visibleStreams = remember(state.streams, state.selectedProvider, state.sortCriterion, preferredQuality) {
                        state.streams.filter { state.selectedProvider == null || it.addonTitle == state.selectedProvider }.let { filtered ->
                            when (state.sortCriterion) {
                                StreamSortCriterion.DEFAULT -> filtered
                                StreamSortCriterion.SEEDS -> filtered.sortedByDescending { parseSeedCount(it.seeds) }
                                StreamSortCriterion.SIZE -> filtered.sortedByDescending { parseSizeBytes(it.size) }
                                StreamSortCriterion.QUALITY -> filtered.sortedByDescending { qualityScore(it.quality, preferredQuality) }
                            }
                        }
                    }

                    if (providers.size > 1 || state.sortCriterion != StreamSortCriterion.DEFAULT) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (providers.size > 1) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item { FilterChip("Todos", state.selectedProvider == null) { onSelectProvider(null) } }
                                    items(providers) { provider -> FilterChip(provider, state.selectedProvider == provider) { onSelectProvider(provider) } }
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(StreamSortCriterion.entries.toList()) { criterion ->
                                    FilterChip("Ordenar: ${criterion.label}", state.sortCriterion == criterion) { onSelectSortCriterion(criterion) }
                                }
                            }
                        }
                    }

                    LaunchedEffect(visibleStreams, state.selectedEpisodeLabel) {
                        if (visibleStreams.isNotEmpty()) {
                            delay(100)
                            runCatching { firstContentFocus.requestFocus() }
                        }
                    }

                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(visibleStreams, key = { it.key }) { option ->
                            StreamRow(
                                option = option,
                                enabled = !state.isResolving,
                                onSelect = { onSelect(option) },
                                modifier = if (option == visibleStreams.firstOrNull()) Modifier.focusRequester(firstContentFocus) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingStreams(message: String) {
    Column(Modifier.fillMaxWidth().height(220.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(36.dp)); Spacer(Modifier.height(16.dp))
        Text(message, color = MutedText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ThemedCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
        Row(
            modifier = modifier.fillMaxWidth().background(if (episode.isCurrent) Color(0x332A2042) else Color.Transparent)
                .tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1.025f).clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(86.dp).height(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF202033))) {
                if (!episode.thumbnail.isNullOrBlank()) AsyncImage(episode.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("E${episode.episode}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Box(Modifier.align(Alignment.BottomStart).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(if (episode.isCurrent) AccentPurple else Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("E${episode.episode}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("E${episode.episode}. ${episode.title}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                episode.releaseDate?.takeIf { it.isNotBlank() }?.let { Text(it, color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (episode.watched) Box(Modifier.size(8.dp).clip(CircleShape).background(AccentPurple))
        }
    }
}

@Composable
private fun StreamRow(option: StreamOption, enabled: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    ThemedCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Row(modifier.fillMaxWidth().tvFocusTarget(cornerRadius = 16.dp, focusedScale = 1.025f).clickable(enabled = enabled, onClick = onSelect).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(AccentPurple), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    option.quality?.let { Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF3B3B4F)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(it.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
                    if (option.addonTitle.isNotBlank()) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(AccentPurple.copy(alpha = .12f)).border(1.dp, AccentPurple.copy(alpha = .24f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(option.addonTitle, color = AccentPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    Text(option.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                val cleanDesc = option.cleanDescription ?: option.description ?: option.addonTitle
                if (cleanDesc.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(cleanDesc, color = MutedText, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.08f)) {
        ThemedChip(selected = selected, onClick = onClick) { Text(label, color = if (selected) Color.White else MutedText, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
    }
}
