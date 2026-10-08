package com.stremio.mobile.presentation.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.focus.focusProperties
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
 * Collapsing removes the menu targets and explicitly returns focus to the content owner.
 * The owner positions this rail at the left edge and restores its current content target;
 * spatial focus search cannot reliably find a poster from a vertically centered opener.
 */
@Composable
fun StremioBottomBar(
    selectedView: AppView,
    backdrop: LayerBackdrop?,
    onSelect: (AppView) -> Unit,
    modifier: Modifier = Modifier,
    onExitToContent: (() -> Unit)? = null,
    navigationEnabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(true) }
    var previouslyExpanded by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val menuFocusRequester = remember { FocusRequester() }
    val collapsedFocusRequester = remember { FocusRequester() }

    val exitToContent = {
        onExitToContent?.invoke() ?: run {
            focusManager.moveFocus(FocusDirection.Right)
            Unit
        }
    }

    LaunchedEffect(expanded, navigationEnabled) {
        val justCollapsed = previouslyExpanded && !expanded
        previouslyExpanded = expanded
        if (!navigationEnabled || (!expanded && !justCollapsed)) return@LaunchedEffect
        // A section selection can replace the catalog at the same time as the menu closes.
        // Let its focus targets attach before handing control back to the content owner.
        withFrameNanos { }
        withFrameNanos { }
        if (expanded) {
            menuFocusRequester.requestFocus()
        } else {
            // Keep the opener as a fallback when the catalog is still loading or empty.
            collapsedFocusRequester.requestFocus()
            exitToContent()
        }
    }

    BackHandler(enabled = expanded && navigationEnabled) {
        expanded = false
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
                    .focusProperties { canFocus = navigationEnabled }
                    .onPreviewKeyEvent { event ->
                        if (navigationEnabled && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                            exitToContent()
                            true
                        } else {
                            false
                        }
                    }
                    .tvFocusTarget(cornerRadius = 16.dp, focusedScale = 1.06f)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(Color(0xE60B0C16))
                    .clickable(enabled = navigationEnabled) { expanded = true },
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
                .focusProperties { canFocus = navigationEnabled }
                .onPreviewKeyEvent { event ->
                    if (navigationEnabled && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                        expanded = false
                        true
                    } else {
                        false
                    }
                }
                .tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1.06f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(enabled = navigationEnabled) { expanded = false },
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
                    enabled = navigationEnabled,
                    onClick = {
                        onSelect(view)
                        expanded = false
                    },
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
    enabled: Boolean,
    onClick: () -> Unit,
    onExitToContent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .width(96.dp)
            .height(58.dp)
            .focusProperties { canFocus = enabled }
            .tvFocusTarget(cornerRadius = 18.dp, focusedScale = 1.06f)
            .onPreviewKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
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
            .clickable(enabled = enabled, onClick = onClick)
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

