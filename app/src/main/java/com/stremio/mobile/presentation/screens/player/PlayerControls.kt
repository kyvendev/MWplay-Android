package com.stremio.mobile.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.player.PlayerResizeMode
import com.stremio.mobile.presentation.components.DpadValueKeyAction
import com.stremio.mobile.presentation.components.dpadValueKeyAction
import com.stremio.mobile.presentation.components.tvFocusTarget
import kotlinx.coroutines.delay

data class PlayerControlsState(
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPositionMs: Long,
    val currentSpeed: Float,
    val resizeMode: PlayerResizeMode,
    val title: String,
    val showControls: Boolean,
    val hasInfoHash: Boolean,
    val isStatsVisible: Boolean,
    val canSelectSubtitles: Boolean,
    val canSelectAudio: Boolean,
    val isMuted: Boolean,
    val volumeFraction: Float,
    val seekStepMs: Long = 10_000L,
    val canPlayNext: Boolean = false,
)

class PlayerControlsActions(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSkipForward: () -> Unit,
    val onSkipBack: () -> Unit,
    val onCycleSpeed: () -> Unit,
    val onCycleAspect: () -> Unit,
    val onToggleMute: () -> Unit,
    val onShowSubtitles: () -> Unit,
    val onShowAudio: () -> Unit,
    val onToggleStats: () -> Unit,
    val onDecreaseVolume: () -> Unit = {},
    val onIncreaseVolume: () -> Unit = {},
    val onPlayNext: () -> Unit = {},
)

@Composable
fun ClassicPlayerControls(state: PlayerControlsState, actions: PlayerControlsActions, backdrop: LayerBackdrop?, modifier: Modifier = Modifier) = TvPlayerControls(state, actions, modifier)

@Composable
fun ModernPlayerControls(state: PlayerControlsState, actions: PlayerControlsActions, backdrop: LayerBackdrop?, glassEffectsMode: String, hapticsEnabled: Boolean = true, hapticsIntensity: String = "Medium", modifier: Modifier = Modifier) = TvPlayerControls(state, actions, modifier)

@Composable
private fun TvPlayerControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
    if (!state.showControls) return

    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        primaryFocus.requestFocus()
    }

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent, Color(0xE6000000))))) {
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(52.dp).tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.12f)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(18.dp))
            Text(state.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }

            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(Icons.Outlined.FastRewind, "Voltar ${state.seekStepMs / 1000} segundos", 58, actions.onSkipBack)
                IconButton(
                    onClick = actions.onPlayPause,
                    modifier = Modifier
                        .size(82.dp)
                        .focusRequester(primaryFocus)
                        .tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.13f)
                        .background(Color(0x55000000), CircleShape)
                        .border(2.dp, Color.White.copy(alpha = .45f), CircleShape)
                ) {
                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pausar" else "Reproduzir", tint = Color.White, modifier = Modifier.size(44.dp))
                }
                TvIconButton(Icons.Outlined.FastForward, "Avançar ${state.seekStepMs / 1000} segundos", 58, actions.onSkipForward)
            }

        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 38.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvTimeline(state, actions)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Mute shows the current state (crossed speaker when muted); the volume steps use - and + so the three
                    // different actions never share an icon.
                    TvIconButton(if (state.isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp, if (state.isMuted) "Ativar som" else "Silenciar", 50, actions.onToggleMute)
                    TvIconButton(Icons.Outlined.Remove, "Diminuir volume", 50, actions.onDecreaseVolume)
                    TvIconButton(Icons.Outlined.Add, "Aumentar volume", 50, actions.onIncreaseVolume)
                    TvTextButton("${state.currentSpeed}x", Icons.Outlined.Speed, actions.onCycleSpeed)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.canSelectSubtitles) TvIconButton(Icons.Outlined.Subtitles, "Legendas", 50, actions.onShowSubtitles)
                    if (state.canSelectAudio) TvIconButton(Icons.Outlined.Audiotrack, "Áudio", 50, actions.onShowAudio)
                    if (state.canPlayNext) TvIconButton(Icons.Outlined.SkipNext, "Próximo episódio", 50, actions.onPlayNext)
                    TvTextButton(resizeLabel(state.resizeMode), Icons.Outlined.AspectRatio, actions.onCycleAspect)
                    if (state.hasInfoHash) TvIconButton(Icons.Outlined.Info, "Informações", 50, actions.onToggleStats)
                }
            }
        }
    }
}

@Composable
private fun TvIconButton(icon: ImageVector, description: String, size: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(size.dp).tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1.12f)) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size((size * .52f).dp))
    }
}

@Composable
private fun TvTextButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(50.dp).tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1.08f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvTimeline(state: PlayerControlsState, actions: PlayerControlsActions) {
    val duration = state.durationMs.coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val focusManager = LocalFocusManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(state.positionMs), color = Color.White, fontSize = 14.sp, modifier = Modifier.width(62.dp), textAlign = TextAlign.Center)
        Slider(
            value = progress,
            onValueChange = { actions.onSeekTo((it * duration).toLong()) },
            enabled = state.durationMs > 0,
            modifier = Modifier.weight(1f).height(44.dp)
                .tvFocusTarget(cornerRadius = 12.dp, focusedScale = 1.015f)
                .semantics {
                    contentDescription = "Progresso do vídeo"
                    stateDescription = "${formatTime(state.positionMs)} de ${formatTime(state.durationMs)}"
                }
                .onPreviewKeyEvent { event ->
                    when (val action = dpadValueKeyAction(event.key)) {
                        DpadValueKeyAction.DECREASE, DpadValueKeyAction.INCREASE -> {
                            if (state.durationMs <= 0) false else {
                                if (event.type == KeyEventType.KeyDown) {
                                    val delta = if (action == DpadValueKeyAction.INCREASE) state.seekStepMs else -state.seekStepMs
                                    actions.onSeekTo((state.positionMs + delta).coerceIn(0L, duration))
                                }
                                true
                            }
                        }
                        // Consumed here so the Slider never sees them; focus moves to the row above/below instead.
                        DpadValueKeyAction.FOCUS_UP, DpadValueKeyAction.FOCUS_DOWN -> {
                            if (event.type == KeyEventType.KeyDown) {
                                focusManager.moveFocus(if (action == DpadValueKeyAction.FOCUS_UP) FocusDirection.Up else FocusDirection.Down)
                            }
                            true
                        }
                        DpadValueKeyAction.PASS -> false
                    }
                },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AccentPurple, inactiveTrackColor = Color.White.copy(alpha = .28f))
        )
        Text(formatTime(state.durationMs), color = Color.White, fontSize = 14.sp, modifier = Modifier.width(62.dp), textAlign = TextAlign.Center)
    }
}

private fun resizeLabel(mode: PlayerResizeMode): String = when (mode) {
    PlayerResizeMode.FIT -> "Ajustar"
    PlayerResizeMode.STRETCH -> "Esticar"
    PlayerResizeMode.ZOOM -> "Zoom"
}

private fun formatTime(ms: Long): String {
    val t = (ms / 1000L).coerceAtLeast(0L)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

