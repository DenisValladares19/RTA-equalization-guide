package org.rtaguide.app.ui

import androidx.compose.foundation.horizontalScroll
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import org.rtaguide.dsp.AveragingMode
import org.rtaguide.targets.CorrectionFlag
import kotlin.math.ceil

fun chartModelOf(state: UiState): ChartModel {
    val frame = state.frame
    val s = state.settings
    // Guide view: everything relative to the aligned target (0 dB = curve at its reference), like
    // the classic GEQ/RTA guide screens (−9 … +21 dB). Without a comparison (no test signal) the
    // spectrum is shown on the absolute scale instead, like a plain analyzer.
    val guide = s.chartStyleEnum == ChartStyle.GUIDE && state.comparison != null
    val shift = if (guide) -(state.comparison?.offsetDb ?: 0.0) else 0.0
    val offset = state.displayOffsetDb + shift
    fun DoubleArray.shifted(by: Double) = DoubleArray(size) { this[it] + by }
    return ChartModel(
        bands = frame?.bands ?: emptyList(),
        measuredDb = state.measuredDb?.shifted(shift),
        statuses = state.comparison?.items?.map { it.status },
        curve = state.curve,
        curveOffsetDb = if (guide) 0.0 else state.comparison?.offsetDb ?: (s.yTopDb - 20),
        toleranceDb = state.comparison?.toleranceDb ?: state.curve.toleranceDb,
        showSilhouette = s.showSilhouette && state.comparison != null,
        fineHz = if (s.showFineSpectrum) frame?.fineHz else null,
        fineDb = if (s.showFineSpectrum) frame?.fineDb?.shifted(offset) else null,
        maxHoldDb = if (s.showMaxHold) frame?.maxHoldDb?.shifted(offset) else null,
        referenceDb = state.referenceDb?.shifted(shift),
        previewDb = state.previewDb?.shifted(shift),
        feedbackHz = frame?.feedback?.map { it.frequencyHz } ?: emptyList(),
        yTopDb = if (guide) GUIDE_TOP_DB else s.yTopDb + state.displayOffsetDb,
        yRangeDb = if (guide) GUIDE_RANGE_DB else s.yRangeDb,
        unit = if (guide) "dB rel." else s.unit,
        style = s.chartStyleEnum,
    )
}

const val GUIDE_TOP_DB = 21.0
const val GUIDE_RANGE_DB = 30.0

@Composable
fun AnalyzerScreen(state: UiState, vm: AnalyzerViewModel) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val onTap: (Int?) -> Unit = { selected = if (selected == it) null else it }

    if (state.chartFullscreen) {
        // Full screen: only the spectrum (and the selected band), maximum space.
        Box(Modifier.fillMaxSize()) {
            RtaChart(chartModelOf(state), Modifier.fillMaxSize(), selected, onTap)
            NoSignalBanner(state, Modifier.align(androidx.compose.ui.Alignment.TopCenter))
            Column(Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(6.dp), horizontalAlignment = androidx.compose.ui.Alignment.End) {
                FilledTonalButton(onClick = vm::toggleFullscreen) { Text("Salir de pantalla completa") }
                Text(state.curve.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (selected != null) {
                Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 22.dp)) { BandInfo(state, selected) }
            }
        }
        return
    }

    if (landscape) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp)) {
                Header(state, compact = true)
                ChartWithZoom(state, vm, selected, onTap, Modifier.weight(1f).fillMaxWidth())
                BandInfo(state, selected)
            }
            Column(
                Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Guía (silueta)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                CurveChips(state, vm)
                Warnings(state)
                Text("Medición", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                PrimaryControls(state, vm)
                Text("Vista", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SecondaryControls(state, vm)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            Header(state, compact = false)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CurveChips(state, vm)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Warnings(state) }
            ChartWithZoom(state, vm, selected, onTap, Modifier.weight(1f).fillMaxWidth())
            BandInfo(state, selected)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) { PrimaryControls(state, vm) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) { SecondaryControls(state, vm) }
        }
    }
}

@Composable
private fun ChartWithZoom(state: UiState, vm: AnalyzerViewModel, selected: Int?, onTap: (Int?) -> Unit, modifier: Modifier) {
    Box(modifier) {
        RtaChart(chartModelOf(state), Modifier.fillMaxSize(), selected, onTap)
        NoSignalBanner(state, Modifier.align(androidx.compose.ui.Alignment.TopCenter))
        TextButton(onClick = vm::toggleFullscreen, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)) {
            Text("⤢ Ampliar")
        }
    }
}

