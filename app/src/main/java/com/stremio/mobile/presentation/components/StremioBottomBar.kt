package com.stremio.mobile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
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
 * Dedicated Android TV navigation. The TV build intentionally uses a left rail instead of
 * reusing the touch-first mobile bottom bar: every destination is a single D-pad focus target,
 * focus is visually explicit, and no pointer/drag gestures are required.
 */
@Composable
fun StremioBottomBar(
    selectedView: AppView,
    backdrop: LayerBackdrop?,
    onSelect: (AppView) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Do not inherit the mobile call site's BottomCenter parent-data modifier. In the TV branch
    // this component is deliberately anchored to the Box's default TopStart as a navigation rail.
    Column(
        modifier = Modifier
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

        Spacer(modifier = Modifier.height(34.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppView.entries.forEach { view ->
                TvNavigationItem(
                    view = view,
                    selected = view == selectedView,
                    onClick = { onSelect(view) },
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
) {
    val shape = RoundedCornerShape(18.dp)
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .width(96.dp)
            .height(58.dp)
            .tvFocusTarget(cornerRadius = 18.dp, focusedScale = 1.06f)
            .onPreviewKeyEvent { event ->
                // Material clickable items can retain D-pad focus inside the rail on some TV
                // devices. Explicitly hand RIGHT to Compose focus search so the remote can
                // enter the hero/poster/settings content instead of becoming trapped here.
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    focusManager.moveFocus(FocusDirection.Right)
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
