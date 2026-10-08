package com.stremio.mobile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.presentation.navigation.AppView

/**
 * Dedicated Android TV navigation rail.
 *
 * The rail can be explicitly collapsed. This avoids relying on OEM/Compose spatial focus
 * heuristics to escape the menu: after collapsing, the menu focus targets are removed from
 * composition and focus is handed to the content area. A small arrow remains at the left edge
 * so the menu can always be opened again with the remote.
 */
@Composable
fun StremioBottomBar(
    selectedView: AppView,
    backdrop: LayerBackdrop?,
    onSelect: (AppView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val menuFocusRequester = remember { FocusRequester() }
    val collapsedFocusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        // Wait for the new targets to be laid out, then transfer focus from a known source.
        // Removing the focused menu/opener first leaves spatial search without an origin.
        withFrameNanos { }
        if (expanded) {
            menuFocusRequester.requestFocus()
        } else {
            collapsedFocusRequester.requestFocus()
            focusManager.moveFocus(FocusDirection.Right)
        }
    }

    if (!expanded) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(54.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .width(46.dp)
                    .height(72.dp)
                    .focusRequester(collapsedFocusRequester)
                    .tvFocusTarget(cornerRadius = 16.dp, focusedScale = 1.06f)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(Color(0xE60B0C16))
                    .clickable { expanded = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Abrir menu",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(116.dp)
            .background(Color(0xF20B0C16))
            .padding(horizontal = 10.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StremioMark(modifier = Modifier.size(46.dp))
        Text(
            text = "PLAY",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 5.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .width(96.dp)
                .height(42.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                        expanded = false
                        true
                    } else {
                        false
                    }
                }
                .tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1.06f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable { expanded = false },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Fechar menu",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = "Fechar",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppView.entries.forEach { view ->
                TvNavigationItem(
                    view = view,
                    selected = view == selectedView,
                    onClick = { onSelect(view) },
                    onExitToContent = { expanded = false },
                    modifier = if (view == selectedView) Modifier.focusRequester(menuFocusRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun TvNavigationItem(
    view: AppView,
    selected: Boolean,
    onClick: () -> Unit,
    onExitToContent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .width(96.dp)
            .height(58.dp)
            .tvFocusTarget(cornerRadius = 18.dp, focusedScale = 1.06f)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    // RIGHT now deterministically collapses the rail instead of asking Compose
                    // spatial search to cross from the overlay into the content hierarchy.
                    onExitToContent()
                    true
                } else {
                    false
                }
            }
            .clip(shape)
            .background(if (selected) AccentPurple.copy(alpha = 0.34f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = view.icon,
            contentDescription = view.label,
            tint = if (selected) Color.White else Color(0xFFC8C3D5),
            modifier = Modifier.size(23.dp),
        )
        Text(
            text = view.label,
            color = if (selected) Color.White else Color(0xFFC8C3D5),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 2,
        )
    }
}

