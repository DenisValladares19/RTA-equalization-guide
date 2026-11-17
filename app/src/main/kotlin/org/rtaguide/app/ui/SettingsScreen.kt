package org.rtaguide.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.rtaguide.app.AnalyzerViewModel
import org.rtaguide.app.UiState
import org.rtaguide.app.data.ChartStyle
import org.rtaguide.app.data.InputSource
import org.rtaguide.dsp.AveragingMode
import org.rtaguide.dsp.Weighting
import org.rtaguide.dsp.WindowFunction
import org.rtaguide.targets.Alignment

@Composable
fun SettingsScreen(state: UiState, vm: AnalyzerViewModel) {
    val s = state.settings
    val calibrationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCalibration(uri)
    }
    val wavCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        if (uri != null) vm.exportPinkWav(uri)
    }
    fun update(transform: (org.rtaguide.app.data.AppSettings) -> org.rtaguide.app.data.AppSettings) = vm.updateSettings(transform)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        SectionTitle("Entrada de audio")
        OptionSelector("Fuente", InputSource.entries.toList(), s.inputSourceEnum, { it.label }) { v -> update { it.copy(inputSource = v.name) } }
        if (!state.unprocessedSupported) {
            Text(
                "Este teléfono no declara audio sin procesar: se usa \"reconocimiento de voz\", que suele evitar el control automático de ganancia.",
                style = MaterialTheme.typography.bodySmall, color = StatusWarning,
            )
        }
        val devices = listOf(0 to "Automático") + state.inputDevices
        OptionSelector("Micrófono", devices, devices.firstOrNull { it.first == s.inputDeviceId } ?: devices.first(), { it.second }) { v ->
            update { it.copy(inputDeviceId = v.first) }
        }
        OutlinedButton(onClick = vm::refreshInputDevices) { Text("Buscar micrófonos USB") }
        OptionSelector("Frecuencia de muestreo", listOf(44100, 48000), s.sampleRate, { "$it Hz" }) { v -> update { it.copy(sampleRate = v) } }
        Text("En uso: ${state.sampleRate} Hz · ${state.sourceLabel}", style = MaterialTheme.typography.bodySmall)

        SectionTitle("Análisis")
        OptionSelector("Resolución de bandas", listOf(1, 3, 6, 12), s.bandsPerOctave, { if (it == 1) "1/1 octava" else "1/$it de octava" }) { v ->
            update { it.copy(bandsPerOctave = v) }
        }
        OptionSelector("Promedio", AveragingMode.entries.toList(), s.averagingEnum, { it.label }) { v -> update { it.copy(averaging = v.name) } }
        OptionSelector("Ventana FFT", WindowFunction.entries.toList(), s.windowEnum, { it.label }) { v -> update { it.copy(window = v.name) } }
        OptionSelector("Tamaño FFT", listOf(1024, 2048, 4096), s.fftSize, { "$it puntos" }) { v -> update { it.copy(fftSize = v) } }
        OptionSelector("Diezmados (resolución en graves)", listOf(3, 4, 5, 6), s.decimations, {
            "$it (%.2f Hz/bin)".format(state.sampleRate.toDouble() / (1 shl it) / s.fftSize)
        }) { v -> update { it.copy(decimations = v) } }
        OptionSelector("Actualización", listOf(33, 50, 100, 200), s.updateIntervalMs, { "$it ms" }) { v -> update { it.copy(updateIntervalMs = v) } }
        SwitchRow("Quitar componente continua (DC)", s.removeDc, { v -> update { it.copy(removeDc = v) } })

        SectionTitle("Gráfico")
        OptionSelector("Estilo", ChartStyle.entries.toList(), s.chartStyleEnum, { it.label }) { v -> update { it.copy(chartStyle = v.name) } }
        Text(
            "Guía: espectro como línea sobre la banda verde de la curva, en dB relativos (−9 a +21 dB). " +
                "Barras + silueta: barras de colores por banda; usa el nivel superior y el rango de abajo.",
            style = MaterialTheme.typography.bodySmall,
        )
        SliderRow("Nivel superior", s.yTopDb, -60.0..140.0, 5.0, { "%.0f dB".format(it + state.displayOffsetDb) }) { v -> update { it.copy(yTopDb = v) } }
        SliderRow("Rango vertical", s.yRangeDb, 20.0..120.0, 5.0, { "%.0f dB".format(it) }) { v -> update { it.copy(yRangeDb = v) } }
        SwitchRow("Mantener pantalla encendida", s.keepScreenOn, { v -> update { it.copy(keepScreenOn = v) } })

        SectionTitle("Alineación de la curva objetivo")
        OptionSelector("Modo", Alignment.Mode.entries.toList(), s.alignment.mode, { it.label }) { v ->
            update { it.copy(alignment = it.alignment.copy(mode = v)) }
        }
        when (s.alignment.mode) {
            Alignment.Mode.RANGE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Desde (Hz)", s.alignment.lowHz, { v -> if (v != null && v > 0) update { it.copy(alignment = it.alignment.copy(lowHz = v)) } }, Modifier.weight(1f))
                NumberField("Hasta (Hz)", s.alignment.highHz, { v -> if (v != null && v > 0) update { it.copy(alignment = it.alignment.copy(highHz = v)) } }, Modifier.weight(1f))
            }
            Alignment.Mode.FREQUENCY ->
                NumberField("Frecuencia (Hz)", s.alignment.frequencyHz, { v -> if (v != null && v > 0) update { it.copy(alignment = it.alignment.copy(frequencyHz = v)) } })
            Alignment.Mode.MANUAL ->
                NumberField("Desplazamiento (dB)", s.alignment.manualOffsetDb, { v -> if (v != null) update { it.copy(alignment = it.alignment.copy(manualOffsetDb = v)) } })
        }
        SwitchRow(
            "Comparar aunque no haya señal de prueba",
            s.alwaysCompare,
            { v -> update { it.copy(alwaysCompare = v) } },
            "Normalmente la guía solo aparece con ruido rosa sonando: el ruido ambiental tiene muchos graves y daría lecturas engañosas.",
        )
        SliderRow("Tolerancia (0 = la de la curva)", s.toleranceDb ?: 0.0, 0.0..6.0, 0.5, {
            if (it == 0.0) "±%.1f dB (curva)".format(state.curve.toleranceDb) else "±%.1f dB".format(it)
        }) { v -> update { it.copy(toleranceDb = v.takeIf { t -> t > 0 }) } }

        SectionTitle("Reglas de ecualización")
        SliderRow("Realce máximo", s.rules.maxBoostDb, 0.0..12.0, 0.5, { "+%.1f dB".format(it) }) { v -> update { it.copy(rules = it.rules.copy(maxBoostDb = v)) } }
        SliderRow("Recorte máximo", s.rules.maxCutDb, 3.0..24.0, 0.5, { "−%.1f dB".format(it) }) { v -> update { it.copy(rules = it.rules.copy(maxCutDb = v)) } }
        OptionSelector("No corregir por debajo de", listOf(20.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0), s.rules.lowLimitHz, { "${it} Hz" }) { v ->
            update { it.copy(rules = it.rules.copy(lowLimitHz = v)) }
        }
        OptionSelector("No corregir por encima de", listOf(8000.0, 10000.0, 12500.0, 16000.0, 20000.0), s.rules.highLimitHz, { "${(it / 1000)} kHz" }) { v ->
            update { it.copy(rules = it.rules.copy(highLimitHz = v)) }
        }
        SliderRow("Profundidad para considerar cancelación", s.rules.nullDepthDb, 3.0..15.0, 1.0, { "%.0f dB".format(it) }) { v ->
            update { it.copy(rules = it.rules.copy(nullDepthDb = v)) }
        }
        SliderRow("Ignorar diferencias menores de", s.rules.deadbandDb, 0.0..3.0, 0.5, { "%.1f dB".format(it) }) { v ->
            update { it.copy(rules = it.rules.copy(deadbandDb = v)) }
        }
        OptionSelector("Redondeo", listOf(0.1, 0.5, 1.0), s.rules.roundingStepDb, { "$it dB" }) { v -> update { it.copy(rules = it.rules.copy(roundingStepDb = v)) } }
        OptionSelector("Filtros paramétricos máximos", listOf(3, 4, 6, 8, 10), s.maxParametricFilters, { "$it" }) { v -> update { it.copy(maxParametricFilters = v) } }
        SwitchRow("Suavizar bandas finas (1/6, 1/12)", s.rules.smoothFineBands, { v -> update { it.copy(rules = it.rules.copy(smoothFineBands = v)) } })

        SectionTitle("Calibración del micrófono")
        Text(
            state.calibrationName?.let { "Archivo cargado: $it" } ?: "Sin archivo. Admite el formato de miniDSP UMIK, Dayton iMM-6/EMM-6 y REW (frecuencia dB por línea).",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { calibrationPicker.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }) { Text("Cargar archivo") }
            if (state.calibrationName != null) OutlinedButton(onClick = vm::clearCalibration) { Text("Quitar") }
        }
        SwitchRow("Aplicar calibración", s.useCalibration, { v -> update { it.copy(useCalibration = v) } })

        SectionTitle("Nivel de presión sonora (SPL)")
        Text(
            s.splOffsetDb?.let { "Calibrado: dB SPL = dBFS + %.1f".format(it) }
                ?: "Sin calibrar: los niveles se muestran en dBFS (relativos).",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::calibrateWith94dB) { Text("Calibrar con 94 dB / 1 kHz") }
            if (s.splOffsetDb != null) OutlinedButton(onClick = { vm.setSplOffset(null) }) { Text("Quitar") }
        }
        NumberField("Desplazamiento manual (dB)", s.splOffsetDb, { v -> vm.setSplOffset(v) }, Modifier.fillMaxWidth().padding(top = 6.dp))
        OptionSelector("Ponderación del nivel global", Weighting.entries.toList(), s.weightingEnum, { it.label }) { v -> update { it.copy(weighting = v.name) } }

        SectionTitle("Ruido de fondo")
        Text(
            "Con el sistema en silencio, captura el ruido de fondo. Las bandas a menos de 10 dB de él se marcan como poco fiables y no se corrigen.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::captureNoiseFloor) { Text(if (state.noiseFloorCaptured) "Volver a capturar" else "Capturar ruido de fondo") }
            if (state.noiseFloorCaptured) OutlinedButton(onClick = vm::clearNoiseFloor) { Text("Quitar") }
        }

        SectionTitle("Ruido rosa")
        SliderRow("Nivel", s.pinkNoiseLevelDb, -40.0..-3.0, 1.0, { "%.0f dBFS".format(it) }) { v -> update { it.copy(pinkNoiseLevelDb = v) } }
        Text(
            "Para medir un sistema profesional, conecta el teléfono a la mesa o reproduce el archivo WAV desde la consola.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { wavCreator.launch("ruido-rosa-48k.wav") }) { Text("Guardar WAV de ruido rosa (60 s)") }

        SectionTitle("Acerca de")
        Text(
            "RTA Guía EQ 0.1.0 · Analizador de espectro con curvas objetivo y guía de ecualización. " +
                "Micrófonos de teléfono: poco fiables por debajo de ~50 Hz y por encima de ~12 kHz; usa un micro de medición calibrado para trabajo profesional.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(32.dp))
    }
}
