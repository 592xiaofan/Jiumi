// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ybblab.jiumi.R
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.ui.theme.JiumiPrimary
import com.ybblab.jiumi.ui.theme.StatusSuccess
import kotlin.math.floor
import kotlin.math.sin

internal enum class PairingGuide { SPLIT, TILE }

/**
 * A looping sketch of a phone going through one way of pairing, so the steps beside it can be
 * followed at a glance: shapes only, no words, and hidden from screen readers, which read the steps.
 */
@Composable
internal fun PairingGuideAnimation(guide: PairingGuide, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "guide").animateFloat(
        0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    val ink = MaterialTheme.colorScheme.onSurface
    val colors = GuideColors(
        frame = ink.copy(alpha = 0.55f),
        screen = MaterialTheme.colorScheme.background,
        card = MaterialTheme.colorScheme.surfaceBright,
        ink = ink,
        line = ink.copy(alpha = 0.16f),
        faint = ink.copy(alpha = 0.08f),
        accent = JiumiPrimary,
        success = StatusSuccess
    )
    val cat = rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_pairing_tile))
    Box(
        modifier.fillMaxWidth().height(188.dp).background(Ink.fill, AppShapes.Control).clearAndSetSemantics {}
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val phoneHeight = size.height - 24.dp.toPx()
            val phoneWidth = phoneHeight * 0.5f
            val phone = Rect(Offset((size.width - phoneWidth) / 2, 12.dp.toPx()), Size(phoneWidth, phoneHeight))
            drawRoundRect(colors.frame, phone.topLeft, phone.size, CornerRadius(14.dp.toPx()),
                style = Stroke(2.dp.toPx()))
            val screen = phone.deflate(4.dp.toPx())
            val clip = Path().apply { addRoundRect(RoundRect(screen, CornerRadius(10.dp.toPx()))) }
            clipPath(clip) {
                drawRect(colors.screen, screen.topLeft, screen.size)
                when (guide) {
                    PairingGuide.SPLIT -> drawSplit(t, screen, colors)
                    PairingGuide.TILE -> drawTile(t, screen, colors, cat)
                }
            }
        }
    }
}

private class GuideColors(
    val frame: Color, val screen: Color, val card: Color, val ink: Color, val line: Color,
    val faint: Color, val accent: Color, val success: Color
)

/** Split screen: the dialog shares the screen with Jiumi, and the code is typed in the lower half. */
private fun DrawScope.drawSplit(t: Float, screen: Rect, c: GuideColors) {
    val split = ease(phase(t, 0.10f, 0.26f)) * (1 - ease(phase(t, 0.90f, 1f)))
    val divider = lerp(screen.bottom, screen.center.y, split)
    drawDialog(Rect(screen.left, screen.top, screen.right, divider), c)
    if (split <= 0f) return
    val lower = Rect(screen.left, divider, screen.right, screen.bottom)
    drawRect(c.screen, lower.topLeft, lower.size)
    val handle = 3.dp.toPx()
    drawLine(c.line, Offset(screen.left, divider), Offset(screen.right, divider), 1.dp.toPx())
    drawLine(c.frame, Offset(screen.center.x - 8.dp.toPx(), divider), Offset(screen.center.x + 8.dp.toPx(), divider),
        handle, cap = StrokeCap.Round)
    drawPad(lower.deflate(6.dp.toPx()), t, typing = phase(t, 0.34f, 0.70f), done = ease(phase(t, 0.74f, 0.82f)), c)
}

/**
 * Quick Settings tile: the shade comes down over the dialog, Jiumi's tile is tapped, and its keypad
 * rises from the bottom while the dialog stays open behind it.
 */
