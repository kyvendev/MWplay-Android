package com.stremio.mobile.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.stremio.mobile.core.theme.TvRailReservedWidth
import com.stremio.mobile.presentation.navigation.AppView

/** Keeps the TV rail at the screen edge and hands focus to the content group explicitly. */
@Composable
fun TvNavigationScaffold(
    selectedView: AppView,
    backdrop: LayerBackdrop?,
    onSelect: (AppView) -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    navigationEnabled: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content(
            Modifier
                // Opening the rail must not cover or reflow the focused content.
                .padding(start = TvRailReservedWidth)
                .focusRequester(contentFocusRequester)
                // LazyColumn already owns a focus group. Adding another one would save the
                // scroll container instead of the actual poster when focus leaves the content.
                .focusRestorer(),
        )
        StremioBottomBar(
            selectedView = selectedView,
            backdrop = backdrop,
            onSelect = onSelect,
            onExitToContent = { contentFocusRequester.requestFocus() },
            navigationEnabled = navigationEnabled,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}
