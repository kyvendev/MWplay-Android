package com.stremio.mobile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stremio.mobile.core.theme.HairlineBorder
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.core.theme.SurfaceHigh
import com.stremio.mobile.core.theme.TouchTarget

@Composable
fun BoardHeader(
    onOpenSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenGutter, top = 8.dp, end = ScreenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StremioMark(modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(TouchTarget)
                .clip(CircleShape)
                .background(SurfaceHigh)
                .border(1.dp, HairlineBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onOpenSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Pesquisar",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
