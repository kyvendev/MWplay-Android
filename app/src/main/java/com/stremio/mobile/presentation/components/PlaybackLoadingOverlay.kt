package com.stremio.mobile.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.stremio.mobile.core.theme.AccentGlow
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.StremioBackground
import com.stremio.mobile.core.theme.StremioBackgroundBrush
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.presentation.state.StreamsUiState

/** What the cinematic loading screen shows; built only from data already in [StreamsUiState]. */
data class PlaybackLoadingInfo(
    val title: String,
    val backdropUrl: String?,
    val episodeLabel: String? = null,
    val episodeTitle: String? = null,
)

/** Maps the item/episode that is being prepared for playback. No extra requests are made. */
fun StreamsUiState.toPlaybackLoadingInfo(): PlaybackLoadingInfo? {
    val item = forItem ?: return null
    val episode: EpisodeOption? = selectedVideoId?.let { id -> episodes.firstOrNull { it.videoId == id } }
    val episodeLabel = when {
        !isSeries -> null
        episode != null && episode.season > 0 && episode.episode > 0 ->
            "Temporada ${episode.season} · Episódio ${episode.episode}"
        // Episodes played via "next episode" may not be in the loaded season list; reuse its label.
        else -> selectedEpisodeLabel?.takeIf { it.isNotBlank() }
    }
    return PlaybackLoadingInfo(
        title = item.name,
        // Backdrop first, then the episode still, then the poster; the brand gradient covers the rest.
        backdropUrl = item.background ?: episode?.thumbnail ?: item.poster,
        episodeLabel = episodeLabel,
        episodeTitle = episode?.title?.takeIf { it.isNotBlank() && isSeries },
    )
}

/**
 * Full-screen cinematic layer shown only while playback is really being prepared. It swallows
 * touches so nothing hidden underneath can be tapped by accident, while the system back
 * gesture/button keeps working through the screens' existing BackHandlers.
 *
 * [startVisible] lets the player take over from the streams screen without a second fade-in.
 */
@Composable
fun PlaybackLoadingOverlay(
    info: PlaybackLoadingInfo?,
    visible: Boolean,
    modifier: Modifier = Modifier,
    startVisible: Boolean = false,
) {
    val visibleState = remember { MutableTransitionState(startVisible) }
    visibleState.targetState = visible && info != null
    // Keep the last content while fading out, even if the caller already cleared it.
    val lastInfo = remember { arrayOfNulls<PlaybackLoadingInfo>(1) }
    if (info != null) lastInfo[0] = info
    val shown = lastInfo[0] ?: return

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(tween(durationMillis = 280)),
        exit = fadeOut(tween(durationMillis = 420)),
    ) {
        PlaybackLoadingContent(shown)
    }
}

