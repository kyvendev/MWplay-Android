package com.stremio.mobile.canais

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stremio.core.models.CatalogWithFilters
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.HairlineBorder
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.SubtleText
import com.stremio.mobile.core.theme.SurfaceHigh
import com.stremio.mobile.core.theme.SurfaceMid
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.data.model.ContentLabels
import com.stremio.mobile.data.model.SportsChannelPolicy
import com.stremio.mobile.data.model.TvCatalogPolicy
import com.stremio.mobile.data.model.TvCategories
import com.stremio.mobile.presentation.components.StremioMark

/** A category of the row: the source's categories, plus "Favoritos" kept on the device. */
internal sealed interface ChannelFilter {
    data class Source(val chip: TvCategories.Chip<ResourceRequest>) : ChannelFilter
    data object Favorites : ChannelFilter
}

/**
 * Channels to show for the filter, search text and favourites. Pure, so phone and TV behave the
 * same and it can be tested without a device.
 */
internal object ChannelList {
    fun visible(
        catalog: List<CatalogItem>,
        favorites: List<CatalogItem>,
        showFavorites: Boolean,
        sportsOpen: Boolean,
        listedUnderSports: Boolean,
        query: String,
    ): List<CatalogItem> {
        val base = when {
            showFavorites -> favorites
            sportsOpen -> SportsChannelPolicy.sportsChannels(catalog, { it.id }, { it.genres }, listedUnderSports)
            else -> catalog.distinctBy { it.type to it.id }
        }
        val words = SportsChannelPolicy.normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return base
        return base.filter { item -> SportsChannelPolicy.normalize(item.name).let { name -> words.all(name::contains) } }
    }
}

