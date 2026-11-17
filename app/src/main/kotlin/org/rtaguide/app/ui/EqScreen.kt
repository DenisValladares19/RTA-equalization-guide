package org.rtaguide.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.rtaguide.app.AnalyzerViewModel
import org.rtaguide.app.UiState
import org.rtaguide.app.data.PreviewSource
import org.rtaguide.targets.CorrectionFlag
import org.rtaguide.targets.GraphicEqLayout
import kotlin.math.abs

@Composable
fun EqScreen(state: UiState, vm: AnalyzerViewModel) {
    val s = state.settings
    val suggestion = state.suggestion
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            SectionTitle("Guía de ecualización")
            val cmp = state.comparison
            Text(
                if (cmp == null) "Mide con ruido rosa para obtener sugerencias."
                else "Curva: ${state.curve.name}. ${cmp.okCount} de ${cmp.ratedCount} bandas dentro de ±%.0f dB.".format(cmp.toleranceDb),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Recorta antes de realzar. Aplica los cambios, vuelve a medir y escucha: el oído decide al final.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OptionSelector("Ecualizador", GraphicEqLayout.entries.toList(), s.graphicLayoutEnum, { it.label }) { layout ->
                vm.updateSettings { it.copy(graphicLayout = layout.name) }
            }
            OptionSelector("Vista previa en el gráfico", PreviewSource.entries.toList(), s.previewSourceEnum, { it.label }) { p ->
                vm.updateSettings { it.copy(previewSource = p.name, showPreview = true) }
            }
            SliderRow("Realce máximo", s.rules.maxBoostDb, 0.0..12.0, 0.5, { "+%.1f dB".format(it) }) { v ->
                vm.updateSettings { it.copy(rules = it.rules.copy(maxBoostDb = v)) }
            }
            SliderRow("Recorte máximo", s.rules.maxCutDb, 3.0..24.0, 0.5, { "−%.1f dB".format(it) }) { v ->
                vm.updateSettings { it.copy(rules = it.rules.copy(maxCutDb = v)) }
            }
        }

        item { SectionTitle("EQ gráfico: mueve cada fader a este valor") }
        if (state.graphic.isEmpty()) {
            item { Text("Sin datos todavía.", style = MaterialTheme.typography.bodySmall) }
        }
        items(state.graphic) { band ->
            GainRow(band.label + " Hz", band.gainDb, s.rules.maxCutDb, s.rules.maxBoostDb)
        }

        item {
            SectionTitle("EQ paramétrico sugerido")
            if (state.parametric.isEmpty()) {
                Text("No hace falta ningún filtro (o aún no hay medición).", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "Error residual: %.1f dB RMS frente a la corrección por banda.".format(state.parametricRmsDb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.parametric.withIndex().toList()) { (i, f) ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtro ${i + 1}", Modifier.width(72.dp), fontWeight = FontWeight.Bold)
                    Text(
                        "%s Hz   %s   Q %.1f (%.2f oct)".format(
                            org.rtaguide.dsp.formatFrequency(f.frequencyHz), formatGain(f.gainDb), f.q, f.bandwidthOctaves,
                        ),
                    )
                }
            }
        }

        val notes = suggestion?.items?.filter {
            it.flag == CorrectionFlag.ACOUSTIC_NULL || it.flag == CorrectionFlag.BOOST_LIMITED || it.flag == CorrectionFlag.LOW_SNR
        }.orEmpty()
        if (notes.isNotEmpty()) {
            item { SectionTitle("Avisos por banda") }
            items(notes) { n ->
                Text("• ${n.band.label} Hz (%+.1f dB): ${n.flag.message}".format(-n.rawDb), style = MaterialTheme.typography.bodySmall)
            }
        }

        val feedback = state.frame?.feedback.orEmpty()
        item {
            SectionTitle("Realimentación (feedback)")
            SwitchRow(
                "Detectar realimentación",
                s.feedbackDetection,
                { v -> vm.updateSettings { it.copy(feedbackDetection = v) } },
                "Busca picos estrechos y persistentes (un tono que \"silba\").",
            )
            if (s.feedbackDetection && feedback.isEmpty()) {
                Text("No se detecta realimentación.", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(feedback) { fb ->
            Text(
                "⚠ %s Hz destaca %.0f dB → filtro de muesca: %s, Q %.0f".format(
                    org.rtaguide.dsp.formatFrequency(fb.frequencyHz), fb.prominenceDb, formatGain(fb.suggestedCutDb), fb.suggestedQ,
                ),
                color = StatusOut,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { Box(Modifier.height(24.dp)) }
    }
}

/** One fader row: label, centered bar (cut to the left, boost to the right) and value. */
@Composable
private fun GainRow(label: String, gainDb: Double, maxCut: Double, maxBoost: Double) {
    val color = when {
        gainDb == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
        gainDb < 0 -> MaterialTheme.colorScheme.primary
        else -> StatusWarning
    }
    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp)) {
            val span = maxOf(maxCut, 1.0)
            val cutFraction = if (gainDb < 0) (abs(gainDb) / span).coerceIn(0.0, 1.0).toFloat() else 0f
            val boostFraction = if (gainDb > 0) (gainDb / maxOf(maxBoost, 1.0)).coerceIn(0.0, 1.0).toFloat() else 0f
            // Left half (cuts), filled from the center outwards.
            Row(Modifier.weight(1f).fillMaxHeight()) {
                if (cutFraction < 1f) Box(Modifier.weight(1f - cutFraction))
                if (cutFraction > 0f) Box(Modifier.weight(cutFraction).fillMaxHeight().background(color))
            }
            Box(Modifier.width(2.dp).fillMaxHeight().background(Color.Gray))
            Row(Modifier.weight(1f).fillMaxHeight()) {
                if (boostFraction > 0f) Box(Modifier.weight(boostFraction).fillMaxHeight().background(color))
                if (boostFraction < 1f) Box(Modifier.weight(1f - boostFraction))
            }
        }
        Text(formatGain(gainDb), Modifier.width(76.dp).padding(start = 8.dp), color = color, fontWeight = FontWeight.Bold)
    }
}