@Composable
internal fun PlaybackLoadingContent(
    info: PlaybackLoadingInfo,
    phase: State<LoadingPhase> = rememberLoadingPhase(),
) {
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StremioBackground)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        val landscape = maxWidth > maxHeight
        // Fallback when there is no artwork (or it fails to load): brand gradient plus a soft glow.
        Box(Modifier.fillMaxSize().background(StremioBackgroundBrush))
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(AccentGlow.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset.Zero,
                    radius = 1400f,
                ),
            ),
        )
        if (info.backdropUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(info.backdropUrl)
                    .size(1280, 720)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    // A very slow push-in: one GPU layer transform, no re-layout or blur.
                    .graphicsLayer {
                        val zoom = 1.04f + 0.02f * phase.value.slowDrift
                        scaleX = zoom
                        scaleY = zoom
                    },
                contentScale = ContentScale.Crop,
                alignment = if (landscape) Alignment.TopEnd else Alignment.Center,
            )
        }
        if (landscape) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(colorStops = arrayOf(0f to Color(0xF206070D), 0.5f to Color(0xB306070D), 1f to Color(0x5906070D)))))
        }
        // Portrait relies on a taller bottom fade so the title block sits on solid ground.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(0f to Color(0x8C06070D), 0.3f to Color(0x1A06070D), if (landscape) 0.6f to Color(0x2606070D) else 0.45f to Color(0x6606070D), 1f to Color(0xFA06070D)))))

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .fillMaxWidth(if (landscape) 0.68f else 1f)
                .padding(start = 24.dp, end = 24.dp, bottom = if (landscape) 28.dp else 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            info.episodeLabel?.let {
                Text(
                    text = it.uppercase(),
                    color = Color(0xFFC9B8FF),
                    fontSize = 12.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val titleSize = when {
                isTablet -> 46.sp
                landscape -> 34.sp
                else -> 36.sp
            }
            Text(
                text = info.title,
                fontSize = titleSize,
                lineHeight = titleSize * 1.12f,
                fontWeight = FontWeight.ExtraBold,
                maxLines = if (landscape) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier.titleReveal(phase),
            )
            info.episodeTitle?.let {
                Text(
                    text = it,
                    color = Color(0xFFDADBE6),
                    fontSize = if (isTablet) 20.sp else 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Preparando a reprodução",
                color = MutedText.copy(alpha = 0.85f),
                fontSize = 13.sp,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

internal data class LoadingPhase(
    /** 0..1 position of the light front across the title. */
    val sweep: Float,
    /** True while the title is being lit (gray → white), false while it dims back. */
    val revealing: Boolean,
    /** 0..1 slow back-and-forth drift for the backdrop push-in. */
    val slowDrift: Float,
)

internal const val RevealPeriodMs = 2_600L
private const val DriftPeriodMs = 24_000L

/** Pure, wall-clock based phase: reveal sweep, then dim sweep, repeating indefinitely. */
internal fun loadingPhaseAt(nowMs: Long): LoadingPhase {
    val cycle = nowMs % (RevealPeriodMs * 2)
    val revealing = cycle < RevealPeriodMs
    val linear = (cycle % RevealPeriodMs).toFloat() / RevealPeriodMs
    // Ease in-out so the light settles softly at both ends of the title.
    val eased = linear * linear * (3f - 2f * linear)
    val driftLinear = (nowMs % DriftPeriodMs).toFloat() / DriftPeriodMs
    val drift = if (driftLinear < 0.5f) driftLinear * 2f else (1f - driftLinear) * 2f
    return LoadingPhase(eased, revealing, drift)
}

/**
 * Indeterminate phase derived from wall-clock time, so the effect continues seamlessly when the
 * streams layer hands over to the player layer. Uses the infinite-animation frame clock, which
 * tests and "remove animations" accessibility settings can pause.
 */
@Composable
private fun rememberLoadingPhase(): State<LoadingPhase> {
    val inPreview = LocalInspectionMode.current
    val state = remember { mutableStateOf(LoadingPhase(0f, true, 0f)) }
    LaunchedEffect(inPreview) {
        if (inPreview) return@LaunchedEffect
        while (true) {
            withInfiniteAnimationFrameMillis { }
            state.value = loadingPhaseAt(System.currentTimeMillis())
        }
    }
    return state
}

private val DimTitle = Color(0xFF4A4C5A)
private val LitTitle = Color.White
private val Glint = Color(0xFFEDE6FF)

/**
 * Lights the (white) title progressively: a soft front sweeps left → right turning dim gray into
 * white, then sweeps again dimming it, indefinitely. Drawn with SrcAtop on an offscreen layer so
 * only the glyphs are tinted; the phase is read in the draw phase only (no recomposition).
 */
private fun Modifier.titleReveal(phase: State<LoadingPhase>): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val p = phase.value
        val edge = 0.18f
        // The front travels a little beyond both edges so the title fully settles each cycle.
        val front = -edge + p.sweep * (1f + 2f * edge)
        val behind = if (p.revealing) LitTitle else DimTitle
        val ahead = if (p.revealing) DimTitle else LitTitle
        val start = (front - edge).coerceIn(0f, 1f)
        val end = (front + edge).coerceIn(0f, 1f)
        val glintAt = front.coerceIn(0f, 1f)
        val brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to behind,
                start to behind,
                glintAt to if (p.revealing) Glint else lerp(DimTitle, LitTitle, 0.5f),
                end to ahead,
                1f to ahead,
            ).sortedBy { it.first }.toTypedArray(),
            startX = 0f,
            endX = size.width,
        )
        drawRect(brush = brush, topLeft = Offset.Zero, size = size, blendMode = BlendMode.SrcAtop)
    }
