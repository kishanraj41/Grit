package com.kishan.preptracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.kishan.preptracker.domain.MuscleState

/**
 * Original Grit front/back schematic. Regions match [com.kishan.preptracker.domain.Muscles.ORDER].
 * Intensity is fatigue (recover) or 7-day hard-set dose.
 */
@Composable
fun MuscleMap(muscles: List<MuscleState>, modifier: Modifier = Modifier) {
    var side by remember { mutableStateOf("front") }
    var paint by remember { mutableStateOf("fatigue") }
    val byId = remember(muscles) { muscles.associateBy { it.id } }
    val ink = MaterialTheme.colorScheme.primary
    val mute = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = side == "front", onClick = { side = "front" }, label = { Text("Front") })
            FilterChip(selected = side == "back", onClick = { side = "back" }, label = { Text("Back") })
            FilterChip(selected = paint == "fatigue", onClick = { paint = "fatigue" }, label = { Text("Fatigue") })
            FilterChip(selected = paint == "dose", onClick = { paint = "dose" }, label = { Text("Dose") })
        }
        Canvas(Modifier.fillMaxWidth().height(280.dp)) {
            val w = size.width
            val h = size.height
            fun t(x: Float, y: Float, bw: Float, bh: Float, id: String) {
                val m = byId[id]
                val a = when {
                    m == null -> 0.12f
                    paint == "dose" -> (m.volume7 / 16.0).toFloat().coerceIn(0.12f, 1f)
                    else -> (m.fatigue / 0.7).toFloat().coerceIn(0.12f, 1f)
                }
                drawRoundRect(
                    color = ink.copy(alpha = a),
                    topLeft = Offset(x * w, y * h),
                    size = Size(bw * w, bh * h),
                    cornerRadius = CornerRadius(8f, 8f),
                )
            }
            drawRoundRect(mute.copy(alpha = 0.35f), Offset(0.34f * w, 0.02f * h), Size(0.32f * w, 0.96f * h), CornerRadius(48f, 48f))
            if (side == "front") {
                t(0.42f, 0.02f, 0.16f, 0.08f, "traps")
                t(0.28f, 0.10f, 0.16f, 0.08f, "delts")
                t(0.56f, 0.10f, 0.16f, 0.08f, "delts")
                t(0.36f, 0.18f, 0.28f, 0.14f, "chest")
                t(0.24f, 0.20f, 0.10f, 0.16f, "biceps")
                t(0.66f, 0.20f, 0.10f, 0.16f, "biceps")
                t(0.22f, 0.36f, 0.08f, 0.12f, "forearms")
                t(0.70f, 0.36f, 0.08f, 0.12f, "forearms")
                t(0.32f, 0.32f, 0.08f, 0.10f, "serratus")
                t(0.60f, 0.32f, 0.08f, 0.10f, "serratus")
                t(0.40f, 0.34f, 0.20f, 0.14f, "abs")
                t(0.32f, 0.36f, 0.08f, 0.12f, "obliques")
                t(0.60f, 0.36f, 0.08f, 0.12f, "obliques")
                t(0.34f, 0.50f, 0.14f, 0.22f, "quads")
                t(0.52f, 0.50f, 0.14f, 0.22f, "quads")
                t(0.44f, 0.54f, 0.12f, 0.12f, "adductors")
                t(0.40f, 0.48f, 0.20f, 0.06f, "hip-flexors")
                t(0.36f, 0.74f, 0.12f, 0.18f, "shins")
                t(0.52f, 0.74f, 0.12f, 0.18f, "shins")
            } else {
                t(0.38f, 0.04f, 0.24f, 0.10f, "traps")
                t(0.28f, 0.10f, 0.16f, 0.08f, "delts")
                t(0.56f, 0.10f, 0.16f, 0.08f, "delts")
                t(0.34f, 0.16f, 0.32f, 0.16f, "upper-back")
                t(0.24f, 0.20f, 0.10f, 0.16f, "triceps")
                t(0.66f, 0.20f, 0.10f, 0.16f, "triceps")
                t(0.22f, 0.36f, 0.08f, 0.12f, "forearms")
                t(0.70f, 0.36f, 0.08f, 0.12f, "forearms")
                t(0.38f, 0.32f, 0.24f, 0.12f, "lower-back")
                t(0.34f, 0.46f, 0.32f, 0.12f, "glutes")
                t(0.34f, 0.58f, 0.14f, 0.18f, "hams")
                t(0.52f, 0.58f, 0.14f, 0.18f, "hams")
                t(0.36f, 0.78f, 0.12f, 0.16f, "calves")
                t(0.52f, 0.78f, 0.12f, 0.16f, "calves")
            }
        }
        Text("Orange fills from your Train sets. Half-life is longer on legs and posterior chain.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
