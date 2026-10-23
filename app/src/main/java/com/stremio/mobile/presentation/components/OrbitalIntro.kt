package com.stremio.mobile.presentation.components

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.stremio.mobile.R
import com.stremio.mobile.core.theme.AppFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Animated MW Play opening ("Orbital"): a violet orb draws a ring of light, converges into the
 * play symbol and reveals the MW Play logo. It is a visual layer over the normal startup: the
 * app keeps initialising underneath, nothing waits on it.
 *
 * Exit: once the logo is in (~5.3 s) and [ready] is true, it fades out. If startup takes longer
 * it holds the logo with a small spinner; a tap (or OK on the remote, see [skipRequests]) jumps
 * to the logo / exits early.
 */
@Composable
fun OrbitalIntro(
    ready: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** Incremented by the host when the remote's OK is pressed (TV has no touch). */
    skipRequests: Int = 0,
) {
    val context = LocalContext.current
    val reducedMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val clock = remember { IntroClock(startAt = if (reducedMotion) OrbitalTimeline.LOGO_IN else 0f) }
    val latestReady by rememberUpdatedState(ready)
    val latestOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(skipRequests) {
        if (skipRequests > 0) clock.skip()
    }

    LaunchedEffect(clock) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f) // no jumps after a pause
            last = now
            clock.elapsed += dt
            clock.time = if (reducedMotion) OrbitalTimeline.LOGO_IN else clock.time + dt

            val exitAt = clock.exitAt
            if (exitAt == null) {
                val logoShown = clock.time >= OrbitalTimeline.LOGO_SETTLED || (reducedMotion && clock.elapsed >= 0.6f)
                if ((latestReady && (logoShown || clock.skipRequested)) || clock.elapsed >= OrbitalTimeline.HARD_CAP) {
                    clock.exitAt = clock.elapsed
                }
            } else if (clock.elapsed - exitAt >= OrbitalTimeline.FADE_OUT) {
                latestOnFinished()
                break
            }
        }
    }

    val mark = painterResource(R.drawable.mw_play_mark)
    val textMeasurer = rememberTextMeasurer()
    val wordStyle = remember { TextStyle(fontFamily = AppFont.PLUS_JAKARTA_SANS.fontFamily, fontWeight = FontWeight.ExtraBold, color = Color.White) }

    Box(
        modifier = modifier
            .graphicsLayer {
                val exitAt = clock.exitAt
                alpha = if (exitAt == null) 1f else 1f - easeInOut(((clock.elapsed - exitAt) / OrbitalTimeline.FADE_OUT).coerceIn(0f, 1f))
            }
            .semantics { contentDescription = "MW Play" }
            .pointerInput(clock) {
                detectTapGestures { clock.skip() }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val contentAlpha = if (reducedMotion) (clock.elapsed / 0.3f).coerceIn(0f, 1f) else 1f
            // Reduced motion freezes the timeline on the logo, so the spinner follows real time.
            val holdTime = if (reducedMotion) OrbitalTimeline.LOGO_IN + clock.elapsed else clock.time
            drawOrbital(clock.time, clock.elapsed, holdTime, contentAlpha, mark, textMeasurer, wordStyle)
        }
    }
}

/** Remote keys that skip the opening animation. */
private val IntroSkipKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA)

/**
 * Key handling while the opening covers the app (TV): OK skips, every other key is swallowed so
 * the remote cannot move focus in the hidden screen underneath. Back is left to the system.
 * Returns whether the event was consumed.
 */
internal fun interceptIntroKey(event: KeyEvent, onSkip: () -> Unit): Boolean {
    if (event.key == Key.Back) return false
    if (event.type == KeyEventType.KeyUp && event.key in IntroSkipKeys) onSkip()
    return true
}

@Stable
private class IntroClock(startAt: Float) {
    /** Position on the animation timeline, in seconds. */
    var time by mutableFloatStateOf(startAt)
    /** Real time since the intro appeared. */
    var elapsed by mutableFloatStateOf(0f)
    var exitAt by mutableStateOf<Float?>(null)
    var skipRequested = false

    fun skip() {
        skipRequested = true
        if (time < OrbitalTimeline.LOGO_IN) time = OrbitalTimeline.LOGO_IN
    }
}

internal object OrbitalTimeline {
    /** Logo reveal finished; skipping jumps here. */
    const val LOGO_IN = 5.0f
    /** Earliest moment the intro may start leaving. */
    const val LOGO_SETTLED = 5.3f
    const val FADE_OUT = 0.45f
    /** Never cover the app for longer than this, whatever the startup state. */
    const val HARD_CAP = 12f
}

