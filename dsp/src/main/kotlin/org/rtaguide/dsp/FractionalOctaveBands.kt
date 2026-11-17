package org.rtaguide.dsp

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** One fractional-octave band (IEC 61260 base-10 definition). */
data class Band(
    val index: Int,
    val centerHz: Double,
    val nominalHz: Double,
    val lowHz: Double,
    val highHz: Double,
) {
    val label: String get() = formatFrequency(nominalHz)
}

/** Formats a frequency the way it is printed on equalizers: 31.5, 250, 1k, 12.5k. */
fun formatFrequency(hz: Double): String {
    fun trim(v: Double): String {
        val rounded = (v * 10).roundToLong() / 10.0
        return if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }
    return if (hz >= 1000) trim(hz / 1000) + "k" else trim(hz)
}

/**
 * Set of fractional-octave bands between [minHz] and [maxHz].
 *
 * @param bandsPerOctave 1 (octaves), 3 (third-octaves, the classic 31-band RTA), 6, 12 or 24.
 */
class BandSet(
    val bandsPerOctave: Int = 3,
    val minHz: Double = 20.0,
    val maxHz: Double = 20000.0,
) {
    init {
        require(bandsPerOctave in setOf(1, 2, 3, 6, 12, 24)) { "bandsPerOctave must be 1, 2, 3, 6, 12 or 24" }
        require(minHz > 0 && maxHz > minHz)
    }

    val bands: List<Band> = buildBands()

    val size: Int get() = bands.size

    val centers: DoubleArray get() = DoubleArray(bands.size) { bands[it].centerHz }

    private fun buildBands(): List<Band> {
        val g = 10.0.pow(0.3)
        val b = bandsPerOctave.toDouble()
        fun center(x: Int): Double =
            if (bandsPerOctave % 2 == 1) 1000.0 * g.pow(x / b)
            else 1000.0 * g.pow((2.0 * x + 1.0) / (2.0 * b))
        val tolerance = g.pow(1.0 / (4.0 * b))
        val xMin = floor(b * log10(minHz / 1000.0) / 0.3).toInt() - 2
        val xMax = ceil(b * log10(maxHz / 1000.0) / 0.3).toInt() + 2
        val result = ArrayList<Band>()
        for (x in xMin..xMax) {
            val fm = center(x)
            if (fm < minHz / tolerance || fm > maxHz * tolerance) continue
            val edge = g.pow(1.0 / (2.0 * b))
            result.add(Band(result.size, fm, nominalFor(fm), fm / edge, fm * edge))
        }
        return result
    }

    /**
     * Sums the power of [spectrum] bins into the bands. Bins that straddle a band edge are
     * split proportionally to their overlap.
     */
    fun bandPowers(spectrum: Spectrum): DoubleArray {
        val out = DoubleArray(bands.size)
        var start = 0
        for ((i, band) in bands.withIndex()) {
            while (start < spectrum.size && spectrum.highHz[start] <= band.lowHz) start++
            var j = start
            var sum = 0.0
            while (j < spectrum.size && spectrum.lowHz[j] < band.highHz) {
                val lo = max(spectrum.lowHz[j], band.lowHz)
                val hi = min(spectrum.highHz[j], band.highHz)
                if (hi > lo) sum += spectrum.power[j] * (hi - lo) / spectrum.widthHz(j)
                j++
            }
            out[i] = sum
        }
        return out
    }

    fun bandLevelsDb(spectrum: Spectrum): DoubleArray =
        bandPowers(spectrum).let { p -> DoubleArray(p.size) { Levels.powerToDbFs(p[it]) } }

    companion object {
        /** ISO 266 preferred frequencies (R10 series) for third-octave and octave bands. */
        val ISO_NOMINAL = doubleArrayOf(
            10.0, 12.5, 16.0, 20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0,
            250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0,
            3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0, 25000.0,
        )

        fun nominalFor(exactHz: Double): Double {
            val nearest = ISO_NOMINAL.minByOrNull { abs(log10(it / exactHz)) }!!
            if (abs(log10(nearest / exactHz)) < 0.015) return nearest
            // Not an R10 frequency (1/6, 1/12 octave…): round to 3 significant digits.
            val digits = floor(log10(exactHz)).toInt() - 2
            val factor = 10.0.pow(digits)
            return (exactHz / factor).roundToLong() * factor
        }
    }
}
