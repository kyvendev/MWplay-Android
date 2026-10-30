package com.stremio.mobile.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.presentation.components.rememberIsTelevision
import com.stremio.mobile.presentation.components.tvFocusTarget

/**
 * Dark pill with a purple skip icon. On TV it is a D-pad focus target with the TV focus ring, and
 * [showOkHint] adds an "OK" badge while the controls are hidden (OK then skips directly).
 */
@Composable
fun SkipIntroButton(onClick: () -> Unit, modifier: Modifier = Modifier, showOkHint: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1.06f)
            .clip(shape)
            .background(Color(0xE6141422))
            .border(1.dp, Color(0x40FFFFFF), shape)
            .clickable(role = Role.Button, onClickLabel = "Pular abertura", onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.SkipNext, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(22.dp))
        Text("Pular abertura", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (showOkHint && rememberIsTelevision()) {
            Text(
                "OK",
                color = Color(0xFF141422),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
