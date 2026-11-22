// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ybblab.jiumi.MonitorController.Range
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.Activity
import com.ybblab.jiumi.data.Behavior
import com.ybblab.jiumi.data.Hits
import com.ybblab.jiumi.ui.theme.ChartPurple
import com.ybblab.jiumi.ui.theme.ChartPurpleDark
import com.ybblab.jiumi.ui.theme.ChartPurpleFaint
import com.ybblab.jiumi.ui.theme.ChartPurpleFaintDark
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.ui.theme.LocalAppIsDark
import java.text.SimpleDateFormat
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The one accent of the charts: a misty purple, brighter on the dark canvas. */
val chartColor: Color @Composable @ReadOnlyComposable get() = if (LocalAppIsDark.current) ChartPurpleDark else ChartPurple

/**
 * Every word and figure around a chart, in one size: the app's small text, with figures in fixed
 * widths. What matters is set apart by weight and ink, never by size.
 */
private val chartLabel: TextStyle @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")

/**
 * Records over time as one smooth line over a fill that fades downwards. A slot that is null (not
 * watched, or still to come) breaks the line, and a dashed stroke bridges the gap. With [references],
 * the busiest slot is marked ([peakLabel], else its count) and a faint dashed line marks the average, so the line can be read
 * without touching it; [onSelect] lets a tap or a horizontal slide pick a slot. [growing] is the slot
 * under way: the point just before it is marked, with a dashed run to it, and only that far.
 */
