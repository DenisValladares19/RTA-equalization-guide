package org.rtaguide.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.rtaguide.app.AnalyzerViewModel
import org.rtaguide.app.UiState
import org.rtaguide.app.export.Exporter
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionsScreen(state: UiState, vm: AnalyzerViewModel) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            SectionTitle("Guardar la medición actual")
            OutlinedTextField(name, { name = it }, label = { Text("Nombre (p. ej. \"Iglesia – antes de EQ\")") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notas del lugar, equipo, posición…") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 2)
            Button(
                onClick = {
                    vm.saveSession(name, notes)
                    name = ""
                    notes = ""
                },
                enabled = state.frame != null,
                modifier = Modifier.padding(vertical = 8.dp),
            ) { Text("Guardar medición") }
            Text(
                "Se guarda el espectro, la curva, las sugerencias de EQ y tus notas. Usa \"Comparar\" para ver el antes y el después en el gráfico.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.reference?.let { ref ->
                Row(Modifier.padding(top = 8.dp)) {
                    Text("Comparando con: ${ref.name}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { vm.setReference(null) }) { Text("Quitar") }
                }
            }
            SectionTitle("Mediciones guardadas (${state.sessions.size})")
        }
        items(state.sessions, key = { it.id }) { session ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(session.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${dateFormat.format(Date(session.createdAtMs))} · ${session.curveName ?: "sin curva"} · ${session.unit}" +
                            if (session.positions > 1) " · ${session.positions} posiciones" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (session.notes.isNotBlank()) Text(session.notes, style = MaterialTheme.typography.bodySmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(onClick = { vm.setReference(session) }) { Text("Comparar") }
                        OutlinedButton(onClick = { Exporter.shareCsv(context, session) }) { Text("CSV") }
                        OutlinedButton(onClick = { Exporter.sharePdf(context, session, state.curves) }) { Text("PDF") }
                        OutlinedButton(onClick = { Exporter.sharePng(context, session, state.curves) }) { Text("Imagen") }
                        TextButton(onClick = { vm.deleteSession(session.id) }) { Text("Borrar") }
                    }
                }
            }
        }
    }
}
