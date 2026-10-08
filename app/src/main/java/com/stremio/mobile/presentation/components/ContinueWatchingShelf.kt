package com.stremio.mobile.presentation.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps short OK presses and held OK presses separate, including remotes without key repeats. */
@Composable
internal fun Modifier.tvPosterMenuInput(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    if (!rememberIsTelevision()) return this
    val scope = rememberCoroutineScope()
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val press = remember { PosterMenuKeyPress() }
    DisposableEffect(press) {
        onDispose { press.cancel() }
    }
    return this
        .onFocusChanged { if (!it.hasFocus) press.cancel() }
        .onPreviewKeyEvent { event ->
            val code = event.nativeKeyEvent.keyCode
            if (!isPosterSelectKey(code)) return@onPreviewKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (press.keyCode == null && event.nativeKeyEvent.repeatCount == 0) {
                        press.keyCode = code
                        press.job = scope.launch {
                            delay(timeout)
                            press.longPressed = true
                            currentLongClick()
                        }
                    }
                }
                KeyEventType.KeyUp -> {
                    val shouldClick = press.keyCode == code && !press.longPressed && !event.nativeKeyEvent.isCanceled
                    press.cancel()
                    if (shouldClick) currentClick()
                }
            }
            true
        }
}

private class PosterMenuKeyPress {
    var keyCode: Int? = null
    var longPressed = false
    var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
        keyCode = null
        longPressed = false
    }
}

private fun isPosterSelectKey(code: Int): Boolean = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
    code == AndroidKeyEvent.KEYCODE_ENTER || code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER

@Composable
fun ContinueWatchingShelf(
    shelf: CatalogShelf,
    onItemClick: (CatalogItem) -> Unit,
    onRemoveItem: (CatalogItem) -> Unit,
    onEmptyAfterRemoval: () -> Unit = {},
    onContinueWatching: (CatalogItem) -> Unit = onItemClick,
    actionsEnabled: Boolean = true,
) {
    var menuKey by remember { mutableStateOf<String?>(null) }
    val menuItem = shelf.items.firstOrNull { it.posterKey() == menuKey }
    var focusRequest by remember { mutableStateOf<PosterShelfFocusRequest?>(null) }
    var pendingRemoval by remember { mutableStateOf<Pair<String, String?>?>(null) }

    fun restorePoster(key: String) {
        focusRequest = PosterShelfFocusRequest(key, (focusRequest?.sequence ?: 0) + 1)
    }

    LaunchedEffect(actionsEnabled, menuKey, shelf.items) {
        if (!actionsEnabled || menuItem == null) menuKey = null
    }

    LaunchedEffect(shelf.items, pendingRemoval, actionsEnabled) {
        if (!actionsEnabled) return@LaunchedEffect
        val removal = pendingRemoval ?: return@LaunchedEffect
        if (shelf.items.any { it.posterKey() == removal.first }) return@LaunchedEffect
        pendingRemoval = null
        val neighbor = shelf.items.firstOrNull { it.posterKey() == removal.second } ?: shelf.items.firstOrNull()
        if (neighbor != null) restorePoster(neighbor.posterKey()) else onEmptyAfterRemoval()
    }

    PosterShelf(
        shelf = shelf,
        mode = ShelfMode.Continue,
        onItemClick = onItemClick,
        onItemLongClick = if (actionsEnabled) ({ menuKey = it.posterKey() }) else null,
        focusRestoreRequest = focusRequest,
    )

    menuItem?.takeIf { actionsEnabled }?.let { item ->
        ContinueWatchingMenu(
            item = item,
            onContinue = {
                menuKey = null
                restorePoster(item.posterKey())
                onContinueWatching(item)
            },
            onDetails = {
                menuKey = null
                restorePoster(item.posterKey())
                onItemClick(item.copy(isContinueWatching = false, continueWatchingVideoId = null))
            },
            onRemove = {
                val index = shelf.items.indexOfFirst { it.posterKey() == item.posterKey() }
                val neighbor = shelf.items.getOrNull(index + 1) ?: shelf.items.getOrNull(index - 1)
                pendingRemoval = item.posterKey() to neighbor?.posterKey()
                menuKey = null
                onRemoveItem(item)
            },
            onDismiss = {
                menuKey = null
                restorePoster(item.posterKey())
            },
        )
    }
}

@Composable
internal fun ContinueWatchingMenu(
    item: CatalogItem,
    onContinue: () -> Unit,
    onDetails: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    val isTv = rememberIsTelevision()
    // The key that opened this window is still held. Its repeats/release must not select an action.
    var freshSelectDown by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalGlobalBackdrop provides null) {
            ThemedCard(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .focusGroup()
                        .onPreviewKeyEvent { event ->
                            if (!isTv || !isPosterSelectKey(event.nativeKeyEvent.keyCode)) return@onPreviewKeyEvent false
                            when (event.type) {
                                KeyEventType.KeyDown -> {
                                    if (event.nativeKeyEvent.repeatCount > 0 && !freshSelectDown) true
                                    else {
                                        freshSelectDown = true
                                        false
                                    }
                                }
                                KeyEventType.KeyUp -> {
                                    val consume = !freshSelectDown
                                    freshSelectDown = false
                                    consume
                                }
                                else -> false
                            }
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(item.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("A remoção limpa o ponto de retomada e mantém o item na biblioteca.", color = Color(0xFFC9C8D8), fontSize = 14.sp)
                    ThemedButton("Continuar assistindo", onContinue, modifier = Modifier.fillMaxWidth().focusRequester(initialFocus))
                    ThemedButton("Ver detalhes", onDetails, modifier = Modifier.fillMaxWidth())
                    ThemedButton("Remover de continuar assistindo", onRemove, modifier = Modifier.fillMaxWidth())
                    ThemedButton("Cancelar", onDismiss, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        LaunchedEffect(isTv) {
            if (isTv) {
                withFrameNanos { }
                initialFocus.requestFocus()
            }
        }
    }
}
