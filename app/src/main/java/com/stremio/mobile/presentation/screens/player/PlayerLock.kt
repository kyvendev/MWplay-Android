package com.stremio.mobile.presentation.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import kotlinx.coroutines.delay

/** How long the lock indicator stays after a tap on the locked screen. */
const val PLAYER_LOCK_HINT_MS = 3_000L

/**
 * Screen lock of the player. While it is shown, every touch on the player is swallowed here:
 * no pause/resume, seeking, double-tap, swipe (volume/brightness/seek), menus or controls.
 * The video keeps playing; nothing is sent to the engine, so it works the same with EXO, VLC
 * and MPV. A tap only reveals the closed lock for a few seconds; tapping the lock itself
 * unlocks (with a short closed-to-open animation) and never also acts on playback.
 */
@Composable
fun PlayerLockLayer(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    hintDurationMs: Long = PLAYER_LOCK_HINT_MS,
) {
    // Shown right after locking, then after every tap on the screen.
    var hintRequest by remember { mutableIntStateOf(1) }
    var hintVisible by remember { mutableStateOf(true) }
    var unlocking by remember { mutableStateOf(false) }

    LaunchedEffect(hintRequest) {
        hintVisible = true
        delay(hintDurationMs)
        if (!unlocking) hintVisible = false
    }
    LaunchedEffect(unlocking) {
        if (!unlocking) return@LaunchedEffect
        delay(UNLOCK_ANIMATION_MS)
        onUnlock()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag(PLAYER_LOCK_LAYER_TAG)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        hintRequest++
                    }
                },
        )
        AnimatedVisibility(
            visible = hintVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            LockIndicator(unlocking = unlocking, onTapLock = { if (!unlocking) unlocking = true })
        }
    }
}

@Composable
private fun LockIndicator(unlocking: Boolean, onTapLock: () -> Unit) {
    // A small "pop" when the lock appears or opens.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (!appeared) 0.8f else if (unlocking) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "lockScale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Color(0xE6141422))
                .border(1.dp, Color(0x40FFFFFF), CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Desbloquear tela", onClick = onTapLock),
        ) {
            Crossfade(targetState = unlocking, label = "lockIcon") { open ->
                Icon(
                    imageVector = if (open) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    contentDescription = if (open) null else "Desbloquear tela",
                    tint = AccentPurple,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = if (unlocking) "Tela desbloqueada" else "Tela bloqueada · toque no cadeado para desbloquear",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xB3141422))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

const val PLAYER_LOCK_LAYER_TAG = "player-lock-layer"
private const val UNLOCK_ANIMATION_MS = 250L
