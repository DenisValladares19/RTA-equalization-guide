package org.rtaguide.targets

import kotlinx.serialization.Serializable
import org.rtaguide.dsp.Band
import kotlin.math.abs
import kotlin.math.roundToLong

/** Professional rules applied when turning deviations into EQ moves. */
@Serializable
data class EqRules(
    /** Largest boost suggested (dB). Boosts eat headroom and can cause feedback. */
    val maxBoostDb: Double = 3.0,
    /** Largest cut suggested (dB). */
    val maxCutDb: Double = 12.0,
    /** No correction below this frequency (system / microphone limit). */
    val lowLimitHz: Double = 40.0,
    /** No correction above this frequency (system / microphone limit). */
    val highLimitHz: Double = 16000.0,
    /** A dip this much deeper than its neighbors is treated as a room cancellation. */
    val nullDepthDb: Double = 6.0,
    /** Corrections smaller than this are left at 0 dB. */
    val deadbandDb: Double = 1.0,
    /** Suggestions are rounded to this step (dB). */
    val roundingStepDb: Double = 0.5,
    /** Smooth neighboring bands when the analysis is finer than 1/3 octave. */
    val smoothFineBands: Boolean = true,
)

enum class CorrectionFlag(val message: String) {
    NONE(""),
    OUT_OF_RANGE("Fuera del rango a corregir"),
    LOW_SNR("Lectura poco fiable (ruido de fondo)"),
    ACOUSTIC_NULL("Cancelación de sala: no se corrige con EQ (mueve altavoz o micrófono)"),
    BOOST_LIMITED("Realce limitado por seguridad"),
    CUT_LIMITED("Recorte limitado"),
    BELOW_THRESHOLD("Diferencia pequeña: no hace falta tocar"),
}

data class BandCorrection(
    val band: Band,
    /** target − measured, before rules. */
    val rawDb: Double,
    /** Suggested EQ gain after rules (negative = cut, positive = boost). */
    val correctionDb: Double,
    val flag: CorrectionFlag,
)

data class EqSuggestion(val items: List<BandCorrection>, val rules: EqRules) {
    val corrections: DoubleArray get() = DoubleArray(items.size) { items[it].correctionDb }
    val centersHz: DoubleArray get() = DoubleArray(items.size) { items[it].band.centerHz }
}

object EqAdvisor {
    fun suggest(
        comparison: CurveComparison,
        rules: EqRules = EqRules(),
        bandsPerOctave: Int = 3,
        lowSnrBands: Set<Int> = emptySet(),
    ): EqSuggestion {
        val items = comparison.items
        val n = items.size
        val raw = DoubleArray(n) { items[it].targetDb - items[it].measuredDb }
        val flags = Array(n) { CorrectionFlag.NONE }
        val value = raw.copyOf()
        val inRange = BooleanArray(n) { items[it].band.centerHz in rules.lowLimitHz..rules.highLimitHz }
        val k = maxOf(1, bandsPerOctave / 3) // neighbors ≈ 1/3 octave away

        for (i in 0 until n) {
            when {
                !inRange[i] || items[i].status == BandStatus.IGNORED && i !in lowSnrBands -> {
                    flags[i] = CorrectionFlag.OUT_OF_RANGE; value[i] = 0.0
                }
                i in lowSnrBands -> {
                    flags[i] = CorrectionFlag.LOW_SNR; value[i] = 0.0
                }
            }
        }
        // Narrow deep dips = acoustic cancellations: boosting them wastes power and does not work.
        for (i in 0 until n) {
            if (flags[i] != CorrectionFlag.NONE || raw[i] <= 0) continue
            val left = i - k
            val right = i + k
            if (left < 0 || right >= n) continue
            val neighbors = (items[left].measuredDb + items[right].measuredDb) / 2
            if (neighbors - items[i].measuredDb >= rules.nullDepthDb) {
                flags[i] = CorrectionFlag.ACOUSTIC_NULL
                value[i] = 0.0
            }
        }
        if (rules.smoothFineBands && bandsPerOctave > 3) {
            val smoothed = value.copyOf()
            for (i in 0 until n) {
                if (flags[i] != CorrectionFlag.NONE) continue
                var sum = 0.0
                var count = 0
                for (j in maxOf(0, i - k)..minOf(n - 1, i + k)) {
                    if (flags[j] == CorrectionFlag.NONE) { sum += value[j]; count++ }
                }
                smoothed[i] = sum / count
            }
            smoothed.copyInto(value)
        }
        for (i in 0 until n) {
            if (flags[i] != CorrectionFlag.NONE) continue
            if (value[i] > rules.maxBoostDb) { value[i] = rules.maxBoostDb; flags[i] = CorrectionFlag.BOOST_LIMITED }
            if (value[i] < -rules.maxCutDb) { value[i] = -rules.maxCutDb; flags[i] = CorrectionFlag.CUT_LIMITED }
            if (abs(value[i]) < rules.deadbandDb) {
                if (raw[i] != 0.0) flags[i] = CorrectionFlag.BELOW_THRESHOLD
                value[i] = 0.0
            }
            value[i] = roundTo(value[i], rules.roundingStepDb)
        }
        return EqSuggestion(List(n) { BandCorrection(items[it].band, raw[it], value[it], flags[it]) }, rules)
    }

    internal fun roundTo(v: Double, step: Double): Double =
        if (step <= 0) v else (v / step).roundToLong() * step + 0.0
}