private fun DrawScope.drawTile(t: Float, screen: Rect, c: GuideColors, cat: VectorPainter) {
    drawDialog(screen, c)
    val shade = ease(phase(t, 0.08f, 0.22f)) * (1 - ease(phase(t, 0.40f, 0.50f)))
    val sheet = ease(phase(t, 0.46f, 0.58f)) * (1 - ease(phase(t, 0.92f, 1f)))
    val dim = maxOf(shade, sheet)
    if (dim > 0f) drawRect(Color.Black.copy(alpha = 0.22f * dim), screen.topLeft, screen.size)

    if (shade > 0f) {
        val height = screen.height * 0.56f
        val panel = Rect(screen.left, screen.top - height * (1 - shade), screen.right, screen.top - height * (1 - shade) + height)
        drawRoundRect(c.card, panel.topLeft, panel.size, CornerRadius(8.dp.toPx()))
        val pad = 6.dp.toPx()
        val gap = 4.dp.toPx()
        val tileWidth = (panel.width - pad * 2 - gap) / 2
        val tileHeight = (panel.height - pad * 2 - gap * 2) / 3
        repeat(2) { row ->
            repeat(2) { col ->
                val x = panel.left + pad + col * (tileWidth + gap)
                val y = panel.top + pad + row * (tileHeight + gap)
                drawRoundRect(c.faint, Offset(x, y), Size(tileWidth, tileHeight), CornerRadius(tileHeight / 2))
                drawCircle(c.line, tileHeight * 0.18f, Offset(x + tileHeight / 2, y + tileHeight / 2))
            }
        }
        // Jiumi's tile: a full-width row with the cat, outlined until it is tapped, then filled.
        val jiumi = Rect(Offset(panel.left + pad, panel.top + pad + 2 * (tileHeight + gap)),
            Size(panel.width - pad * 2, tileHeight))
        val on = ease(phase(t, 0.30f, 0.36f))
        drawRoundRect(lerp(c.accent.copy(alpha = 0.12f), c.accent, on), jiumi.topLeft, jiumi.size, CornerRadius(tileHeight / 2))
        drawRoundRect(c.accent, jiumi.topLeft, jiumi.size, CornerRadius(tileHeight / 2), style = Stroke(1.dp.toPx()))
        val icon = tileHeight * 0.62f
        drawCat(cat, Offset(jiumi.left + tileHeight * 0.3f, jiumi.center.y - icon / 2), icon, lerp(c.accent, Color.White, on))
        drawRoundRect(lerp(c.accent, Color.White, on).copy(alpha = 0.7f),
            Offset(jiumi.left + tileHeight * 0.3f + icon + 4.dp.toPx(), jiumi.center.y - 1.5.dp.toPx()),
            Size(jiumi.width * 0.38f, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
        drawFinger(Offset(jiumi.center.x + jiumi.width * 0.18f, jiumi.center.y), pulse(phase(t, 0.26f, 0.38f)), c)
    }

    if (sheet > 0f) {
        val height = screen.height * 0.6f
        val card = Rect(screen.left + 3.dp.toPx(), screen.bottom - height * sheet,
            screen.right - 3.dp.toPx(), screen.bottom - height * sheet + height)
        drawRoundRect(c.card, card.topLeft, card.size, CornerRadius(10.dp.toPx()))
        val icon = 9.dp.toPx()
        drawCat(cat, Offset(card.left + 6.dp.toPx(), card.top + 5.dp.toPx()), icon, c.accent)
        val body = Rect(card.left, card.top + icon + 6.dp.toPx(), card.right, card.bottom).deflate(5.dp.toPx())
        drawPad(body, t, typing = phase(t, 0.60f, 0.82f), done = ease(phase(t, 0.84f, 0.90f)), c)
    }
}

/** The system's pairing dialog: a card with a title and the six-digit code as bars. */
private fun DrawScope.drawDialog(area: Rect, c: GuideColors) {
    if (area.height < 8.dp.toPx()) return
    val width = area.width * 0.84f
    val height = minOf(area.height * 0.8f, width * 0.62f)
    val card = Rect(Offset(area.center.x - width / 2, area.center.y - height / 2), Size(width, height))
    drawRoundRect(c.card, card.topLeft, card.size, CornerRadius(6.dp.toPx()))
    drawRoundRect(c.line, card.topLeft, card.size, CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
    val bar = 3.dp.toPx()
    drawRoundRect(c.ink.copy(alpha = 0.45f), Offset(card.left + 6.dp.toPx(), card.top + height * 0.2f),
        Size(width * 0.45f, bar), CornerRadius(bar / 2))
    val gap = 2.dp.toPx()
    val digit = (width * 0.7f - gap * 5) / 6
    repeat(6) { i ->
        drawRoundRect(c.ink, Offset(card.left + 6.dp.toPx() + i * (digit + gap), card.top + height * 0.52f),
            Size(digit, bar * 1.6f), CornerRadius(1.dp.toPx()))
    }
}

/** Jiumi's keypad: six boxes filling one by one as keys are tapped, then a check once paired. */
private fun DrawScope.drawPad(area: Rect, t: Float, typing: Float, done: Float, c: GuideColors) {
    if (area.height < 12.dp.toPx()) return
    val gap = 2.5.dp.toPx()
    val box = minOf((area.width - gap * 5) / 6, area.height * 0.2f)
    val rowLeft = area.center.x - (box * 6 + gap * 5) / 2
    val typed = if (typing >= 1f) 6 else floor(typing * 6).toInt()
    repeat(6) { i ->
        val topLeft = Offset(rowLeft + i * (box + gap), area.top)
        val filled = i < typed
        if (filled) drawRoundRect(c.faint, topLeft, Size(box, box), CornerRadius(2.dp.toPx()))
        val edge = when {
            done > 0f -> lerp(c.line, c.success, done)
            i == typed -> c.accent
            else -> c.line
        }
        drawRoundRect(edge, topLeft, Size(box, box), CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()))
        if (filled) drawCircle(c.ink, box * 0.14f, Offset(topLeft.x + box / 2, topLeft.y + box / 2))
    }
    val keysTop = area.top + box + 5.dp.toPx()
    val keyHeight = (area.bottom - keysTop - gap * 3) / 4
    val keyWidth = (area.width - gap * 2) / 3
    val pressed = if (typing in 0f..<1f && typing > 0f) KEYS[typed] else -1
    val within = (typing * 6) - typed
    for (key in 0 until 12) {
        if (key == 9) continue
        val origin = Offset(area.left + (key % 3) * (keyWidth + gap), keysTop + (key / 3) * (keyHeight + gap))
        val press = if (key == pressed) pulse(within) else 0f
        drawRoundRect(lerp(c.faint, c.accent.copy(alpha = 0.35f), press), origin, Size(keyWidth, keyHeight),
            CornerRadius(3.dp.toPx()))
        if (key == pressed) drawFinger(Offset(origin.x + keyWidth / 2, origin.y + keyHeight / 2), press, c)
    }
    if (done > 0f) {
        val center = Offset(area.center.x, keysTop + (area.bottom - keysTop) / 2)
        val radius = 13.dp.toPx() * done
        drawCircle(c.success, radius, center)
        val s = radius * 0.45f
        val check = Path().apply {
            moveTo(center.x - s, center.y)
            lineTo(center.x - s * 0.25f, center.y + s * 0.7f)
            lineTo(center.x + s, center.y - s * 0.6f)
        }
        drawPath(check, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawFinger(at: Offset, press: Float, c: GuideColors) {
    if (press <= 0f) return
    drawCircle(c.ink.copy(alpha = 0.16f * press), 9.dp.toPx() + 5.dp.toPx() * (1 - press), at)
    drawCircle(c.ink.copy(alpha = 0.32f * press), 5.dp.toPx(), at)
}

private fun DrawScope.drawCat(cat: VectorPainter, topLeft: Offset, size: Float, color: Color) {
    translate(topLeft.x, topLeft.y) {
        with(cat) { draw(Size(size, size), colorFilter = ColorFilter.tint(color)) }
    }
}

/** Where 0..1 falls between [from] and [to] of the loop. */
private fun phase(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun ease(x: Float) = FastOutSlowInEasing.transform(x)

/** Up and back down over 0..1, for a tap. */
private fun pulse(x: Float) = if (x <= 0f || x >= 1f) 0f else sin(x * Math.PI).toFloat()

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/** The keys tapped for the sketch's code, as 0-based positions on a phone keypad. */
private val KEYS = intArrayOf(3, 7, 8, 6, 3, 7)

private const val LOOP_MS = 6_400
