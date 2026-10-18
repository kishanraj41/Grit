package com.kishan.preptracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.kishan.preptracker.domain.Load
import com.kishan.preptracker.domain.Plates

@Composable
fun PlateCalculatorDialog(loadKg: Double, onDismiss: () -> Unit) {
    val loadLb = Load.roundLb(Load.kgToLb(loadKg))
    val result = Plates.forLoad(loadLb, Load.US_BAR_LB, Load.US_PLATES_LB)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("${Load.displayLb(loadKg)} lb") },
        text = {
            if (result == null) Text("Load is below the ${Load.US_BAR_LB.toInt()} lb bar.")
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${Load.US_BAR_LB.toInt()} lb bar")
                if (result.perSide.isEmpty()) Text("No plates — bar only.")
                else Text(
                    "Per side: " + result.perSide.groupingBy { it }.eachCount().entries
                        .joinToString(" + ") { (p, n) ->
                            val label = if (p == p.toLong().toDouble()) p.toLong().toString() else p.toString()
                            if (n == 1) label else "${n}×$label"
                        },
                )
                if (result.leftoverKg > 0) Text("Cannot make exactly — ${"%.1f".format(result.leftoverKg)} lb leftover. Do not invent a plate.")
            }
        },
    )
}