/**
 * MW Play Canais home: live channels only. Category row ("Todos os canais", "Favoritos",
 * "Esportes" and the addon's own), channel search, and a grid of channels; a tap / OK plays at
 * once, a long press / long OK adds to the favourites. Works with touch and with the TV remote.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelsScreen(
    filters: CatalogWithFilters?,
    catalog: CatalogShelf,
    favorites: List<CatalogItem>,
    onLoadChannels: () -> Unit,
    onSelectRequest: (ResourceRequest) -> Unit,
    onPlay: (CatalogItem, List<CatalogItem>) -> Unit,
    onToggleFavorite: (CatalogItem) -> Boolean,
    onOpenSettings: () -> Unit,
    onCloseApp: (() -> Unit)? = null,
    /** MW Play Canais lists only the channels of these addons (Best Cine), by addon URL. */
    isChannelSource: (String) -> Boolean = { true },
    /** The installed addons are still loading, so [isChannelSource] may not know Best Cine yet. */
    channelSourcesLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isTv = isTvDevice()
    val context = LocalContext.current
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var sportsByMetadata by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { onLoadChannels() }
    BackHandler(enabled = searchOpen) {
        searchOpen = false
        query = ""
    }

    val tvFilters = filters?.takeIf { TvCatalogPolicy.isTv(it.selected?.request?.path?.type) }
    // Only Best Cine's channel catalogs ("Canais", "Canais | Esportes"…); other addons' are left out.
    val catalogs = tvFilters?.selectable?.catalogs.orEmpty().filter { isChannelSource(it.request.base) }
    val row = tvFilters?.let { channelCategoryRow(it, catalogs, sportsByMetadata) }
    val selectedIsSource = tvFilters?.selected?.request?.base?.let(isChannelSource) == true
    // No addon offers TV catalogs at all, or none of them is Best Cine.
    val noTvSource = filters != null && filters.selectable.types.isNotEmpty() && filters.selectable.types.none { TvCatalogPolicy.isTv(it.type) }
    val noChannelSource = tvFilters != null && catalogs.isEmpty() && !channelSourcesLoading
    // Another addon's TV catalog is open: switch to Best Cine's.
    LaunchedEffect(tvFilters?.selected?.request, catalogs) {
        if (tvFilters != null && !selectedIsSource) {
            (catalogs.firstOrNull { !SportsChannelPolicy.isSportsCatalog(it.name) } ?: catalogs.firstOrNull())
                ?.let { onSelectRequest(it.request) }
        }
    }
    // While the TV catalog is being selected, the model may still hold another one.
    val loading = catalog.isLoading || (tvFilters == null && !noTvSource) || (tvFilters != null && !selectedIsSource && !noChannelSource)
    val items = ChannelList.visible(
        catalog = if (tvFilters != null && selectedIsSource) catalog.items else emptyList(),
        favorites = favorites,
        showFavorites = showFavorites,
        sportsOpen = row?.sportsOpen == true,
        listedUnderSports = row?.listedUnderSports == true,
        query = query,
    )
    val columns = if (isTv) GridCells.Adaptive(200.dp) else GridCells.Adaptive(112.dp)
    val gutter = if (isTv) 40.dp else 16.dp
    val firstCardFocus = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 14.dp),
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).testTag("channels-grid"),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChannelsHeader(
                isTv = isTv,
                searchOpen = searchOpen,
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                onOpenSettings = onOpenSettings,
                onCloseApp = onCloseApp,
            )
        }
        if (searchOpen) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Buscar canal", color = SubtleText) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = MutedText) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = HairlineBorder,
                        cursorColor = AccentPurple,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("channel-search"),
                )
            }
        }
        if (row != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val filtersShown = buildList {
                    row.chips.firstOrNull { it.kind == TvCategories.Kind.ALL }?.let { add(ChannelFilter.Source(it)) }
                    add(ChannelFilter.Favorites)
                    row.chips.filter { it.kind != TvCategories.Kind.ALL }.forEach { add(ChannelFilter.Source(it)) }
                }
                ChipRow(
                    labels = filtersShown.map { if (it is ChannelFilter.Source) it.chip.label else "Favoritos" },
                    selected = filtersShown.indexOfFirst { filter ->
                        when (filter) {
                            ChannelFilter.Favorites -> showFavorites
                            is ChannelFilter.Source -> !showFavorites && filter.chip.selected
                        }
                    },
                    onSelect = { index ->
                        when (val filter = filtersShown[index]) {
                            ChannelFilter.Favorites -> showFavorites = true
                            is ChannelFilter.Source -> {
                                showFavorites = false
                                sportsByMetadata = filter.chip.kind == TvCategories.Kind.SPORTS && row.sportsFromMetadata
                                onSelectRequest(filter.chip.request)
                            }
                        }
                    },
                )
            }
            if (!showFavorites && row.sportsOpen && row.sportsSubcategories.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChipRow(
                        labels = row.sportsSubcategories.map { it.label },
                        selected = row.sportsSubcategories.indexOfFirst { it.selected },
                        onSelect = { onSelectRequest(row.sportsSubcategories[it].request) },
                    )
                }
            }
        }
        // More than one channel source (e.g. two addons): pick which one.
        if (!showFavorites && catalogs.count { !SportsChannelPolicy.isSportsCatalog(it.name) } > 1) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val general = catalogs.filterNot { SportsChannelPolicy.isSportsCatalog(it.name) }
                ChipRow(
                    labels = general.map { ContentLabels.catalog(it.name) },
                    selected = general.indexOfFirst { it.selected },
                    onSelect = { onSelectRequest(general[it].request) },
                    small = true,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = if (isTv) "OK para assistir  ·  segure OK para favoritar" else "Toque para assistir  ·  segure para favoritar",
                color = SubtleText,
                fontSize = if (isTv) 14.sp else 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        when {
            !showFavorites && (noTvSource || noChannelSource) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChannelsMessage("Os canais vêm do addon BestCine. Instale o BestCine na sua conta para ver os canais.")
                }
            !showFavorites && loading ->
                items(12) { ChannelCardPlaceholder(isTv) }
            !showFavorites && catalog.error != null ->
                item(span = { GridItemSpan(maxLineSpan) }) { ChannelsMessage("Não foi possível carregar os canais. ${catalog.error}", onRetry = onLoadChannels) }
            items.isEmpty() ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChannelsMessage(
                        when {
                            query.isNotBlank() -> "Nenhum canal encontrado para \"$query\"."
                            showFavorites -> "Você ainda não tem favoritos. ${if (isTv) "Segure OK" else "Segure"} em um canal para adicioná-lo aqui."
                            row?.sportsOpen == true -> "Nenhum canal de esportes encontrado neste catálogo."
                            else -> "Nenhum canal neste catálogo."
                        },
                    )
                }
            else -> items(items, key = { "${it.type}|${it.id}" }) { item ->
                val isFavorite = favorites.any { it.sameChannel(item) }
                ChannelCard(
                    item = item,
                    isFavorite = isFavorite,
                    isTv = isTv,
                    onPlay = { onPlay(item, items) },
                    onToggleFavorite = {
                        val added = onToggleFavorite(item)
                        Toast.makeText(context, if (added) "${item.name} nos favoritos" else "${item.name} saiu dos favoritos", Toast.LENGTH_SHORT).show()
                    },
                    modifier = if (item == items.first()) Modifier.focusRequester(firstCardFocus) else Modifier,
                )
            }
        }
    }

    // TV: start on the first channel so the remote works right away.
    LaunchedEffect(isTv, items.isNotEmpty()) {
        if (isTv && items.isNotEmpty() && !focusedOnce) {
            // The grid composes its cards over the next frames: wait for the first one.
            repeat(3) { withFrameNanos { } }
            focusedOnce = runCatching { firstCardFocus.requestFocus() }.isSuccess
        }
    }
}

