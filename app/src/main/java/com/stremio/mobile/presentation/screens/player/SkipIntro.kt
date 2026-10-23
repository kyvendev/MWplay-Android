package com.stremio.mobile.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.player.intro.IntroInterval
import com.stremio.mobile.player.intro.IntroSkipPolicy
import com.stremio.mobile.player.intro.IntroSource
import com.stremio.mobile.presentation.components.rememberIsTelevision
import com.stremio.mobile.presentation.components.tvFocusTarget
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

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

/**
 * Lets the user mark where the opening starts and ends using the current playback position, for
 * this episode or for every episode of the series.
 */
@Composable
fun IntroMarkDialog(
    currentPositionMs: Long,
    durationMs: Long,
    existing: IntroInterval?,
    onSave: (startMs: Long, endMs: Long, wholeSeries: Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var startMs by remember { mutableStateOf(existing?.startMs) }
    var endMs by remember { mutableStateOf(existing?.endMs) }
    var wholeSeries by remember { mutableStateOf(existing?.source == IntroSource.MANUAL_SERIES) }
    val candidate = if (startMs != null && endMs != null) IntroInterval(startMs!!, endMs!!, IntroSource.MANUAL_EPISODE) else null
    val valid = candidate != null && IntroSkipPolicy.isReliable(candidate, durationMs)
    val firstFocus = remember { FocusRequester() }
    val isTv = rememberIsTelevision()
    LaunchedEffect(isTv) {
        if (isTv) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1B26),
        title = { Text("Marcar abertura", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Use a posição atual do vídeo para marcar o início e o fim da abertura.",
                    color = MutedText,
                    fontSize = 14.sp,
                )
                OutlinedButton(onClick = { startMs = currentPositionMs }, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus).tvFocusTarget(cornerRadius = 20.dp, focusedScale = 1.02f)) {
                    Text("Início: ${startMs?.let(::formatIntroTime) ?: "—"}  ·  marcar em ${formatIntroTime(currentPositionMs)}", color = Color.White)
                }
                OutlinedButton(onClick = { endMs = currentPositionMs }, modifier = Modifier.fillMaxWidth().tvFocusTarget(cornerRadius = 20.dp, focusedScale = 1.02f)) {
                    Text("Fim: ${endMs?.let(::formatIntroTime) ?: "—"}  ·  marcar em ${formatIntroTime(currentPositionMs)}", color = Color.White)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .tvFocusTarget(cornerRadius = 10.dp, focusedScale = 1.02f)
                        .clickable(role = Role.Checkbox) { wholeSeries = !wholeSeries },
                ) {
                    Checkbox(
                        checked = wholeSeries,
                        onCheckedChange = { wholeSeries = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentPurple),
                    )
                    Text("Usar em todos os episódios desta série", color = Color.White, fontSize = 14.sp)
                }
                if (candidate != null && !valid) {
                    Text(
                        "A abertura precisa ter entre 5 segundos e 4 minutos, com o fim depois do início.",
                        color = Color(0xFFFFB4AB),
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { candidate?.let { onSave(it.startMs, it.endMs, wholeSeries) } }, enabled = valid, modifier = Modifier.tvFocusTarget(cornerRadius = 20.dp)) {
                Text("Salvar", color = if (valid) AccentPurple else MutedText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onClear, modifier = Modifier.tvFocusTarget(cornerRadius = 20.dp)) { Text("Remover marcação", color = MutedText) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.tvFocusTarget(cornerRadius = 20.dp)) { Text("Cancelar", color = MutedText) }
            }
        },
    )
}

internal fun formatIntroTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
