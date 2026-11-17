package org.rtaguide.app.data

import android.media.MediaRecorder
import kotlinx.serialization.Serializable
import org.rtaguide.dsp.AveragingMode
import org.rtaguide.dsp.Weighting
import org.rtaguide.dsp.WindowFunction
import org.rtaguide.targets.Alignment
import org.rtaguide.targets.BuiltInCurves
import org.rtaguide.targets.EqRules
import org.rtaguide.targets.GraphicEqLayout

enum class InputSource(val label: String, val androidSource: Int) {
    AUTO("Automático (sin procesar si es posible)", MediaRecorder.AudioSource.UNPROCESSED),
    UNPROCESSED("Sin procesar (UNPROCESSED)", MediaRecorder.AudioSource.UNPROCESSED),
    VOICE_RECOGNITION("Reconocimiento de voz", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    MIC("Micrófono estándar", MediaRecorder.AudioSource.MIC),
    CAMCORDER("Videocámara", MediaRecorder.AudioSource.CAMCORDER),
}

/** How the analyzer chart is drawn. */
enum class ChartStyle(val label: String) {
    GUIDE("Guía (línea + banda verde)"),
    SILHOUETTE("Barras + silueta"),
}

enum class PreviewSource(val label: String) {
    GRAPHIC("EQ gráfico"),
    PARAMETRIC("EQ paramétrico"),
    BANDS("Corrección por banda"),
}

/** All user settings, persisted as JSON. Enums are stored by name to survive renames. */
@Serializable
data class AppSettings(
    val inputSource: String = InputSource.AUTO.name,
    val inputDeviceId: Int = 0,
    val sampleRate: Int = 48000,
    val fftSize: Int = 2048,
    val decimations: Int = 5,
    val window: String = WindowFunction.BLACKMAN_HARRIS.name,
    val removeDc: Boolean = true,
    val bandsPerOctave: Int = 3,
    val averaging: String = AveragingMode.SLOW.name,
    val updateIntervalMs: Int = 50,
    val weighting: String = Weighting.Z.name,
    val splOffsetDb: Double? = null,
    val useCalibration: Boolean = true,
    val curveId: String = BuiltInCurves.BASS_GUIDE.id,
    val toleranceDb: Double? = null,
    val alignment: Alignment = Alignment(),
    val rules: EqRules = EqRules(),
    val graphicLayout: String = GraphicEqLayout.THIRTY_ONE.name,
    val maxParametricFilters: Int = 6,
    val showFineSpectrum: Boolean = false,
    val showMaxHold: Boolean = false,
    val showSilhouette: Boolean = true,
    val chartStyle: String = ChartStyle.GUIDE.name,
    val showPreview: Boolean = false,
    val previewSource: String = PreviewSource.GRAPHIC.name,
    val feedbackDetection: Boolean = false,
    val yTopDb: Double = 0.0,
    val yRangeDb: Double = 70.0,
    val pinkNoiseLevelDb: Double = -12.0,
    val keepScreenOn: Boolean = true,
) {
    val inputSourceEnum: InputSource get() = enumOr(inputSource, InputSource.AUTO)
    val windowEnum: WindowFunction get() = enumOr(window, WindowFunction.BLACKMAN_HARRIS)
    val averagingEnum: AveragingMode get() = enumOr(averaging, AveragingMode.SLOW)
    val weightingEnum: Weighting get() = enumOr(weighting, Weighting.Z)
    val graphicLayoutEnum: GraphicEqLayout get() = enumOr(graphicLayout, GraphicEqLayout.THIRTY_ONE)
    val previewSourceEnum: PreviewSource get() = enumOr(previewSource, PreviewSource.GRAPHIC)
    val chartStyleEnum: ChartStyle get() = enumOr(chartStyle, ChartStyle.GUIDE)

    /** Unit shown on screen: dB SPL once a calibration offset exists. */
    val unit: String get() = if (splOffsetDb != null) "dB SPL" else "dBFS"

    fun needsCaptureRestart(other: AppSettings): Boolean =
        inputSource != other.inputSource || inputDeviceId != other.inputDeviceId || sampleRate != other.sampleRate

    fun needsEngineRebuild(other: AppSettings): Boolean =
        fftSize != other.fftSize || decimations != other.decimations || bandsPerOctave != other.bandsPerOctave
}

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(default)
