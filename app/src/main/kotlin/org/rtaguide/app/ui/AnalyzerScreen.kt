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
import kotlin.math.ceil

fun chartModelOf(state: UiState): ChartModel {
    val frame = state.frame
    val s = state.settings
    val offset = state.displayOffsetDb
    fun DoubleArray.shifted(by: Double) = DoubleArray(size) { this[it] + by }
    // Absolute scale (dBFS, or dB SPL when calibrated). The guide sits at the fixed level chosen by
    // the user; it does not follow the measurement.
    return ChartModel(
        bands = frame?.bands ?: emptyList(),
        measuredDb = state.measuredDb,
        statuses = state.comparison?.items?.map { it.status },
        curve = state.curve,
        curveOffsetDb = s.guideAnchorForBands + offset,
        toleranceDb = s.toleranceDb ?: state.curve.toleranceDb,
        showSilhouette = s.showSilhouette,
        fineHz = if (s.showFineSpectrum) frame?.fineHz else null,
        fineDb = if (s.showFineSpectrum) frame?.fineDb?.shifted(offset) else null,
        maxHoldDb = if (s.showMaxHold) frame?.maxHoldDb?.shifted(offset) else null,
        referenceDb = state.referenceDb,
        previewDb = null,
        feedbackHz = frame?.feedback?.map { it.frequencyHz } ?: emptyList(),
        yTopDb = s.yTopDb + offset,
        yRangeDb = s.yRangeDb,
        unit = s.unit,
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
            RtaChart(chartModelOf(state), Modifier.fillMaxSize(), selected, onTap, onPanZoom = vm::panZoomScale)
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
                GuideLevel(state, vm)
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
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                GuideLevel(state, vm)
                Warnings(state)
            }
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
        RtaChart(chartModelOf(state), Modifier.fillMaxSize(), selected, onTap, onPanZoom = vm::panZoomScale)
        TextButton(onClick = vm::toggleFullscreen, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)) {
            Text("⤢ Ampliar")
        }
    }
}

@Composable
private fun Header(state: UiState, compact: Boolean) {
    val level = state.spl?.let { "%.1f dB SPL (Z)  ·  %.1f dBA  ·  %.1f dBC".format(it.z, it.a, it.c) }
        ?: state.frame?.let { "%.1f dBFS".format(it.overallDbFs) } ?: "—"
    val cmp = state.comparison
    val summary = buildString {
        append(state.curve.name)
        append("  ·  guía en %.1f %s".format(state.settings.guideAnchorForBands + state.displayOffsetDb, state.settings.unit))
        if (cmp != null && state.settings.showSilhouette) append("  ·  ${cmp.okCount}/${cmp.ratedCount} bandas dentro")
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
    FilterChip(selected = s.showFineSpectrum, onClick = { vm.updateSettings { it.copy(showFineSpectrum = !it.showFineSpectrum) } }, label = { Text("Línea FFT") })
    FilterChip(selected = s.showMaxHold, onClick = { vm.updateSettings { it.copy(showMaxHold = !it.showMaxHold) } }, label = { Text("Máximos") })
    OutlinedButton(onClick = vm::capturePosition) { Text("Capturar posición (${state.spatialCount})") }
    if (state.spatialCount > 0) {
        FilterChip(selected = state.useSpatial, onClick = { vm.setUseSpatial(!state.useSpatial) }, label = { Text("Promedio espacial") })
        OutlinedButton(onClick = vm::removeLastPosition) { Text("Quitar última") }
        OutlinedButton(onClick = vm::clearPositions) { Text("Borrar posiciones") }
    }
    OutlinedButton(onClick = vm::autoScale) { Text("Autoescala") }
}

/** Guide (silhouette) level: fixed, adjustable in 1 dB steps or placed once on the measurement. */
@Composable
private fun GuideLevel(state: UiState, vm: AnalyzerViewModel) {
    val level = state.settings.guideAnchorForBands + state.displayOffsetDb
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedButton(onClick = { vm.nudgeGuide(-1.0) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("▼") }
        Text(
            "Guía %.1f".format(level),
            modifier = Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        OutlinedButton(onClick = { vm.nudgeGuide(1.0) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("▲") }
    }
    TextButton(onClick = vm::fitGuideToMeasurement) { Text("Fijar guía al nivel actual") }
}

@Composable
private fun BandInfo(state: UiState, selected: Int?) {
    val frame = state.frame
    val measured = state.measuredDb
    val unit = state.settings.unit
    val text = if (selected == null || frame == null || measured == null || selected !in frame.bands.indices) {
        "Toca el espectro para ver la frecuencia y su nivel."
    } else {
        val band = frame.bands[selected]
        val range = "%s–%s Hz".format(
            org.rtaguide.dsp.formatFrequency(band.lowHz), org.rtaguide.dsp.formatFrequency(band.highHz),
        )
        val level = measured[selected]
        val base = "${band.label} Hz ($range): %.1f %s".format(level, unit)
        val item = state.comparison?.items?.getOrNull(selected)
        if (item == null || !state.settings.showSilhouette) base
        else base + "  ·  guía %.1f %s (%+.1f dB)".format(item.targetDb, unit, item.deviationDb)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
}
