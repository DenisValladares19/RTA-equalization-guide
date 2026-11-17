package org.rtaguide.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.rtaguide.app.audio.AnalyzerEngine
import org.rtaguide.app.audio.AudioCapture
import org.rtaguide.app.audio.Frame
import org.rtaguide.app.audio.PinkNoisePlayer
import org.rtaguide.app.data.AppSettings
import org.rtaguide.app.data.CalibrationStore
import org.rtaguide.app.data.CurveStore
import org.rtaguide.app.data.InputSource
import org.rtaguide.app.data.SessionStore
import org.rtaguide.app.data.SettingsStore
import org.rtaguide.dsp.Band
import org.rtaguide.dsp.QualityWarning
import org.rtaguide.dsp.SignalQuality
import org.rtaguide.dsp.SpatialAverager
import org.rtaguide.dsp.SplMeter
import org.rtaguide.dsp.WavWriter
import org.rtaguide.dsp.Weighting
import org.rtaguide.targets.Alignment
import org.rtaguide.targets.BuiltInCurves
import org.rtaguide.targets.CurveComparator
import org.rtaguide.targets.CurveComparison
import org.rtaguide.targets.CurveEditor
import org.rtaguide.targets.EqAdvisor
import org.rtaguide.targets.EqSuggestion
import org.rtaguide.targets.GraphicEqBand
import org.rtaguide.targets.GraphicEqMapper
import org.rtaguide.targets.PeakingFilter
import org.rtaguide.targets.TargetCurve
import org.rtaguide.targets.session.Session
import org.rtaguide.targets.session.SessionBand

data class SplReadout(val z: Double, val a: Double, val c: Double)

data class UiState(
    val settings: AppSettings,
    val curves: List<TargetCurve>,
    val curve: TargetCurve,
    val hasPermission: Boolean = false,
    val running: Boolean = false,
    val frozen: Boolean = false,
    /** Spectrum fills the whole screen (no controls, no navigation, no system bars). */
    val chartFullscreen: Boolean = false,
    val pinkPlaying: Boolean = false,
    val sampleRate: Int = 48000,
    val sourceLabel: String = "",
    val unprocessedSupported: Boolean = true,
    val frame: Frame? = null,
    /** Levels used for the comparison (live average or spatial average), in display units. */
    val measuredDb: DoubleArray? = null,
    /** Live averaged levels in display units. */
    val liveDb: DoubleArray? = null,
    val displayOffsetDb: Double = 0.0,
    /** A broadband test signal (pink noise through the system) is present, so the curve comparison is meaningful. */
    val testSignal: Boolean = true,
    val comparison: CurveComparison? = null,
    val suggestion: EqSuggestion? = null,
    val graphic: List<GraphicEqBand> = emptyList(),
    val parametric: List<PeakingFilter> = emptyList(),
    val parametricRmsDb: Double = 0.0,
    val previewDb: DoubleArray? = null,
    val warnings: List<QualityWarning> = emptyList(),
    val lowSnrBands: Set<Int> = emptySet(),
    val spl: SplReadout? = null,
    val useSpatial: Boolean = false,
    val spatialCount: Int = 0,
    val noiseFloorCaptured: Boolean = false,
    val calibrationName: String? = null,
    val sessions: List<Session> = emptyList(),
    val reference: Session? = null,
    val referenceDb: DoubleArray? = null,
    val inputDevices: List<Pair<Int, String>> = emptyList(),
    val message: String? = null,
)

class AnalyzerViewModel(app: Application) : AndroidViewModel(app), AudioCapture.Listener {
    private val settingsStore = SettingsStore(app)
    private val curveStore = CurveStore(app.filesDir)
    private val sessionStore = SessionStore(app.filesDir)
    private val calibrationStore = CalibrationStore(app)
    private val capture = AudioCapture(app)
    private val pink = PinkNoisePlayer()
    private val spatial = SpatialAverager()
    private var noiseFloor: DoubleArray? = null
    private val engine: AnalyzerEngine

    private val _state: MutableStateFlow<UiState>
    val state: StateFlow<UiState>

    init {
        val settings = settingsStore.load()
        val curves = BuiltInCurves.ALL + curveStore.loadAll()
        val calibration = calibrationStore.load()
        engine = AnalyzerEngine(settings, settings.sampleRate).also { it.calibration = calibration }
        pink.levelDbFs = settings.pinkNoiseLevelDb
        _state = MutableStateFlow(
            UiState(
                settings = settings,
                curves = curves,
                curve = curves.firstOrNull { it.id == settings.curveId } ?: BuiltInCurves.LIVE_PA,
                calibrationName = calibration?.name,
                sessions = sessionStore.loadAll(),
                unprocessedSupported = capture.unprocessedSupported,
                inputDevices = runCatching { capture.inputDevices() }.getOrDefault(emptyList()),
            ),
        )
        state = _state.asStateFlow()
    }

