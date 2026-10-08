package com.stremio.mobile.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf

/** Long-press actions use the current shelf item, so refreshed episode/progress data stays current. */
@Composable
fun ContinueWatchingShelf(
    shelf: CatalogShelf,
    onItemClick: (CatalogItem) -> Unit,
    onContinueWatching: (CatalogItem) -> Unit,
    onOpenDetails: (CatalogItem) -> Unit,
    onRemove: (CatalogItem) -> Unit,
    actionsEnabled: Boolean = true,
) {
    var selectedKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    val selectedItem = shelf.items.firstOrNull { selectedKey == (it.type to it.id) }

    LaunchedEffect(actionsEnabled, selectedItem) {
        if (!actionsEnabled || selectedItem == null) selectedKey = null
    }

    PosterShelf(
        shelf = shelf,
        mode = ShelfMode.Continue,
        onItemClick = onItemClick,
        onItemLongClick = if (actionsEnabled) {
            { item -> selectedKey = item.type to item.id }
        } else {
            null
        },
    )

    if (actionsEnabled && selectedItem != null) {
        // Dialogs have their own window; use static theme surfaces instead of the board backdrop.
        CompositionLocalProvider(LocalGlobalBackdrop provides null) {
            AlertDialog(
                onDismissRequest = { selectedKey = null },
                title = { Text(selectedItem.name, color = Color.White) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        Text("Opções de continuar assistindo", color = MutedText)
                        Text("Remover apaga o ponto de retomada e mantém o item na biblioteca.", color = MutedText)
                        ThemedButton(
                            text = "Continuar assistindo",
                            onClick = {
                                selectedKey = null
                                onContinueWatching(selectedItem)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ThemedButton(
                            text = "Ver detalhes",
                            onClick = {
                                selectedKey = null
                                onOpenDetails(selectedItem.copy(isContinueWatching = false))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0xFF3A374D),
                        )
                        ThemedButton(
                            text = "Remover de continuar assistindo",
                            onClick = {
                                selectedKey = null
                                onRemove(selectedItem)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0xFF51323D),
                        )
                    }
                },
                confirmButton = {
                    ThemedTextButton("Cancelar", onClick = { selectedKey = null })
                },
                containerColor = Color(0xFF2A2935),
            )
        }
    }
}
