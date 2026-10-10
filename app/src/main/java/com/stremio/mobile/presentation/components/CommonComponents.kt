package com.stremio.mobile.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.ScreenGutter

@Composable
fun StremioMark(modifier: Modifier = Modifier) {
    // Keep the historical function name internally to avoid touching unrelated call sites,
    // while the customer-facing asset is the original MW Play mark.
    Image(
        painter = painterResource(id = com.stremio.mobile.R.drawable.mw_play_mark),
        contentDescription = "MW Play",
        modifier = modifier,
    )
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier.padding(start = ScreenGutter, end = 12.dp),
) {
    Text(
        text = title,
        modifier = modifier,
        color = Color.White,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
fun EmptyState(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 12.dp),
        color = MutedText,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
}

@Composable
fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = AccentPurple,
            strokeWidth = 3.dp,
        )
    }
}

@Composable
fun GlassPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AccentPurple.copy(alpha = 0.88f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = text.uppercase(),
            color = Color.White,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
