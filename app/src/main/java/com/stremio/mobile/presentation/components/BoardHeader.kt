package com.stremio.mobile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.HairlineBorder
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.core.theme.SurfaceHigh

@Composable
fun BoardHeader(
    onOpenSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenGutter, top = 20.dp, end = ScreenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StremioMark(modifier = Modifier.size(38.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "MW PLAY",
            color = Color.White,
            fontSize = 17.sp,
            letterSpacing = 2.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .height(46.dp)
                .widthIn(min = 240.dp)
                .tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.04f)
                .clip(RoundedCornerShape(999.dp))
                .background(SurfaceHigh)
                .border(1.dp, HairlineBorder, RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenSearch)
                .padding(start = 16.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Pesquisar",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Buscar filmes e séries",
                color = MutedText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
