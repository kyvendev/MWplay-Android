package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.stremio.mobile.core.theme.AccentGreen
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.CardFallback
import com.stremio.mobile.core.theme.AccentGlow
import com.stremio.mobile.core.theme.HairlineBorder
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.SubtleText
import com.stremio.mobile.core.theme.SurfaceHigh
import com.stremio.mobile.core.theme.SurfaceLow
import com.stremio.mobile.core.theme.SurfaceMid
import com.stremio.mobile.core.theme.TouchTarget
import com.stremio.mobile.data.model.MetaDetails
import com.stremio.mobile.presentation.components.LocalGlobalUiTheme
import com.stremio.mobile.presentation.components.drawBackdropSafe
import com.stremio.mobile.presentation.components.rememberGlobalHapticFeedback

@Composable
fun DetailSheet(
    details: MetaDetails,
    inLibrary: Boolean,
    onBack: () -> Unit,
    onToggleLibrary: () -> Unit,
    onOpenStreams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp.dp * 0.82f).coerceAtMost(720.dp)
    // Shorter artwork on landscape phones so the actions stay reachable without scrolling.
    val artworkHeight = (configuration.screenHeightDp.dp * 0.28f).coerceIn(140.dp, 240.dp)
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Tablets and landscape: a centered sheet instead of a screen-wide slab.
            .widthIn(max = 640.dp)
            .heightIn(min = 420.dp.coerceAtMost(maxSheetHeight), max = maxSheetHeight)
            .clip(sheetShape)
            .background(SurfaceLow)
            .border(1.dp, HairlineBorder, sheetShape)
            .pointerInput(Unit) {
                var dragAccumulator = 0f
                var hasTriggered = false
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f; hasTriggered = false },
                    onVerticalDrag = { _, dragAmount ->
                        if (!hasTriggered) {
                            dragAccumulator += dragAmount
                            if (dragAccumulator < -40f) { hasTriggered = true; onOpenStreams() }
                        }
                    }
                )
            }
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(artworkHeight).background(CardFallback)) {
            AsyncImage(
                model = details.item.background ?: details.item.poster,
                contentDescription = details.item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colorStops = arrayOf(0f to Color(0x6606070D), 0.45f to Color.Transparent, 1f to SurfaceLow))
                )
            )
            // Drag handle: the sheet still opens the streams with an upward swipe.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f)),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Voltar",
                tint = Color.White,
                modifier = Modifier.padding(12.dp).size(TouchTarget).clip(CircleShape)
                    .background(Color(0x8006070D)).clickable(onClick = onBack).focusable().padding(12.dp),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = details.item.name, color = Color.White, fontSize = 26.sp, lineHeight = 31.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                details.year,
                details.runtime,
                when (details.item.type) { "movie" -> "Filme"; "series" -> "Série"; else -> null },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                details.item.imdbRating?.let { rating ->
                    Text(
                        text = "IMDb $rating",
                        color = Color(0xFF1A1300),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5C518)).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                if (meta.isNotEmpty()) {
                    Text(text = meta.joinToString("  •  "), color = Color(0xFFE2E3EE), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (details.genres.isNotEmpty()) {
                Text(text = details.genres.take(4).joinToString("  ·  "), color = MutedText, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                DetailLiquidActionButton(
                    label = "Assistir",
                    imageVector = Icons.Outlined.PlayArrow,
                    onClick = onOpenStreams,
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled = !details.isLoading,
                    tint = AccentPurple,
                )
                DetailLiquidActionButton(
                    label = if (inLibrary) "Na minha lista" else "Minha lista",
                    imageVector = if (inLibrary) Icons.Outlined.Check else Icons.Outlined.Add,
                    onClick = onToggleLibrary,
                    modifier = Modifier.weight(1f).height(52.dp),
                    tint = AccentPurple,
                    surface = true,
                )
            }
            if (details.isLoading) {
                CircularProgressIndicator(color = AccentPurple, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
            } else if (details.error != null) {
                Text(text = details.error, color = Color(0xFFFFC66D), fontSize = 14.sp)
            } else {
                Text(text = details.description ?: "Sinopse indisponível.", color = Color(0xFFDADBE6), fontSize = 15.sp,
                    lineHeight = 22.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                val credits = details.cast.take(4)
                if (credits.isNotEmpty()) {
                    Text(text = "Elenco: " + credits.joinToString(", "), color = SubtleText,
                        fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DetailLiquidActionButton(
    label: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: LayerBackdrop? = null,
    surface: Boolean = false,
) {
    val triggerHaptic = rememberGlobalHapticFeedback()
    val theme = LocalGlobalUiTheme.current
    val tuning = theme.liquidGlassTuning.clamped()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when { pressed -> 0.965f; focused -> 1.06f; else -> 1f },
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 430f),
        label = "detailLiquidButtonScale",
    )
    val shape = RoundedCornerShape(999.dp)
    val realGlass = enabled && backdrop != null && theme.style == "modern" && theme.glassEffectsMode != "static"
    // Dark text only sits on the light liquid-glass surface; the regular surfaces use white text.
    val contentColor = if (surface && realGlass) Color(0xFF101018).copy(alpha = if (enabled) 0.92f else 0.42f)
        else Color.White.copy(alpha = if (enabled) 0.96f else 0.42f)

    Box(
        modifier = modifier
            .scale(scale)
            .then(
                if (realGlass) {
                    Modifier.drawBackdropSafe(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = { vibrancy(); blur(4f.dp.toPx()); lens(refractionHeight = 16f.dp.toPx(), refractionAmount = 32f.dp.toPx(), depthEffect = true, chromaticAberration = tuning.chromaticAberration) },
                        highlight = { Highlight.Ambient.copy(width = Highlight.Ambient.width / 1.4f, blurRadius = Highlight.Ambient.blurRadius / 1.4f, alpha = 0.72f) },
                        shadow = { Shadow(radius = 18.dp, offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp), color = Color.Black.copy(alpha = 0.18f)) },
                        onDrawSurface = { if (surface) drawRoundRect(Color.White.copy(alpha = 0.62f)) else { drawRoundRect(tint, blendMode = BlendMode.Hue); drawRoundRect(tint.copy(alpha = 0.72f)) } },
                    )
                } else {
                    Modifier.clip(shape).background(
                        if (surface) {
                            Brush.verticalGradient(listOf(SurfaceHigh.copy(alpha = if (enabled) 1f else 0.5f), SurfaceMid.copy(alpha = if (enabled) 1f else 0.5f)))
                        } else {
                            Brush.verticalGradient(listOf(tint.copy(alpha = if (enabled) 1f else 0.25f), AccentGlow.copy(alpha = if (enabled) 0.92f else 0.2f)))
                        }
                    )
                }
            )
            .border(
                width = if (focused) 3.dp else 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = if (focused) listOf(Color.White, tint.copy(alpha = 0.95f))
                    else listOf(Color.White.copy(alpha = if (enabled) 0.58f else 0.16f), Color.White.copy(alpha = if (enabled) 0.22f else 0.08f))
                ),
                shape = shape,
            )
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                triggerHaptic(); onClick()
            }
            .focusable(enabled),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clip(shape).background(
                Brush.verticalGradient(colors = listOf(Color.White.copy(alpha = if (focused) 0.42f else if (enabled) 0.28f else 0.04f), Color.Transparent, Color.White.copy(alpha = if (surface) 0.08f else 0.04f)))
            )
        )
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp)) {
            Icon(imageVector = imageVector, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
