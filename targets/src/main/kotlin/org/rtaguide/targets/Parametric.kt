package org.rtaguide.targets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.rtaguide.dsp.interpolateLogFrequency
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Peaking (bell) filter, as found on every parametric EQ (RBJ Audio EQ Cookbook). */
@Serializable
data class PeakingFilter(
    @SerialName("frecuencia_hz") val frequencyHz: Double,
    @SerialName("ganancia_db") val gainDb: Double,
    @SerialName("q") val q: Double,
) {
    /** Bandwidth in octaves equivalent to [q]. */
    val bandwidthOctaves: Double
        get() {
            val x = 1.0 / (2.0 * q)
            return 2.0 / kotlin.math.ln(2.0) * kotlin.math.asinh(x)
        }

    fun magnitudeDb(hz: Double, sampleRate: Double = 48000.0): Double {
        val w = 2.0 * PI * hz / sampleRate
        return magnitudeDb(cos(w), sin(w), cos(2 * w), sin(2 * w), sampleRate)
    }

    internal fun magnitudeDb(cw: Double, sw: Double, c2w: Double, s2w: Double, sampleRate: Double): Double {
        if (gainDb == 0.0) return 0.0
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequencyHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cos0 = cos(w0)
        val b0 = 1 + alpha * a
        val b1 = -2 * cos0
        val b2 = 1 - alpha * a
        val a0 = 1 + alpha / a
        val a1 = -2 * cos0
        val a2 = 1 - alpha / a
        val nr = b0 + b1 * cw + b2 * c2w
        val ni = -(b1 * sw + b2 * s2w)
        val dr = a0 + a1 * cw + a2 * c2w
        val di = -(a1 * sw + a2 * s2w)
        return 10.0 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }
}

/**
 * Approximates a band correction curve with a few peaking filters (greedy fit).
 *
 * At each step the largest remaining error is attacked with the frequency / Q / gain
 * combination that reduces the squared error most; it stops at [maxFilters] or when the
 * remaining error is below the rules' deadband.
 */
object ParametricFitter {
    private val Q_CANDIDATES = doubleArrayOf(0.5, 0.7, 1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 10.0)
    private val GAIN_SCALES = doubleArrayOf(0.7, 0.85, 1.0, 1.15)

    fun fit(suggestion: EqSuggestion, maxFilters: Int = 6, sampleRate: Double = 48000.0): List<PeakingFilter> {
        val rules = suggestion.rules
        val lo = maxOf(20.0, rules.lowLimitHz)
        val hi = minOf(20000.0, rules.highLimitHz, sampleRate * 0.45)
        if (hi <= lo || suggestion.items.isEmpty()) return emptyList()
        val step = 2.0.pow(1.0 / 24.0)
        val grid = generateSequence(lo) { it * step }.takeWhile { it <= hi * 1.0001 }.toList().toDoubleArray()
        val xs = suggestion.centersHz.toList()
        val ys = suggestion.corrections.toList()
        val residual = DoubleArray(grid.size) { interpolateLogFrequency(xs, ys, grid[it]) }
        val cw = DoubleArray(grid.size) { cos(2 * PI * grid[it] / sampleRate) }
        val sw = DoubleArray(grid.size) { sin(2 * PI * grid[it] / sampleRate) }
        val c2w = DoubleArray(grid.size) { cos(4 * PI * grid[it] / sampleRate) }
        val s2w = DoubleArray(grid.size) { sin(4 * PI * grid[it] / sampleRate) }

        fun response(f: PeakingFilter) = DoubleArray(grid.size) { f.magnitudeDb(cw[it], sw[it], c2w[it], s2w[it], sampleRate) }
        fun sse(r: DoubleArray) = r.sumOf { it * it }

        val filters = ArrayList<PeakingFilter>()
        repeat(maxFilters) {
            val peak = residual.indices.maxByOrNull { abs(residual[it]) } ?: return@repeat
            if (abs(residual[peak]) < maxOf(rules.deadbandDb, 0.5)) return@repeat
            val current = sse(residual)
            var best: PeakingFilter? = null
            var bestResponse: DoubleArray? = null
            var bestError = current
            for (shift in -2..2) {
                val idx = (peak + shift).coerceIn(0, grid.size - 1)
                for (q in Q_CANDIDATES) for (s in GAIN_SCALES) {
                    val gain = (residual[peak] * s).coerceIn(-rules.maxCutDb, rules.maxBoostDb)
                    val candidate = PeakingFilter(grid[idx], gain, q)
                    val resp = response(candidate)
                    var err = 0.0
                    for (i in grid.indices) { val d = residual[i] - resp[i]; err += d * d }
                    if (err < bestError) { bestError = err; best = candidate; bestResponse = resp }
                }
            }
            if (best == null || bestError > current * 0.97) return@repeat
            filters.add(best)
            for (i in grid.indices) residual[i] -= bestResponse!![i]
        }
        return filters.map { round(it) }.sortedBy { it.frequencyHz }
    }

    private fun round(f: PeakingFilter): PeakingFilter {
        val digits = kotlin.math.floor(log10(f.frequencyHz)).toInt() - 2
        val factor = 10.0.pow(digits)
        return PeakingFilter(
            frequencyHz = (f.frequencyHz / factor).roundToLong() * factor,
            gainDb = (f.gainDb * 10).roundToLong() / 10.0,
            q = (f.q * 10).roundToLong() / 10.0,
        )
    }

    /** Combined response (dB) of a set of filters at [hz]. */
    fun responseDb(filters: List<PeakingFilter>, hz: Double, sampleRate: Double = 48000.0): Double =
        filters.sumOf { it.magnitudeDb(hz, sampleRate) }

    /** RMS error (dB) between the fitted filters and the band corrections in range. */
    fun rmsError(suggestion: EqSuggestion, filters: List<PeakingFilter>): Double {
        val inRange = suggestion.items.filter { it.band.centerHz in suggestion.rules.lowLimitHz..suggestion.rules.highLimitHz }
        if (inRange.isEmpty()) return 0.0
        val sum = inRange.sumOf { val d = it.correctionDb - responseDb(filters, it.band.centerHz); d * d }
        return sqrt(sum / inRange.size)
    }
}