@Composable
private fun NoSignalBanner(state: UiState, modifier: Modifier) {
    if (state.frame == null || state.testSignal || state.settings.alwaysCompare) return
    Text(
        "Sin señal de prueba · escala absoluta (${state.settings.unit})\nReproduce ruido rosa por el sistema para ver la guía",
        modifier = modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = StatusWarning,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun Header(state: UiState, compact: Boolean) {
    val level = state.spl?.let { "%.1f dB SPL (Z)  ·  %.1f dBA  ·  %.1f dBC".format(it.z, it.a, it.c) }
        ?: state.frame?.let { "%.1f dBFS".format(it.overallDbFs) } ?: "—"
    val cmp = state.comparison
    val summary = buildString {
        append(state.curve.name)
        if (cmp != null) append("  ·  ${cmp.okCount}/${cmp.ratedCount} bandas en ±%.0f dB".format(cmp.toleranceDb))
        if (state.useSpatial) append("  ·  promedio de ${state.spatialCount} posiciones")
        if (state.reference != null) append("  ·  comparando con \"${state.reference.name}\"")
    }
    if (compact) {
        Text("$level  ·  $summary", style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(vertical = 2.dp))
    } else {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(level, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One chip per target curve: selecting one replaces the green guide on the spectrum. */
@Composable
private fun CurveChips(state: UiState, vm: AnalyzerViewModel) {
    state.curves.forEach { curve ->
        FilterChip(
            selected = curve.id == state.curve.id,
            onClick = { vm.selectCurve(curve.id) },
            label = { Text(curve.name, maxLines = 1) },
        )
    }
}

@Composable
private fun Warnings(state: UiState) {
    state.warnings.forEach { w ->
        AssistChip(
            onClick = {},
            label = { Text(w.message, style = MaterialTheme.typography.bodySmall) },
            colors = AssistChipDefaults.assistChipColors(labelColor = StatusWarning),
        )
    }
}

@Composable
private fun PrimaryControls(state: UiState, vm: AnalyzerViewModel) {
    Button(onClick = vm::toggleRunning) { Text(if (state.running) "Detener" else "Medir") }
    FilterChip(selected = state.pinkPlaying, onClick = vm::togglePink, label = { Text("Ruido rosa") })
    FilterChip(selected = state.frozen, onClick = vm::toggleFreeze, label = { Text("Congelar") })
    OutlinedButton(onClick = vm::resetAveraging) { Text("Reiniciar") }
    OptionSelector(
        label = "",
        options = AveragingMode.entries.toList(),
        selected = state.settings.averagingEnum,
        labelOf = { it.label },
        onSelect = { mode -> vm.updateSettings { it.copy(averaging = mode.name) } },
    )
    if (!state.hasPermission) {
        Text("Concede el permiso de micrófono para medir.", color = StatusOut, modifier = Modifier.padding(4.dp))
    }
}

@Composable
private fun SecondaryControls(state: UiState, vm: AnalyzerViewModel) {
    val s = state.settings
    OptionSelector(
        label = "",
        options = ChartStyle.entries.toList(),
        selected = s.chartStyleEnum,
        labelOf = { it.label },
        onSelect = { style -> vm.updateSettings { it.copy(chartStyle = style.name) } },
    )
    FilterChip(selected = s.showSilhouette, onClick = { vm.updateSettings { it.copy(showSilhouette = !it.showSilhouette) } }, label = { Text("Mostrar guía") })
    FilterChip(selected = s.showPreview, onClick = { vm.updateSettings { it.copy(showPreview = !it.showPreview) } }, label = { Text("Vista previa EQ") })
    FilterChip(selected = s.showFineSpectrum, onClick = { vm.updateSettings { it.copy(showFineSpectrum = !it.showFineSpectrum) } }, label = { Text("Línea FFT") })
    FilterChip(selected = s.showMaxHold, onClick = { vm.updateSettings { it.copy(showMaxHold = !it.showMaxHold) } }, label = { Text("Máximos") })
    OutlinedButton(onClick = vm::capturePosition) { Text("Capturar posición (${state.spatialCount})") }
    if (state.spatialCount > 0) {
        FilterChip(selected = state.useSpatial, onClick = { vm.setUseSpatial(!state.useSpatial) }, label = { Text("Promedio espacial") })
        OutlinedButton(onClick = vm::removeLastPosition) { Text("Quitar última") }
        OutlinedButton(onClick = vm::clearPositions) { Text("Borrar posiciones") }
    }
    if (s.chartStyleEnum == ChartStyle.SILHOUETTE) {
        OutlinedButton(onClick = {
            val max = state.measuredDb?.maxOrNull() ?: return@OutlinedButton
            val top = ceil((max - state.displayOffsetDb + 6) / 10) * 10
            vm.updateSettings { it.copy(yTopDb = top) }
        }) { Text("Autoescala") }
    }
}

@Composable
private fun BandInfo(state: UiState, selected: Int?) {
    val cmp = state.comparison
    val sug = state.suggestion
    val text = if (selected == null || cmp == null || sug == null || selected !in cmp.items.indices) {
        "Toca una banda para ver cuánto subir o bajar."
    } else {
        val c = cmp.items[selected]
        val corr = sug.items[selected]
        val action = when {
            corr.flag == CorrectionFlag.NONE || corr.flag == CorrectionFlag.BOOST_LIMITED || corr.flag == CorrectionFlag.CUT_LIMITED ->
                if (corr.correctionDb < 0) "bajar ${formatGain(-corr.correctionDb).removePrefix("+")}" else "subir ${formatGain(corr.correctionDb)}"
            else -> corr.flag.message
        }
        "${c.band.label} Hz: medido %.1f, objetivo %.1f (%+.1f dB) → %s".format(c.measuredDb, c.targetDb, c.deviationDb, action) +
            if (corr.flag == CorrectionFlag.BOOST_LIMITED || corr.flag == CorrectionFlag.CUT_LIMITED) " (${corr.flag.message.lowercase()})" else ""
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
}