    // ---------------------------------------------------------------- capture

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted && !capture.isRunning) start()
        if (!granted) message("Sin permiso de micrófono no se puede medir.")
    }

    fun start() {
        val s = _state.value
        if (!s.hasPermission) return
        try {
            val started = capture.start(s.settings.inputSourceEnum, s.settings.sampleRate, s.settings.inputDeviceId, this)
            engine.configure(s.settings, started.sampleRate)
            _state.update {
                it.copy(
                    running = true, sampleRate = started.sampleRate, sourceLabel = started.sourceLabel,
                    unprocessedSupported = started.unprocessedSupported,
                )
            }
        } catch (e: IllegalStateException) {
            _state.update { it.copy(running = false) }
            message(e.message ?: "No se pudo abrir el micrófono.")
        }
    }

    fun stop() {
        capture.stop()
        _state.update { it.copy(running = false) }
    }

    fun toggleRunning() = if (_state.value.running) stop() else start()

    fun onBackground() {
        stop()
        stopPink()
    }

    fun toggleFullscreen() = _state.update { it.copy(chartFullscreen = !it.chartFullscreen) }

    fun toggleFreeze() = _state.update { it.copy(frozen = !it.frozen) }

    fun resetAveraging() {
        engine.resetAveraging()
        message("Promedio reiniciado")
    }

    override fun onAudio(block: FloatArray, length: Int, peak: Float) {
        val frame = engine.process(block, length, peak) ?: return
        if (_state.value.frozen) return
        _state.update { derive(it.copy(frame = frame)) }
    }

    override fun onCaptureError(message: String) {
        _state.update { it.copy(running = false) }
        message(message)
    }

    // ---------------------------------------------------------------- derived data

    private fun derive(base: UiState): UiState {
        val frame = base.frame ?: return base
        val s = base.settings
        val offset = s.splOffsetDb ?: 0.0
        val bands = frame.bands
        val centers = DoubleArray(bands.size) { bands[it].centerHz }
        val spatialAvg = spatial.average()?.takeIf { it.size == bands.size }
        val useSpatial = base.useSpatial && spatialAvg != null
        val basis = if (useSpatial) spatialAvg!! else frame.averagedDb
        val measured = DoubleArray(basis.size) { basis[it] + offset }
        val live = DoubleArray(frame.averagedDb.size) { frame.averagedDb[it] + offset }

        val floor = noiseFloor?.takeIf { it.size == bands.size }
        val (quality, lowSnr) = SignalQuality.assess(frame.peak, frame.overallDbFs, frame.averagedDb, floor, frame.warmup)
        val warnings = quality.toMutableList()
        if (!base.unprocessedSupported && s.inputSourceEnum in setOf(InputSource.AUTO, InputSource.UNPROCESSED)) {
            warnings.add(QualityWarning.UNPROCESSED_UNAVAILABLE)
        }

        // The guide is fixed at a user-chosen level (absolute scale); it does not follow the measurement.
        val anchor = guideAnchor(s)
        val comparison = CurveComparator.compare(
            bands, measured, base.curve, Alignment(Alignment.Mode.MANUAL, manualOffsetDb = anchor),
            s.toleranceDb ?: base.curve.toleranceDb, ignored = lowSnr,
        )
        // Band-by-band suggestions are kept for saved sessions; the EQ tab and preview are hidden.
        val suggestion = EqAdvisor.suggest(comparison, s.rules, s.bandsPerOctave, lowSnr)
        val graphic = GraphicEqMapper.map(suggestion, s.graphicLayoutEnum)
        val parametric = emptyList<PeakingFilter>()
        val rms = 0.0
        val preview: DoubleArray? = null
        val testSignal = true
        val spl = s.splOffsetDb?.let {
            SplReadout(
                SplMeter.overallLevel(frame.averagedDb, centers, Weighting.Z, it),
                SplMeter.overallLevel(frame.averagedDb, centers, Weighting.A, it),
                SplMeter.overallLevel(frame.averagedDb, centers, Weighting.C, it),
            )
        }
        val referenceDb = base.reference?.let { AnalyzerEngine.resample(it.centersHz, it.measuredDb, centers) }

        return base.copy(
            measuredDb = measured, liveDb = live, displayOffsetDb = offset, testSignal = testSignal, comparison = comparison,
            suggestion = suggestion, graphic = graphic, parametric = parametric, parametricRmsDb = rms,
            previewDb = preview, warnings = warnings, lowSnrBands = lowSnr, spl = spl,
            useSpatial = useSpatial, spatialCount = spatial.count, noiseFloorCaptured = floor != null,
            referenceDb = referenceDb,
        )
    }

    private fun refresh() = _state.update { derive(it) }

    // ---------------------------------------------------------------- settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = _state.value.settings
        val new = transform(old)
        if (new == old) return
        settingsStore.save(new)
        pink.levelDbFs = new.pinkNoiseLevelDb
        if (new.needsEngineRebuild(old)) {
            spatial.clear()
            noiseFloor = null
        }
        _state.update { it.copy(settings = new) }
        if (new.needsCaptureRestart(old) && _state.value.running) {
            stop()
            start()
        } else {
            engine.configure(new, _state.value.sampleRate)
        }
        refresh()
    }

    fun refreshInputDevices() = _state.update {
        it.copy(inputDevices = runCatching { capture.inputDevices() }.getOrDefault(emptyList()))
    }

    // ---------------------------------------------------------------- curves

    fun selectCurve(id: String) {
        val curve = _state.value.curves.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(curve = curve) }
        updateSettings { it.copy(curveId = id) }
        refresh()
    }

    fun saveCurve(curve: TargetCurve) {
        val stored = curve.copy(builtIn = false)
        curveStore.save(stored)
        reloadCurves(selectId = stored.id)
        message("Curva \"${stored.name}\" guardada")
    }

    fun deleteCurve(id: String) {
        curveStore.delete(id)
        if (_state.value.curve.id == id) selectCurve(BuiltInCurves.LIVE_PA.id)
        reloadCurves()
    }

    fun createCurveFromMeasurement(name: String) {
        val s = _state.value
        val frame = s.frame
        val measured = s.measuredDb
        if (frame == null || measured == null) {
            message("Primero toma una medición.")
            return
        }
        val curve = CurveEditor.fromMeasurement(
            id = "medida-${System.currentTimeMillis()}", name = name.ifBlank { "Curva medida" },
            bands = frame.bands, levelsDb = measured,
        )
        saveCurve(curve)
    }

    private fun reloadCurves(selectId: String? = null) {
        val curves = BuiltInCurves.ALL + curveStore.loadAll()
        val id = selectId ?: _state.value.curve.id
        val curve = curves.firstOrNull { it.id == id } ?: BuiltInCurves.LIVE_PA
        _state.update { it.copy(curves = curves, curve = curve) }
        if (selectId != null) updateSettings { it.copy(curveId = curve.id) }
        refresh()
    }

    // ---------------------------------------------------------------- pink noise

    fun togglePink() = if (pink.isPlaying) stopPink() else startPink()

    private fun startPink() {
        pink.levelDbFs = _state.value.settings.pinkNoiseLevelDb
        pink.start()
        _state.update { it.copy(pinkPlaying = true) }
    }

    private fun stopPink() {
        pink.stop()
        _state.update { it.copy(pinkPlaying = false) }
    }

    fun exportPinkWav(uri: Uri) {
        val level = _state.value.settings.pinkNoiseLevelDb
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(WavWriter.pinkNoise(48000, 60, level))
                } ?: error("no se pudo abrir el archivo")
            }.onSuccess { message("Ruido rosa guardado (60 s, 48 kHz)") }
                .onFailure { message("No se pudo guardar el WAV: ${it.message}") }
        }
    }

    // ---------------------------------------------------------------- spatial average / noise floor

    fun capturePosition() {
        val frame = _state.value.frame ?: return message("Aún no hay medición.")
        spatial.add(frame.averagedDb)
        _state.update { it.copy(useSpatial = true) }
        refresh()
        message("Posición ${spatial.count} capturada")
    }

    fun removeLastPosition() {
        spatial.removeLast()
        refresh()
    }

    fun clearPositions() {
        spatial.clear()
        _state.update { it.copy(useSpatial = false) }
        refresh()
    }

    fun setUseSpatial(enabled: Boolean) {
        _state.update { it.copy(useSpatial = enabled) }
        refresh()
    }

    fun captureNoiseFloor() {
        val frame = _state.value.frame ?: return message("Aún no hay medición.")
        noiseFloor = frame.averagedDb.copyOf()
        refresh()
        message("Ruido de fondo capturado")
    }

    fun clearNoiseFloor() {
        noiseFloor = null
        refresh()
    }

    // ---------------------------------------------------------------- calibration / SPL

    fun importCalibration(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("no se pudo leer el archivo")
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "Calibración"
                calibrationStore.save(text, name)
            }.onSuccess { cal ->
                engine.calibration = cal
                engine.resetAveraging()
                _state.update { it.copy(calibrationName = cal.name) }
                message("Calibración cargada: ${cal.points.size} puntos")
            }.onFailure { message("Archivo de calibración no válido: ${it.message}") }
        }
    }

    fun clearCalibration() {
        calibrationStore.clear()
        engine.calibration = null
        engine.resetAveraging()
        _state.update { it.copy(calibrationName = null) }
    }

    /** With a 94 dB SPL / 1 kHz calibrator on the microphone: sets the dBFS → dB SPL offset. */
    fun calibrateWith94dB() {
        val frame = _state.value.frame ?: return message("Aún no hay medición.")
        val index = frame.bands.indices.minByOrNull { kotlin.math.abs(kotlin.math.ln(frame.bands[it].centerHz / 1000.0)) }!!
        val offset = SplMeter.offsetFromCalibrator(frame.averagedDb[index])
        updateSettings { it.copy(splOffsetDb = (offset * 10).toLong() / 10.0) }
        message("Calibrado: dB SPL = dBFS + ${"%.1f".format(offset)}")
    }

    fun setSplOffset(offset: Double?) = updateSettings { it.copy(splOffsetDb = offset) }

    // ---------------------------------------------------------------- guide level

    /** Guide anchor in display units (dB SPL when calibrated) for the current band resolution. */
    fun guideAnchor(s: AppSettings = _state.value.settings): Double = s.guideAnchorForBands + (s.splOffsetDb ?: 0.0)

    fun nudgeGuide(deltaDb: Double) = updateSettings { it.copy(guideAnchorDb = (it.guideAnchorDb + deltaDb).coerceIn(-90.0, 0.0)) }

    /** Places the guide once where the current measurement is (average 500 Hz–2 kHz), then leaves it fixed. */
    fun fitGuideToMeasurement() {
        val st = _state.value
        val frame = st.frame
        val measured = st.measuredDb
        if (frame == null || measured == null) return message("Aún no hay medición.")
        val idx = frame.bands.indices.filter { frame.bands[it].centerHz in 500.0..2000.0 }
        if (idx.isEmpty()) return
        val level = idx.map { measured[it] - st.curve.relativeGainAt(frame.bands[it].centerHz) }.average()
        val s = st.settings
        val anchorThird = level - (s.splOffsetDb ?: 0.0) - 10.0 * kotlin.math.log10(3.0 / s.bandsPerOctave)
        updateSettings { it.copy(guideAnchorDb = Math.round(anchorThird * 2) / 2.0) }
        message("Guía fijada en %.1f dBFS".format(Math.round(anchorThird * 2) / 2.0))
    }

    // ---------------------------------------------------------------- sessions

    fun saveSession(name: String, notes: String) {
        val s = _state.value
        val frame = s.frame
        val measured = s.measuredDb
        if (frame == null || measured == null) return message("Primero toma una medición.")
        val comparison = s.comparison
        val session = Session(
            id = System.currentTimeMillis().toString(),
            name = name.ifBlank { "Medición ${s.sessions.size + 1}" },
            notes = notes,
            createdAtMs = System.currentTimeMillis(),
            sampleRate = frame.sampleRate,
            bandsPerOctave = frame.bandsPerOctave,
            positions = if (s.useSpatial) s.spatialCount else 1,
            unit = s.settings.unit,
            curveId = s.curve.id,
            curveName = s.curve.name,
            toleranceDb = comparison?.toleranceDb,
            bands = frame.bands.mapIndexed { i, band ->
                val correction = s.suggestion?.items?.getOrNull(i)
                SessionBand(
                    centerHz = band.centerHz, label = band.label, measuredDb = round1(measured[i]),
                    targetDb = comparison?.items?.getOrNull(i)?.targetDb?.let(::round1),
                    correctionDb = correction?.correctionDb,
                    note = correction?.flag?.message.orEmpty(),
                )
            },
            graphicEq = s.graphic,
            parametric = s.parametric,
        )
        sessionStore.save(session)
        _state.update { it.copy(sessions = sessionStore.loadAll()) }
        message("Medición \"${session.name}\" guardada")
    }

    fun deleteSession(id: String) {
        sessionStore.delete(id)
        _state.update {
            it.copy(sessions = sessionStore.loadAll(), reference = it.reference?.takeIf { r -> r.id != id })
        }
        refresh()
    }

    /** Overlays a saved measurement on the chart (before/after comparison). */
    fun setReference(session: Session?) {
        _state.update { it.copy(reference = session) }
        refresh()
    }

    // ---------------------------------------------------------------- misc

    fun message(text: String) = _state.update { it.copy(message = text) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        capture.stop()
        pink.stop()
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0

    companion object {
        fun bandsOf(state: UiState): List<Band> = state.frame?.bands ?: emptyList()
    }
}
