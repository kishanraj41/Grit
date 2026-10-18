package com.kishan.preptracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ScreenHeader(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                "GRIT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun Stat(label: String, value: String, sub: String = "", modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun RecCard(title: String, why: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(12.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (why.isNotEmpty()) Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                action?.invoke()
            }
        }
    }
}

/**
 * Date-linear chart (x = calendar day, so gaps are real). Tap or drag to read a point.
 * Overlay (trend) is drawn on the same date axis as the raw series.
 */
@Composable
fun LineChart(
    points: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier,
    ref: Double? = null,
    overlay: List<Pair<LocalDate, Double>> = emptyList(),
    bars: Boolean = false,
    unit: String = "",
    formatValue: (Double) -> String = { v ->
        if (abs(v - v.roundToInt()) < 1e-6) v.roundToInt().toString() else "%.1f".format(v)
    },
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val refColor = MaterialTheme.colorScheme.tertiary
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val dateFmt = DateTimeFormatter.ofPattern("d MMM")
    if (points.isEmpty()) {
        Text("No data yet", style = MaterialTheme.typography.bodySmall, modifier = modifier.padding(12.dp))
        return
    }
    val all = points + overlay
    val x0 = all.minOf { it.first.toEpochDay() }.toDouble()
    val x1 = maxOf(all.maxOf { it.first.toEpochDay() }.toDouble(), x0 + 1)
    var y0 = all.minOf { it.second }
    var y1 = all.maxOf { it.second }
    if (ref != null) { y0 = minOf(y0, ref); y1 = maxOf(y1, ref) }
    if (y1 - y0 < 1e-6) { y0 -= 1; y1 += 1 }
    val padY = (y1 - y0) * 0.08
    y0 -= padY; y1 += padY
    var selected by remember(points) { mutableStateOf(points.last()) }
    var boxW by remember { mutableStateOf(1f) }

    fun pick(xPx: Float) {
        val t = (xPx / boxW).toDouble().coerceIn(0.0, 1.0)
        val day = x0 + t * (x1 - x0)
        selected = points.minBy { abs(it.first.toEpochDay() - day) }
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(52.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(formatValue(y1 - padY), style = MaterialTheme.typography.labelSmall, color = axis, maxLines = 1)
                Spacer(Modifier.height(56.dp))
                Text(formatValue(y0 + padY), style = MaterialTheme.typography.labelSmall, color = axis, maxLines = 1)
            }
            Box(
                Modifier.weight(1f).height(168.dp).onSizeChanged { boxW = it.width.toFloat() }
                    .pointerInput(points) {
                        detectTapGestures { pick(it.x) }
                    }
                    .pointerInput(points) {
                        detectDragGestures { change, _ -> pick(change.position.x) }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val l = 4f; val r = size.width - 4f; val t = 8f; val b = size.height - 8f
                    fun X(d: LocalDate) = l + ((d.toEpochDay() - x0) / (x1 - x0) * (r - l)).toFloat()
                    fun Y(v: Double) = (t + (1 - (v - y0) / (y1 - y0)) * (b - t)).toFloat()
                    if (ref != null) drawLine(refColor, Offset(l, Y(ref)), Offset(r, Y(ref)), strokeWidth = 2f)
                    if (bars) {
                        val bw = maxOf(3f, (r - l) / maxOf(30.0, x1 - x0).toFloat())
                        points.forEach { drawRect(primary.copy(alpha = 0.75f), Offset(X(it.first) - bw / 2, Y(it.second)), androidx.compose.ui.geometry.Size(bw, Y(y0) - Y(it.second))) }
                    } else if (overlay.isNotEmpty()) {
                        points.forEach { drawCircle(muted, 4.5f, Offset(X(it.first), Y(it.second))) }
                    } else {
                        val p = Path()
                        points.forEachIndexed { i, pt -> if (i == 0) p.moveTo(X(pt.first), Y(pt.second)) else p.lineTo(X(pt.first), Y(pt.second)) }
                        drawPath(p, primary, style = Stroke(width = 4f))
                        points.forEach { drawCircle(primary, 3.5f, Offset(X(it.first), Y(it.second))) }
                    }
                    if (overlay.isNotEmpty()) {
                        val p = Path()
                        overlay.forEachIndexed { i, pt -> if (i == 0) p.moveTo(X(pt.first), Y(pt.second)) else p.lineTo(X(pt.first), Y(pt.second)) }
                        drawPath(p, primary, style = Stroke(width = 4f))
                    }
                    val sel = selected
                    drawLine(primary.copy(alpha = 0.45f), Offset(X(sel.first), t), Offset(X(sel.first), b), strokeWidth = 2f)
                    drawCircle(primary, 7f, Offset(X(sel.first), Y(sel.second)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 52.dp)) {
            Text(points.first().first.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = axis)
            Spacer(Modifier.weight(1f))
            Text(points.last().first.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = axis)
        }
        Text(
            selected.first.format(dateFmt) + " · " + formatValue(selected.second) + if (unit.isNotEmpty()) " $unit" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = primary,
            modifier = Modifier.padding(start = 52.dp, top = 4.dp),
        )
    }
}

@Composable
fun RestTimerBar(left: Int, total: Int, onStop: () -> Unit, onPlus: () -> Unit) {
    if (total <= 0) return
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (left > 0) "Rest" else "Go", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("%d:%02d".format(left / 60, left % 60), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onPlus) { Text("+30 s") }
                TextButton(onClick = onStop) { Text("Skip") }
            }
            LinearProgressIndicator(
                progress = { if (total > 0) left.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