private val Violet = Color(0xFF8A63FF)
private val Lilac = Color(0xFFB9A2FF)
private val Base = Color(0xFF05040A)
private const val TAU = (2 * PI).toFloat()

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeIn(x: Float) = x * x * x
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOutBack(x: Float): Float {
    val c1 = 1.2f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, a: Float) {
    if (a <= 0f || radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = a.coerceAtMost(1f)),
            0.35f to color.copy(alpha = (a * 0.45f).coerceAtMost(1f)),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.arc(c: Offset, r: Float, from: Float, to: Float, color: Color, width: Float) {
    drawArc(
        color = color,
        startAngle = Math.toDegrees(from.toDouble()).toFloat(),
        sweepAngle = Math.toDegrees((to - from).toDouble()).toFloat(),
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
}

private fun DrawScope.ring(c: Offset, r: Float, a0: Float, a1: Float, alpha: Float, u: Float, head: Float) {
    if (alpha <= 0f || a1 <= a0) return
    val al = alpha.coerceAtMost(1f)
    arc(c, r, a0, a1, Violet.copy(alpha = 0.12f * al), 34 * u)
    arc(c, r, a0, a1, Violet.copy(alpha = 0.32f * al), 14 * u)
    arc(c, r, a0, a1, Violet.copy(alpha = 0.55f * al), 6 * u)
    arc(c, r, a0, a1, Lilac.copy(alpha = 0.95f * al), 3.4f * u)
    if (head > 0f) {
        // White-hot tail behind the moving head.
        val tail = min(0.9f, a1 - a0)
        val steps = 18
        for (i in 0 until steps) {
            val s0 = a1 - tail * (1 - i / steps.toFloat())
            val s1 = a1 - tail * (1 - (i + 1) / steps.toFloat())
            val a = (i / steps.toFloat()).pow(2) * 0.9f * head * al
            arc(c, r, s0, s1 + 0.002f, Color.White.copy(alpha = a), 2.2f * u)
        }
    }
}

private fun DrawScope.orb(p: Offset, r: Float, b: Float) {
    if (b <= 0f) return
    glow(p, r * 11, Violet, 0.22f * b)
    glow(p, r * 4.5f, Violet, 0.55f * b)
    glow(p, r * 2, Lilac, 0.9f * b)
    drawCircle(Color.White.copy(alpha = b.coerceAtMost(1f)), r, p)
}

private fun DrawScope.sparkle(p: Offset, s: Float, a: Float) {
    if (a <= 0f) return
    glow(p, s * 4, Lilac, 0.5f * a)
    val d = s * 2.2f
    val path = Path().apply {
        moveTo(p.x, p.y - d)
        quadraticTo(p.x, p.y, p.x + d, p.y)
        quadraticTo(p.x, p.y, p.x, p.y + d)
        quadraticTo(p.x, p.y, p.x - d, p.y)
        quadraticTo(p.x, p.y, p.x, p.y - d)
        close()
    }
    drawPath(path, Color.White.copy(alpha = a.coerceAtMost(1f)))
}

/** Play triangle with softly rounded corners, optically centred on [c]. */
private fun DrawScope.playTriangle(c: Offset, size: Float, a: Float) {
    if (a <= 0f || size <= 0f) return
    val w = size * 0.92f
    val x0 = c.x - w * 0.38f
    val x1 = c.x + w * 0.62f
    val pts = listOf(Offset(x0, c.y - size / 2), Offset(x1, c.y), Offset(x0, c.y + size / 2))
    val r = size * 0.09f
    val path = Path()
    for (i in 0 until 3) {
        val prev = pts[(i + 2) % 3]
        val cur = pts[i]
        val next = pts[(i + 1) % 3]
        val inDir = (cur - prev) / (cur - prev).getDistance()
        val outDir = (next - cur) / (next - cur).getDistance()
        val start = cur - inDir * r
        val end = cur + outDir * r
        if (i == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
        path.quadraticTo(cur.x, cur.y, end.x, end.y)
    }
    path.close()
    drawPath(path, Color.White.copy(alpha = a.coerceAtMost(1f)))
}

/** Official mark (res/drawable/mw_play_mark) + "Play" wordmark, centred on [c]. */
private fun DrawScope.logo(
    c: Offset, u: Float, a: Float, rise: Float, scale: Float,
    mark: Painter, textMeasurer: TextMeasurer, style: TextStyle,
) {
    if (a <= 0f) return
    val markH = 104 * u              // glyph height: viewport rows 30..78 (48 units)
    val k = markH / 48f
    val markW = 98 * k               // viewport columns 8..106
    val painterSize = 108 * k
    val text = textMeasurer.measure("Play", style.copy(fontSize = (118 * u).toSp()))
    val gap = 22 * u
    val total = markW + gap + text.size.width
    val left = c.x - total / 2

    val al = a.coerceAtMost(1f)
    translate(top = rise) {
        scale(scale, pivot = c) {
            glow(c, total * 0.75f, Violet, 0.16f * al)
            translate(left = left - 8 * k, top = c.y - 54 * k) {
                with(mark) { draw(Size(painterSize, painterSize), alpha = al) }
            }
            drawText(
                textLayoutResult = text,
                topLeft = Offset(left + markW + gap, c.y - text.size.height / 2f + 4 * u),
                alpha = al,
            )
        }
    }
}

private fun DrawScope.drawOrbital(
    t: Float, elapsed: Float, holdTime: Float, contentAlpha: Float,
    mark: Painter, textMeasurer: TextMeasurer, wordStyle: TextStyle,
) {
    val w = size.width
    val h = size.height
    val u = min(w, h) / 1080f
    val c = Offset(w / 2, h / 2)
    val ringBase = 190 * u

    // Background: near-black with a faint violet bloom.
    drawRect(Base)
    val bgA = easeOut(seg(t, 0f, 1.2f))
    drawRect(
        Brush.radialGradient(
            0f to Color(58, 30, 130).copy(alpha = 0.42f * bgA),
            0.5f to Color(30, 16, 70).copy(alpha = 0.25f * bgA),
            1f to Color.Transparent,
            center = c,
            radius = max(w, h) * 0.6f,
        ),
    )
    if (contentAlpha <= 0f) return

    // Orbit geometry.
    val start = -PI.toFloat() / 2
    val lift = easeInOut(seg(t, 1.0f, 1.3f))
    val sweep = easeInOut(seg(t, 1.2f, 2.5f))
    val conv = seg(t, 2.5f, 3.0f)
    val ang = start + sweep * TAU + easeOut(conv) * PI.toFloat() * 0.9f
    val rad = ringBase * lift * (1 - easeIn(conv))
    val orbPos = Offset(c.x + cos(ang) * rad, c.y + sin(ang) * rad)

    // Ring: drawn by the orb, pulses as the light converges, then expands and dissolves.
    val ringOut = easeOut(seg(t, 3.45f, 4.3f))
    val ringR = ringBase * (1 + ringOut * 1.5f)
    val ringA = (1 - ringOut) * (if (t < 3.0f) 1f else 1 - 0.35f * seg(t, 3.0f, 3.4f))
    val pulse = sin(PI.toFloat() * seg(t, 2.85f, 3.35f)) * 0.6f
    if (t >= 1.2f) {
        ring(c, ringR, start, start + sweep * TAU, ringA * (1 + pulse * 0.5f), u * (1 - ringOut * 0.6f), if (sweep < 1f) 1f else 1 - conv)
        val details = floatArrayOf(0.18f, 0.52f, 0.81f)
        val sizes = floatArrayOf(1.0f, 0.8f, 0.9f)
        for (i in details.indices) {
            val passed = seg(sweep, details[i], details[i] + 0.08f)
            val twinkle = 0.6f + 0.4f * sin(t * 5 + i * 2)
            val sa = start + details[i] * TAU
            sparkle(Offset(c.x + cos(sa) * ringR, c.y + sin(sa) * ringR), 3.2f * u * sizes[i], passed * twinkle * ringA)
        }
    }

    // Orb.
    val born = easeOut(seg(t, 0.05f, 0.9f))
    val absorb = easeIn(seg(t, 2.85f, 3.15f))
    orb(orbPos, (4 + 8 * born) * u * (1 + absorb * 0.8f), born * (1 - absorb))

    // Play symbol born from the light.
    val triIn = seg(t, 2.85f, 3.3f)
    val triOut = easeIn(seg(t, 3.45f, 3.72f))
    val triA = easeOut(triIn) * (1 - triOut)
    glow(c, (140 + 160 * pulse) * u, Violet, 0.55f * max(pulse, triA * 0.6f))
    playTriangle(Offset(c.x + 4 * u, c.y), 112 * u * (0.35f + 0.65f * easeOutBack(triIn)) * (1 - 0.35f * triOut), triA)

    // Logo: slow, soft reveal.
    val lg = easeInOut(seg(t, 3.6f, 5.0f))
    val lgA = sin(seg(t, 3.6f, 4.9f) * PI.toFloat() / 2) * contentAlpha
    logo(c, u, lgA, (1 - lg) * 26 * u, 0.95f + 0.05f * lg, mark, textMeasurer, wordStyle)

    // Startup still running after the logo: small, quiet spinner below it.
    val waitA = seg(holdTime, OrbitalTimeline.LOGO_SETTLED + 0.6f, OrbitalTimeline.LOGO_SETTLED + 1.0f)
    if (waitA > 0f) {
        val sp = Offset(c.x, c.y + 130 * u)
        val r = 13 * u
        val from = elapsed * 5.2f
        arc(sp, r, from, from + PI.toFloat() * 0.6f, Lilac.copy(alpha = 0.8f * waitA), 2.6f * u)
    }
}
