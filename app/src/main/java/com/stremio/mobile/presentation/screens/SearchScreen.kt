package com.stremio.mobile.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.*
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.presentation.components.PosterShelf
import com.stremio.mobile.presentation.components.PosterTile
import com.stremio.mobile.presentation.components.ShelfMode
import com.stremio.mobile.presentation.components.LocalGlobalUiTheme
import com.stremio.mobile.presentation.components.ThemedIconButton

/**
 * Full-screen search results, shown whenever the query is non-blank. Reuses the Discover-style
 * 3-column [PosterTile] grid and is powered by stremio-core's search across installed catalogs.
 */
@Composable
fun SearchResultsScreen(
    query: String,
    results: CatalogShelf,
    shelves: List<CatalogShelf>,
    history: List<String>,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onOpenDetails: (CatalogItem) -> Unit,
    onOpenDiscoverCatalog: (com.stremio.core.types.addon.ResourceRequest, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    var localQuery by remember {
        mutableStateOf(
            TextFieldValue(
                text = query,
                selection = TextRange(query.length)
            )
        )
    }
    LaunchedEffect(query) {
        if (query != localQuery.text) {
            localQuery = TextFieldValue(
                text = query,
                selection = TextRange(query.length)
            )
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val globalTheme = LocalGlobalUiTheme.current
    val fieldContainerColor = if (globalTheme.style == "modern") {
        Color.White.copy(alpha = (globalTheme.glassAlpha * 0.42f + 0.10f).coerceIn(0.10f, 0.36f))
    } else {
        SurfaceHigh
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StremioBackgroundBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                })
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenGutter, top = 10.dp, end = ScreenGutter, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemedIconButton(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
                modifier = Modifier
                    .size(TouchTarget),
                containerColor = SurfaceHigh,
            )
            OutlinedTextField(
                value = localQuery,
                onValueChange = { newValue ->
                    localQuery = newValue
                    onQueryChange(newValue.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = "Buscar filmes, séries, anime…",
                        color = MutedText,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MutedText,
                        modifier = Modifier.size(22.dp),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(999.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSubmitSearch()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = fieldContainerColor,
                    unfocusedContainerColor = fieldContainerColor,
                    focusedBorderColor = Color(0x33FFFFFF),
                    unfocusedBorderColor = Color(0x19FFFFFF),
                    cursorColor = AccentPurple,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
        }

        when {
            query.isBlank() -> {
                SearchHistoryList(
                    history = history,
                    bottomPadding = 24.dp + navBottom,
                    onSelect = { entry ->
                        localQuery = TextFieldValue(text = entry, selection = TextRange(entry.length))
                        onQueryChange(entry)
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                    onRemove = onRemoveHistory,
                )
            }

            results.isLoading && results.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(34.dp))
                }
            }

            results.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = results.error ?: "Nenhum resultado para \"$query\".",
                        color = MutedText,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        // Search covers the bottom bar, so only the system navigation inset is reserved.
                        bottom = 24.dp + navBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(27.dp),
                ) {
                    items(shelves.size) { index ->
                        val shelf = shelves[index]
                        PosterShelf(
                            shelf = shelf,
                            mode = if (shelf.type == "series") ShelfMode.Series else ShelfMode.Movie,
                            onItemClick = onOpenDetails,
                            onSeeAllClick = {
                                shelf.seeAllRequest?.let { req ->
                                    onOpenDiscoverCatalog(req, shelf.title)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
}

/** Recent searches (most recent first), each with an X to remove it. */
@Composable
private fun SearchHistoryList(
    history: List<String>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Digite o nome de um filme ou série.",
                color = MutedText,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = ScreenGutter),
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 4.dp, bottom = bottomPadding),
    ) {
        item {
            Text(
                text = "Buscas recentes",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(history.size, key = { history[it] }) { index ->
            val entry = history[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(entry) }
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = entry,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                )
                IconButton(onClick = { onRemove(entry) }, modifier = Modifier.size(TouchTarget)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remover \"$entry\" do histórico",
                        tint = MutedText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items_grid(
    items: List<CatalogItem>,
    onOpenDetails: (CatalogItem) -> Unit,
) {
    val rows = items.chunked(3)
    items(rows.size) { rowIndex ->
        val rowItems = rows[rowIndex]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            rowItems.forEach { item ->
                Box(modifier = Modifier.weight(1f)) {
                    PosterTile(
                        item = item,
                        mode = if (item.type == "series") ShelfMode.Series else ShelfMode.Movie,
                        onClick = { onOpenDetails(item) },
                    )
                }
            }
            repeat(3 - rowItems.size) {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}
