package org.rtaguide.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.rtaguide.app.AnalyzerViewModel
import org.rtaguide.app.UiState
import org.rtaguide.app.data.ChartStyle
import org.rtaguide.dsp.BandSet
import org.rtaguide.targets.CurveEditor
import org.rtaguide.targets.TargetCurve

@Composable
fun CurvesScreen(state: UiState, vm: AnalyzerViewModel) {
    var editing by remember { mutableStateOf<TargetCurve?>(null) }
    var askName by remember { mutableStateOf(false) }
    val current = editing
    if (current != null) {
        CurveEditorView(current, onCancel = { editing = null }, onSave = {
            vm.saveCurve(it)
            editing = null
        })
        return
    }
    if (askName) {
        NameDialog("Nueva curva desde la medición", "Mi sistema de referencia", onDismiss = { askName = false }) {
            vm.createCurveFromMeasurement(it)
            askName = false
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            SectionTitle("Curvas objetivo (la silueta)")
            Text(
                "Elige cómo debería verse el espectro. La silueta se dibuja detrás de las barras y se alinea sola con tu nivel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = CurveEditor.duplicate(state.curve, "propia-${System.currentTimeMillis()}", "${state.curve.name} (mía)") }) {
                    Text("Duplicar la actual")
                }
                OutlinedButton(onClick = { askName = true }) { Text("Crear desde medición") }
            }
        }
        items(state.curves, key = { it.id }) { curve ->
            val selected = curve.id == state.curve.id
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.selectCurve(curve.id) },
                colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors(),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { vm.selectCurve(curve.id) })
                        Column(Modifier.weight(1f)) {
                            Text(curve.name, fontWeight = FontWeight.Bold)
                            Text(
                                (if (curve.builtIn) "Incluida" else "Propia") + " · tolerancia ±%.1f dB".format(curve.toleranceDb),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!curve.builtIn) {
                            TextButton(onClick = { editing = curve }) { Text("Editar") }
                            IconButton(onClick = { vm.deleteCurve(curve.id) }) { Icon(Icons.Default.Delete, contentDescription = "Borrar") }
                        }
                    }
                    if (curve.description.isNotBlank()) {
                        Text(curve.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RtaChart(
                        ChartModel(
                            bands = BandSet(3).bands,
                            curve = curve,
                            curveOffsetDb = 0.0,
                            toleranceDb = curve.toleranceDb,
                            yTopDb = GUIDE_TOP_DB,
                            yRangeDb = GUIDE_RANGE_DB,
                            unit = "dB",
                            style = ChartStyle.GUIDE,
                        ),
                        Modifier.fillMaxWidth().height(110.dp).padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CurveEditorView(initial: TargetCurve, onCancel: () -> Unit, onSave: (TargetCurve) -> Unit) {
    // Rows are edited in place (not re-sorted while typing); the curve is rebuilt from them.
    var rows by remember { mutableStateOf(initial.points.sortedBy { it[0] }.map { it[0] to it[1] }) }
    var name by remember { mutableStateOf(initial.name) }
    var tolerance by remember { mutableStateOf(initial.toleranceDb) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = rows.filter { it.first > 0 }
    val preview = if (valid.isEmpty()) initial else initial.copy(
        name = name, toleranceDb = tolerance, points = valid.sortedBy { it.first }.map { listOf(it.first, it.second) }, builtIn = false,
    )
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            SectionTitle("Editar curva")
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SliderRow("Tolerancia", tolerance, 0.5..6.0, 0.5, { "±%.1f dB".format(it) }) { v -> tolerance = v }
            RtaChart(
                ChartModel(
                    bands = BandSet(3).bands, curve = preview, toleranceDb = tolerance,
                    yTopDb = GUIDE_TOP_DB, yRangeDb = GUIDE_RANGE_DB, unit = "dB", style = ChartStyle.GUIDE,
                ),
                Modifier.fillMaxWidth().height(160.dp),
            )
            Text(
                "Puntos (Hz, dB respecto a ${initial.referenceHz.toInt()} Hz). Entre puntos se interpola en escala logarítmica.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        itemsIndexed(rows, key = { i, _ -> i }) { index, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField("Hz", row.first, { v ->
                    if (v != null) rows = rows.toMutableList().also { it[index] = v to row.second }
                }, Modifier.weight(1f))
                NumberField("dB", row.second, { v ->
                    if (v != null) rows = rows.toMutableList().also { it[index] = row.first to v }
                }, Modifier.weight(1f).padding(start = 6.dp))
                IconButton(onClick = {
                    if (rows.size > 1) rows = rows.filterIndexed { i, _ -> i != index } else error = "La curva necesita al menos un punto."
                }) { Icon(Icons.Default.Delete, contentDescription = "Quitar punto") }
            }
        }
        item {
            error?.let { Text(it, color = StatusOut) }
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val last = valid.maxOfOrNull { it.first } ?: 1000.0
                    val hz = if (last < 20000) minOf(20000.0, last * 2) else 10000.0
                    rows = rows + (hz to preview.gainAt(hz))
                }) { Text("Añadir punto") }
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
                Button(onClick = {
                    if (valid.isEmpty()) error = "Añade al menos un punto con frecuencia mayor que 0."
                    else onSave(preview.copy(name = name.ifBlank { "Curva propia" }))
                }) { Text("Guardar") }
            }
        }
    }
}

@Composable
fun NameDialog(title: String, placeholder: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, placeholder = { Text(placeholder) }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.ifBlank { placeholder }) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