@Composable
fun ActivityCurve(
    values: List<Int?>,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    references: Boolean = true,
    growing: Int? = null,
    peakLabel: String? = null,
    unrecorded: List<Boolean> = emptyList(),
    unrecordedLabel: String = "",
    pendingLabel: String? = null,
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null
) {
    val color = chartColor
    val dark = LocalAppIsDark.current
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val peakStyle = chartLabel.copy(color = color, fontWeight = FontWeight.SemiBold)
    val blankStyle = chartLabel.copy(color = Ink.tertiary)
    val n = values.size
    val current by rememberUpdatedState(selected)
    val pick by rememberUpdatedState(onSelect)
    val gestures = if (onSelect == null || n == 0) Modifier else Modifier
        .pointerInput(n) {
            detectTapGestures { o -> val i = slotAt(o.x, size.width.toFloat(), n); pick?.invoke(if (current == i) null else i) }
        }
        .pointerInput(n) {
            detectHorizontalDragGestures { change, _ -> change.consume(); pick?.invoke(slotAt(change.position.x, size.width.toFloat(), n)) }
        }
    Canvas(modifier.fillMaxWidth().height(height).then(gestures)) {
        if (n == 0) return@Canvas
        val top = if (references) 16.dp.toPx() else 4.dp.toPx()
        val bottom = size.height - 1f
        // Nothing to draw yet: the baseline, a dashed line where the curve will run, and when it will.
        if (pendingLabel != null) {
            drawLine(ink.copy(alpha = 0.08f), Offset(0f, bottom), Offset(size.width, bottom), 1f)
            val mid = (top + bottom) / 2
            drawLine(color.copy(alpha = 0.35f), Offset(0f, mid), Offset(size.width, mid), 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            val text = measurer.measure(pendingLabel, blankStyle)
            val pad = 8.dp.toPx()
            drawRect(surface, Offset((size.width - text.size.width) / 2 - pad, mid - text.size.height / 2),
                androidx.compose.ui.geometry.Size(text.size.width + 2 * pad, text.size.height.toFloat()))
            drawText(text, topLeft = Offset((size.width - text.size.width) / 2, mid - text.size.height / 2))
            return@Canvas
        }
        val known = values.filterNotNull()
        val max = ((known.maxOrNull() ?: 0) * 1.12f).coerceAtLeast(1f)
        fun x(i: Int) = if (n == 1) size.width / 2 else i * size.width / (n - 1)
        fun y(v: Int) = bottom - (v / max) * (bottom - top)

        // Stretches with no record that were not watched: a faint field named once, so an empty
        // stretch reads as not measured rather than as nothing drawn.
        var blankFrom = -1
        for (i in 0..n) {
            val blank = i < n && unrecorded.getOrElse(i) { false }
            if (blank && blankFrom < 0) blankFrom = i
            if (!blank && blankFrom >= 0) {
                val half = if (n <= 1) size.width / 2 else size.width / (n - 1) / 2
                val left = (x(blankFrom) - half).coerceAtLeast(0f)
                val right = (x(i - 1) + half).coerceAtMost(size.width)
                drawRect(ink.copy(alpha = 0.035f), Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top))
                if (unrecordedLabel.isNotEmpty() && right - left > 64.dp.toPx()) {
                    val text = measurer.measure(unrecordedLabel, blankStyle)
                    drawText(text, topLeft = Offset((left + right - text.size.width) / 2, (top + bottom - text.size.height) / 2))
                }
                blankFrom = -1
            }
        }
        drawLine(ink.copy(alpha = 0.08f), Offset(0f, bottom), Offset(size.width, bottom), 1f)
        val segments = ArrayList<List<Offset>>()
        var run = ArrayList<Offset>()
        values.forEachIndexed { i, v ->
            if (v == null) { if (run.isNotEmpty()) segments += run; run = ArrayList() } else run += Offset(x(i), y(v))
        }
        if (run.isNotEmpty()) segments += run

        if (references && known.size > 1) {
            val avg = known.average().toFloat()
            val ay = bottom - (avg / max) * (bottom - top)
            // Over the stretch that has records, not across one that has none.
            val from = x(values.indexOfFirst { it != null })
            val to = x(values.indexOfLast { it != null })
            drawLine(ink.copy(alpha = 0.14f), Offset(from, ay), Offset(to, ay), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        }

        val fade = Brush.verticalGradient(listOf(color.copy(alpha = if (dark) 0.30f else 0.20f), color.copy(alpha = 0f)), top, bottom)
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        segments.forEach { points ->
            if (points.size == 1) { drawCircle(color, 1.8.dp.toPx(), points[0]); return@forEach }
            val line = smooth(points)
            val area = Path().apply { addPath(line); lineTo(points.last().x, bottom); lineTo(points.first().x, bottom); close() }
            drawPath(area, fade)
            drawPath(line, color, style = stroke)
        }
        segments.zipWithNext().forEach { (a, b) ->
            drawLine(ink.copy(alpha = 0.25f), a.last(), b.first(), 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        }

        if (references && selected == null && known.isNotEmpty()) {
            val i = values.indexOf(known.max())
            val p = Offset(x(i), y(known.max()))
            drawCircle(color, 2.6.dp.toPx(), p)
            val text = measurer.measure(peakLabel ?: formatCount(known.max().toLong()), peakStyle)
            val left = (p.x - text.size.width / 2).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(left, p.y - text.size.height - 4.dp.toPx()))
        }
        // The slot under way, not drawn yet: a faint dashed run from the last point to now.
        // Never across a stretch without records, nor into hours still to come.
        growing?.takeIf { it in 1 until n && values[it - 1] != null }?.let { i ->
            val p = Offset(x(i - 1), y(values[i - 1]!!))
            drawLine(color.copy(alpha = 0.35f), p, Offset(x(i), p.y), 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            drawCircle(color.copy(alpha = 0.18f), 6.dp.toPx(), p)
            drawCircle(color, 2.8.dp.toPx(), p)
        }
        selected?.takeIf { it in 0 until n }?.let { i ->
            drawLine(ink.copy(alpha = 0.3f), Offset(x(i), 0f), Offset(x(i), bottom), 1f)
            values[i]?.let { v ->
                drawCircle(surface, 3.6.dp.toPx(), Offset(x(i), y(v)))
                drawCircle(color, 3.6.dp.toPx(), Offset(x(i), y(v)), style = Stroke(1.6.dp.toPx()))
            }
        }
    }
}

private fun slotAt(x: Float, width: Float, n: Int): Int =
    if (n <= 1) 0 else (x / width * (n - 1)).roundToInt().coerceIn(0, n - 1)

/** A Catmull-Rom curve through [p]: smooth, and it passes through every point. */
private fun smooth(p: List<Offset>): Path = Path().apply {
    moveTo(p[0].x, p[0].y)
    for (i in 0 until p.size - 1) {
        val p0 = p[maxOf(i - 1, 0)]
        val p1 = p[i]
        val p2 = p[i + 1]
        val p3 = p[minOf(i + 2, p.size - 1)]
        // Keep the curve from swinging past its points' height range.
        val c1y = (p1.y + (p2.y - p0.y) / 6).coerceIn(minOf(p1.y, p2.y), maxOf(p1.y, p2.y))
        val c2y = (p2.y - (p3.y - p1.y) / 6).coerceIn(minOf(p1.y, p2.y), maxOf(p1.y, p2.y))
        cubicTo(p1.x + (p2.x - p0.x) / 6, c1y, p2.x - (p3.x - p1.x) / 6, c2y, p2.x, p2.y)
    }
}

/** A card's title as a figure: what it is, how much in bold, then its unit. */
@Composable
private fun HeaderFigure(label: String, value: String?, unit: String, modifier: Modifier = Modifier) {
    val strong = SpanStyle(color = Ink.primary, fontWeight = FontWeight.SemiBold)
    Text(
        buildAnnotatedString {
            append(label)
            if (value != null) {
                append(' ')
                withStyle(strong) { append(value) }
                append(unit)
            }
        },
        style = chartLabel, color = Ink.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() }
    )
}

/**
 * A range told as one card: its title line (the total and the span, or the picked slot), the curve,
 * the time (or what the picked slot raised, on the same line so nothing moves), then a line of
 * figures in even columns: the average, the busiest slot, and how many were in the background.
 */
@Composable
fun ActivityCard(
    activity: Activity, range: Range, live: Boolean,
    selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier
) {
    val values = activity.values
    // A pick can outlast its slots: the range was rebuilt with fewer, longer ones.
    @Suppress("NAME_SHADOWING")
    val selected = selected?.takeIf { it in values.indices }
    val known = values.withIndex().filter { it.value != null }
    val pickedValue = selected?.let { values[it] }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderFigure(
                selected?.let { slotLabel(activity, it) } ?: stringResource(R.string.chart_total),
                when {
                    selected == null -> formatCount(activity.total.toLong())
                    pickedValue == null -> null
                    else -> formatCount(pickedValue.toLong())
                },
                stringResource(R.string.chart_unit_times)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    selected == null -> spanLabel(activity, range, live)
                    activity.unrecorded(selected) -> stringResource(R.string.chart_not_monitored)
                    else -> ""
                },
                style = chartLabel, color = Ink.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        // While the range is still growing, its last slot is not over yet: drawn, it would always dip.
        // The line ends at the last whole slot; the totals above and below still count it.
        val step = activity.buckets.stepMs
        val plotted = values.mapIndexed { i, v -> v?.takeIf { !live || activity.startOf(i) + step <= activity.until } }
        val drawn = plotted.count { it != null }
        val peak = plotted.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }?.index
        // Too few whole slots for a line yet: say when one will be there.
        val pending = if (range == Range.SESSION && live && drawn < 2 && step < Behavior.DAY) {
            val ready = activity.startOf(0) + 2 * step
            val minutes = ((ready - activity.until + 59_999) / 60_000).coerceAtLeast(1)
            stringResource(R.string.chart_collecting, minutes.toInt())
        } else null
        val growing = values.indices.firstOrNull { values[it] != null && plotted[it] == null }
        ActivityCurve(if (pending != null) List(values.size) { null } else plotted, height = 72.dp, growing = growing,
            peakLabel = peak?.let { whenLabel(activity, range, it) },
            unrecorded = List(activity.size) { activity.unrecorded(it) }, unrecordedLabel = stringResource(R.string.chart_unrecorded),
            pendingLabel = pending, selected = selected, onSelect = onSelect)
        // A picked slot's note takes the axis's line: the long names need the whole width.
        val note = selected?.takeIf { values[it] != null }?.let { activity.hits[it] }
        Box(Modifier.fillMaxWidth()) {
            AxisLabels(axis(activity, range, live), Modifier.alpha(if (note == null) 1f else 0f))
            if (note != null) Text(attentionNote(note), style = chartLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (note.total > 0) chartColor else Ink.tertiary, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(14.dp))
        // The figures sit apart from the chart, each on a quiet tile of the same size, centred.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            figures(activity).forEach { (name, value, unit) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ink.fill).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(name, style = chartLabel, color = Ink.tertiary, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildAnnotatedString {
                            append(value)
                            if (unit.isNotEmpty()) withStyle(SpanStyle(color = Ink.tertiary, fontWeight = FontWeight.Normal)) { append(unit) }
                        },
                        style = chartLabel, fontWeight = FontWeight.SemiBold, color = Ink.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Labels at fractions of the width, each centred on its slot and kept inside the edges. */
@Composable
private fun AxisLabels(labels: List<Pair<Float, String>>, modifier: Modifier = Modifier) {
    val style = chartLabel
    val color = Ink.tertiary
    Layout(
        content = { labels.forEach { (_, text) -> Text(text, style = style, color = color, maxLines = 1, softWrap = false) } },
        modifier = modifier.fillMaxWidth().padding(top = 6.dp)
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        // On one baseline: digits and Chinese sit differently in boxes of their own.
        val baseline = placeables.maxOfOrNull { it[FirstBaseline] } ?: 0
        layout(width, placeables.maxOfOrNull { baseline - it[FirstBaseline] + it.height } ?: 0) {
            placeables.forEachIndexed { i, p ->
                val x = (labels[i].first * width - p.width / 2f).roundToInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                p.place(x, baseline - p[FirstBaseline])
            }
        }
    }
}

/** What a slot covers: its time of day under a day, else its date and weekday. */
@Composable
fun slotLabel(activity: Activity, i: Int): String {
    val context = LocalContext.current
    val start = activity.startOf(i)
    return if (activity.buckets.stepMs < Behavior.DAY) {
        val f = DateFormat.getTimeFormat(context)
        f.format(start) + "–" + f.format(start + activity.buckets.stepMs)
    } else DateUtils.formatDateTime(context, start, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE or
        DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
}

@Composable
private fun spanLabel(activity: Activity, range: Range, live: Boolean): String {
    val context = LocalContext.current
    val from = activity.startOf(0)
    val time = DateFormat.getTimeFormat(context)
    val end = if (live) stringResource(R.string.monitor_now) else time.format(activity.until)
    return when (range) {
        Range.SESSION -> (if (DateUtils.isToday(from)) time.format(from) else shortDateTime(from)) + " – " + end
        Range.TODAY -> time.format(from) + " – " + end
        Range.WEEK, Range.MONTH -> numericDate(from) + " – " + numericDate(activity.until)
    }
}

@Composable
private fun numericDate(t: Long): String = DateUtils.formatDateTime(LocalContext.current, t,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE or DateUtils.FORMAT_NO_YEAR)

/** A few times under the curve, at the slots they name. */
@Composable
private fun axis(activity: Activity, range: Range, live: Boolean): List<Pair<Float, String>> {
    val context = LocalContext.current
    val n = activity.size
    fun at(i: Int) = if (n <= 1) 0f else i.toFloat() / (n - 1)
    val time = DateFormat.getTimeFormat(context)
    return when (range) {
        Range.TODAY -> listOf(0, 6, 12, 18, n - 1).map { h -> at(h) to h.toString() }
        Range.WEEK -> {
            val locale = LocalConfiguration.current.locales[0]
            val weekday = SimpleDateFormat(if (locale.language == "zh") "EEEEE" else "EEE", locale)
            (0 until n).map { at(it) to weekday.format(activity.startOf(it)) }
        }
        Range.MONTH -> listOf(0, n / 2, n - 1).map { at(it) to numericDate(activity.startOf(it)) }
        Range.SESSION -> if (activity.buckets.stepMs >= Behavior.DAY) listOf(0, n - 1).map { at(it) to numericDate(activity.startOf(it)) }
            else listOf(at(0) to time.format(activity.startOf(0)),
                1f to if (live) stringResource(R.string.monitor_now) else time.format(activity.until))
    }
}

/** When slot [i] was, in the fewest words: its weekday over a week, its date over days, else its time. */
@Composable
private fun whenLabel(activity: Activity, range: Range, i: Int): String = when {
    range == Range.WEEK -> SimpleDateFormat("EEE", LocalConfiguration.current.locales[0]).format(activity.startOf(i))
    activity.buckets.stepMs >= Behavior.DAY -> numericDate(activity.startOf(i))
    else -> DateFormat.getTimeFormat(LocalContext.current).format(activity.startOf(i))
}

/**
 * The figures, all amounts: the average per slot, the busiest slot's count (the curve marks when),
 * and how many were made while the app was not in front of the user, which the rest of the page does not show.
 */
@Composable
private fun figures(activity: Activity): List<Triple<String, String, String>> {
    val known = activity.values.filterNotNull()
    if (known.isEmpty()) return emptyList()
    val step = activity.buckets.stepMs
    val unit = when {
        step >= Behavior.DAY -> stringResource(R.string.chart_per_day)
        step == Behavior.HOUR -> stringResource(R.string.chart_per_hour)
        step == 60_000L -> stringResource(R.string.chart_per_minute)
        step > Behavior.HOUR -> stringResource(R.string.chart_per_hours, (step / Behavior.HOUR).toInt())
        else -> stringResource(R.string.chart_per_minutes, (step / 60_000).toInt())
    }
    return listOf(
        Triple(stringResource(R.string.chart_avg), formatCount((known.sum().toDouble() / known.size).roundToInt().toLong()), unit),
        Triple(stringResource(R.string.chart_peak), formatCount(known.max().toLong()), stringResource(R.string.chart_unit_times)),
        Triple(stringResource(R.string.chart_background), formatCount(activity.backgroundTotal.toLong()), stringResource(R.string.chart_unit_times))
    )
}

/** Cells of the attention grid: one a day over days; under a day, slots folded so there are at most 12. */
private fun attentionCells(activity: Activity): List<IntRange> {
    val per = if (activity.buckets.stepMs >= Behavior.DAY) 1 else ((activity.size + 11) / 12).coerceAtLeast(1)
    return (0 until activity.size step per).map { it..minOf(it + per, activity.size) - 1 }
}

/** Cells per row: thirty days in three rows of ten, a week in one, up to twelve shorter cells in rows of six. */
private fun columns(n: Int): Int = when {
    n > 12 -> (n + 2) / 3
    n > 7 -> 6
    else -> n
}

/**
 * The attention card's title line: how many items were raised ([count]), and with [key], what the
 * cells' shades mean.
 */
@Composable
fun AttentionHeader(count: Int, key: Boolean, modifier: Modifier = Modifier) {
    val color = chartColor
    val faint = if (LocalAppIsDark.current) ChartPurpleFaintDark else ChartPurpleFaint
    val empty = Ink.fill
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeaderFigure(stringResource(R.string.attention_title), if (count > 0) formatCount(count.toLong()) else null,
            stringResource(R.string.chart_unit_items), Modifier.weight(1f))
        if (key) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.chart_less), style = chartLabel, color = Ink.tertiary)
            listOf(0f, 0.15f, 0.45f, 1f).forEach { t ->
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(if (t == 0f) empty else lerp(faint, color, sqrt(t))))
            }
            Spacer(Modifier.width(3.dp))
            Text(stringResource(R.string.chart_more), style = chartLabel, color = Ink.tertiary)
        }
    }
}