private fun channelCategoryRow(
    filters: CatalogWithFilters,
    catalogs: List<com.stremio.core.models.CatalogWithFilters.SelectableCatalog>,
    sportsByMetadata: Boolean,
): TvCategories.Row<ResourceRequest> {
    val genre = filters.selectable.extra.firstOrNull { it.name.equals("genre", ignoreCase = true) }
    return TvCategories.build(
        genres = genre?.options.orEmpty().map { TvCategories.Option(it.value, it.selected, it.request) },
        sportsByMetadata = sportsByMetadata,
        label = ContentLabels::genre,
        allRequest = catalogs.firstOrNull { it.selected }?.request,
        catalogs = catalogs.map { TvCategories.Option(it.name, it.selected, it.request) },
    )
}

@Composable
private fun ChannelsHeader(
    isTv: Boolean,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseApp: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = if (isTv) 24.dp else 12.dp, bottom = 4.dp),
    ) {
        StremioMark(modifier = Modifier.size(if (isTv) 44.dp else 36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Canais", color = Color.White, fontSize = if (isTv) 30.sp else 26.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRed))
                Text("Ao vivo", color = MutedText, fontSize = if (isTv) 15.sp else 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        HeaderButton(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, if (searchOpen) "Fechar busca" else "Buscar canal", isTv, onToggleSearch)
        HeaderButton(Icons.Outlined.Settings, "Ajustes", isTv, onOpenSettings)
        onCloseApp?.let { HeaderButton(Icons.Outlined.PowerSettingsNew, "Fechar app", isTv, it) }
    }
}

@Composable
private fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, isTv: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(if (isTv) 52.dp else 48.dp)
            .channelFocus(cornerRadius = 999.dp, focusedScale = 1.08f)
            .clip(CircleShape)
            .background(SurfaceHigh)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, small: Boolean = false) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(labels.indices.toList(), key = { labels[it] + it }) { index ->
            val isSelected = index == selected
            val shape = RoundedCornerShape(999.dp)
            Text(
                text = labels[index],
                color = Color.White,
                fontSize = if (small) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .heightIn(min = if (small) 38.dp else 44.dp)
                    .channelFocus(cornerRadius = 999.dp, focusedScale = 1.04f)
                    .clip(shape)
                    .background(if (isSelected) AccentPurple else SurfaceHigh)
                    .border(1.dp, if (isSelected) AccentPurple else HairlineBorder, shape)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 18.dp, vertical = if (small) 9.dp else 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    item: CatalogItem,
    isFavorite: Boolean,
    isTv: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TV: holding OK (key repeat) toggles the favourite; the release after it does not play.
    var longPressed by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                val select = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!select) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.repeatCount == 1) {
                            longPressed = true
                            onToggleFavorite()
                        }
                        event.nativeKeyEvent.repeatCount > 0
                    }
                    KeyEventType.KeyUp -> if (longPressed) {
                        longPressed = false
                        true
                    } else false
                    else -> false
                }
            }
            .channelFocus(cornerRadius = 18.dp, focusedScale = 1.05f)
            .clip(RoundedCornerShape(18.dp))
            .then(
                // On TV the long OK is handled above; a combined click would toggle a second time.
                if (isTv) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Assistir ${item.name}", onClick = onPlay)
                } else {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClickLabel = "Assistir ${item.name}",
                        onLongClickLabel = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                        onLongClick = onToggleFavorite,
                        onClick = onPlay,
                    )
                },
            )
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceMid)
                .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp)),
        ) {
            val logo = item.poster ?: item.background
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (isTv) 18.dp else 12.dp),
                )
            } else {
                Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = MutedText, modifier = Modifier.size(36.dp))
            }
            if (isFavorite) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = "Favorito",
                    tint = Favorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
                )
            }
        }
        Text(
            text = item.name,
            color = Color.White,
            fontSize = if (isTv) 16.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp).heightIn(min = if (isTv) 40.dp else 34.dp),
        )
    }
}

@Composable
private fun ChannelCardPlaceholder(isTv: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(14.dp)).background(SurfaceMid))
        Box(Modifier.width(if (isTv) 120.dp else 72.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceHigh))
    }
}

@Composable
private fun ChannelsMessage(text: String, onRetry: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 28.dp)) {
        Text(text, color = MutedText, fontSize = 15.sp, lineHeight = 21.sp)
        onRetry?.let {
            Text(
                "Tentar novamente",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .channelFocus(cornerRadius = 999.dp, focusedScale = 1.04f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AccentPurple)
                    .clickable(onClick = it)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

private val LiveRed = Color(0xFFFF4D4F)
private val Favorite = Color(0xFFFFC53D)
