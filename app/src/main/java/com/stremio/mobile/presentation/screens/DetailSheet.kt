package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.stremio.mobile.core.theme.AccentGreen
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.CardFallback
import com.stremio.mobile.core.theme.HairlineBorder
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.StremioBackground
import com.stremio.mobile.core.theme.SubtleText
import com.stremio.mobile.core.theme.SurfaceHigh
import com.stremio.mobile.core.theme.TvButtonHeight
import com.stremio.mobile.core.theme.TvIconSize
import com.stremio.mobile.core.theme.TvTextBody
import com.stremio.mobile.core.theme.TvTextBodyLarge
import com.stremio.mobile.core.theme.TvTextDisplay
import com.stremio.mobile.core.theme.TvTextLabel
import com.stremio.mobile.data.model.MetaDetails
import com.stremio.mobile.presentation.components.LocalGlobalUiTheme
import com.stremio.mobile.presentation.components.drawBackdropSafe
import com.stremio.mobile.presentation.components.rememberGlobalHapticFeedback
import com.stremio.mobile.presentation.components.rememberIsTelevision
import com.stremio.mobile.presentation.components.tvFocusTarget

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
    val isTv = rememberIsTelevision()
    val listRequester = remember { FocusRequester() }
    val watchRequester = remember { FocusRequester() }

    // Detail is a modal surface on TV. Move focus into it immediately instead of leaving
    // the previously selected poster focused behind the sheet.
    LaunchedEffect(isTv, details.item.id) {
        if (isTv) {
            // Let the action buttons attach before requesting focus (same pattern as the rail).
            withFrameNanos { }
            runCatching {
                if (details.isLoading) listRequester.requestFocus() else watchRequester.requestFocus()
            }
        }
    }

    if (isTv) {
        TvDetailLayout(
            details = details,
            inLibrary = inLibrary,
            onBack = onBack,
            onToggleLibrary = onToggleLibrary,
            onOpenStreams = onOpenStreams,
            listRequester = listRequester,
            watchRequester = watchRequester,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .heightIn(min = 420.dp.coerceAtMost(maxSheetHeight), max = maxSheetHeight)
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(Color(0xF20B0C16))
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
        Box(modifier = Modifier.fillMaxWidth().height(152.dp).background(CardFallback)) {
            AsyncImage(
                model = details.item.background ?: details.item.poster,
                contentDescription = details.item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colors = listOf(Color(0x55000000), Color(0xFF0B0C16)))
                )
            )
            Box(
                modifier = Modifier
                    .padding(14.dp)
                    .size(48.dp)
                    .tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.12f)
                    .clip(CircleShape)
                    .background(Color(0x88000000))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = details.item.name, color = Color.White, fontSize = 22.sp, lineHeight = 27.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(details.year, details.runtime, details.item.imdbRating?.let { "IMDb $it" }).joinToString("  •  "),
                color = MutedText, fontSize = 13.sp,
            )
            if (details.isLoading) {
                CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(24.dp))
            } else if (details.error != null) {
                Text(text = details.error, color = Color(0xFFFFC66D), fontSize = 13.sp)
            } else {
                Text(text = details.description ?: "Sinopse indisponível.", color = Color(0xFFE4E0EE), fontSize = 13.sp,
                    lineHeight = 18.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Text(text = (details.genres + details.cast.take(3)).joinToString("  •  "), color = MutedText,
                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                DetailLiquidActionButton(
                    label = if (inLibrary) "Na minha lista" else "Minha lista",
                    imageVector = if (inLibrary) Icons.Outlined.Check else Icons.Outlined.Add,
                    onClick = onToggleLibrary,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .then(if (isTv) Modifier.focusRequester(listRequester).focusProperties { right = watchRequester } else Modifier),
                    tint = AccentPurple,
                    surface = true,
                )
                DetailLiquidActionButton(
                    label = "Assistir",
                    imageVector = Icons.Outlined.PlayArrow,
                    onClick = onOpenStreams,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .then(if (isTv) Modifier.focusRequester(watchRequester).focusProperties { left = listRequester } else Modifier),
                    enabled = !details.isLoading,
                    tint = AccentGreen,
                )
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
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 430f),
        label = "detailLiquidButtonScale",
    )
    val shape = RoundedCornerShape(999.dp)
    val contentColor = if (surface) Color(0xFF101018).copy(alpha = if (enabled) 0.92f else 0.42f)
        else Color.White.copy(alpha = if (enabled) 0.96f else 0.42f)
    val realGlass = enabled && backdrop != null && theme.style == "modern" && theme.glassEffectsMode != "static"

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
                        Brush.verticalGradient(
                            colors = if (surface) listOf(Color.White.copy(alpha = if (enabled) 0.78f else 0.14f), Color.White.copy(alpha = if (enabled) 0.46f else 0.10f))
                            else listOf(tint.copy(alpha = if (enabled) 0.78f else 0.12f), tint.copy(alpha = if (enabled) 0.48f else 0.10f))
                        )
                    )
                }
            )
            .tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.07f)
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                triggerHaptic(); onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clip(shape).background(
                Brush.verticalGradient(colors = listOf(Color.White.copy(alpha = if (enabled) 0.28f else 0.04f), Color.Transparent, Color.White.copy(alpha = if (surface) 0.08f else 0.04f)))
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


/**
 * Full-screen TV detail page: artwork fills the panel, information sits in a readable
 * left column and the primary action ("Assistir") is first and receives initial focus.
 */
@Composable
private fun TvDetailLayout(
    details: MetaDetails,
    inLibrary: Boolean,
    onBack: () -> Unit,
    onToggleLibrary: () -> Unit,
    onOpenStreams: () -> Unit,
    listRequester: FocusRequester,
    watchRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .focusGroup()
            .background(StremioBackground),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(details.item.background ?: details.item.poster)
                .size(1280, 720)
                .crossfade(true)
                .build(),
            contentDescription = details.item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopEnd,
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(colorStops = arrayOf(0f to Color(0xFA06070D), 0.42f to Color(0xD906070D), 0.75f to Color(0x4006070D), 1f to Color(0x1006070D)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(0f to Color(0x6606070D), 0.25f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0xF206070D)))))

        Box(
            modifier = Modifier
                .padding(start = 34.dp, top = 26.dp)
                .size(42.dp)
                .tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.1f)
                .clip(CircleShape)
                .background(SurfaceHigh.copy(alpha = 0.85f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar", tint = Color.White, modifier = Modifier.size(TvIconSize))
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.56f)
                .padding(start = 56.dp, top = 84.dp, bottom = 34.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = details.item.name,
                color = Color.White,
                fontSize = TvTextDisplay,
                lineHeight = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                details.year,
                details.runtime,
                when (details.item.type) { "movie" -> "Filme"; "series" -> "Série"; else -> null },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                details.item.imdbRating?.let { rating ->
                    Text(
                        text = "IMDb $rating",
                        color = Color(0xFF1A1300),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5C518)).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString("   •   "), color = Color(0xFFE2E3EE), fontSize = TvTextBodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            if (details.genres.isNotEmpty()) {
                Text(details.genres.take(4).joinToString("  ·  "), color = MutedText, fontSize = TvTextBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                details.isLoading -> CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(26.dp))
                details.error != null -> Text(text = details.error, color = Color(0xFFFFC66D), fontSize = TvTextBodyLarge)
                else -> Text(
                    text = details.description ?: "Sinopse indisponível.",
                    color = Color(0xFFDADBE6),
                    fontSize = TvTextBodyLarge,
                    lineHeight = 23.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                TvDetailButton(
                    label = "Assistir",
                    imageVector = Icons.Outlined.PlayArrow,
                    onClick = onOpenStreams,
                    primary = true,
                    enabled = !details.isLoading,
                    modifier = Modifier.focusRequester(watchRequester).focusProperties { right = listRequester },
                )
                TvDetailButton(
                    label = if (inLibrary) "Na minha lista" else "Minha lista",
                    imageVector = if (inLibrary) Icons.Outlined.Check else Icons.Outlined.Add,
                    onClick = onToggleLibrary,
                    primary = false,
                    modifier = Modifier.focusRequester(listRequester).focusProperties { left = watchRequester },
                )
            }
            val credits = details.cast.take(4)
            if (credits.isNotEmpty() && !details.isLoading) {
                Text(
                    text = "Elenco: " + credits.joinToString(", "),
                    color = SubtleText,
                    fontSize = TvTextLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TvDetailButton(
    label: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val triggerHaptic = rememberGlobalHapticFeedback()
    val shape = RoundedCornerShape(999.dp)
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = modifier
            .height(TvButtonHeight)
            .widthIn(min = 172.dp)
            .tvFocusTarget(enabled = enabled, cornerRadius = 999.dp, focusedScale = 1.06f)
            .clip(shape)
            .background(if (primary) AccentPurple.copy(alpha = alpha) else SurfaceHigh.copy(alpha = 0.9f * alpha))
            .then(if (primary) Modifier else Modifier.border(1.dp, HairlineBorder, shape))
            .clickable(enabled = enabled) { triggerHaptic(); onClick() }
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector, contentDescription = null, tint = Color.White.copy(alpha = alpha), modifier = Modifier.size(TvIconSize))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White.copy(alpha = alpha), fontSize = TvTextBodyLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