/**
 * When something was raised for attention, as rows of cells over the range: thirty days in three rows
 * of ten, a week in one row, today in two rows of six two-hour cells. The deeper the purple, the more
 * was raised; grey, nothing; faintest, not watched. A tap names what a cell raised.
 */
@Composable
fun AttentionStrip(activity: Activity, modifier: Modifier = Modifier) {
    val cells = remember(activity) { attentionCells(activity) }
    // Kept while a running range refreshes; dropped when its cells become other stretches of time.
    var picked by remember(activity.buckets.start, activity.buckets.stepMs, cells.size) { mutableStateOf<Int?>(null) }
    val dark = LocalAppIsDark.current
    val color = chartColor
    val faint = if (dark) ChartPurpleFaintDark else ChartPurpleFaint
    val totals = cells.map { r -> r.sumOf { activity.hits[it].total } }
    val top = totals.maxOrNull()?.coerceAtLeast(1) ?: 1
    val unwatched = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.025f)
    val empty = Ink.fill
    fun tint(t: Float) = lerp(faint, color, sqrt(t))
    val shape = RoundedCornerShape(3.dp)
    Column(modifier.fillMaxWidth()) {
        val perRow = columns(cells.size).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.indices.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { i ->
                            val gapCell = cells[i].all { activity.values[it] == null }
                            Box(
                                Modifier.weight(1f).height(18.dp)
                                    .then(if (picked == i) Modifier.border(1.5.dp, color, shape) else Modifier)
                                    .padding(if (picked == i) 2.5.dp else 0.dp)
                                    .clip(shape)
                                    .background(when {
                                        gapCell -> unwatched
                                        totals[i] == 0 -> empty
                                        else -> tint(totals[i].toFloat() / top)
                                    })
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        picked = if (picked == i) null else i
                                    }
                            )
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
        }
        picked?.let { i ->
            val range = cells[i]
            val byItem = HashMap<String, Int>()
            val apps = HashSet<com.ybblab.jiumi.data.AppKey>()
            range.forEach { s -> activity.hits[s].byItem.forEach { (k, v) -> byItem.merge(k, v, Int::plus) }; apps += activity.hits[s].apps }
            val label = if (range.first == range.last) slotLabel(activity, range.first) else {
                val f = DateFormat.getTimeFormat(LocalContext.current)
                f.format(activity.startOf(range.first)) + "–" + f.format(activity.startOf(range.last) + activity.buckets.stepMs)
            }
            val note = if (range.all { activity.values[it] == null }) stringResource(R.string.chart_not_monitored)
                else attentionNote(Hits(byItem, apps))
            Text("$label  ·  $note", style = chartLabel, color = Ink.secondary, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** What a slot raised for attention, briefly: the items with their counts and how many apps, or that it raised nothing. */
@Composable
private fun attentionNote(hits: Hits): String {
    if (hits.total == 0) return stringResource(R.string.attention_slot_none)
    val top = hits.byItem.maxBy { it.value }
    val more = hits.byItem.size - 1
    return attentionName(top.key) + " " + formatCount(top.value.toLong()) + stringResource(R.string.chart_unit_times) +
        (if (more > 0) " +$more" else "") + " · " + stringResource(R.string.attention_slot_apps, hits.apps.size)
}
