package org.rtaguide.dsp

import kotlin.math.max
import kotlin.math.min

/**
 * Multi-resolution spectrum analyzer.
 *
 * The input is analyzed at the full sample rate and at [decimations] successively
 * half-rate copies (low-pass + ÷2). Every level runs an FFT of the same [fftSize], so each
 * lower level has twice the frequency resolution of the one above. High frequencies come
 * from the full-rate FFT and low frequencies from the decimated ones, which gives
 * near-constant resolution per octave, like an RTA, without one huge FFT.
 *
 * Frequency coverage per level L (fs_L = sampleRate / 2^L):
 *  - level 0: (0.1·fs_0, 0.5·fs_0]
 *  - intermediate: (0.1·fs_L, 0.2·fs_L]
 *  - last level: (0, 0.2·fs_L]   (or (0, 0.5·fs] if there are no decimations)
 *
 * Not thread-safe: call [push] and [computeSpectrum] from the same thread, or synchronize.
 */
class MultiResolutionAnalyzer(
    val sampleRate: Int,
    val fftSize: Int = 2048,
    val decimations: Int = 5,
    window: WindowFunction = WindowFunction.BLACKMAN_HARRIS,
    var removeDc: Boolean = true,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(fftSize >= 64 && fftSize and (fftSize - 1) == 0) { "fftSize must be a power of 2 ≥ 64" }
        require(decimations in 0..10) { "decimations must be within 0..10" }
    }

    private class Level(size: Int) {
        val ring = DoubleArray(size)
        var writePos = 0
        var filled = 0L
    }

    private val levels = Array(decimations + 1) { Level(fftSize) }
    private val decimators = Array(decimations) { Decimator() }
    private val fft = Fft(fftSize)
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private var scratch = Array(decimations + 1) { DoubleArray(0) }

    private var dcPrevIn = 0.0
    private var dcPrevOut = 0.0
    private val dcR = 1.0 - 2.0 * Math.PI * 1.0 / sampleRate // ~1 Hz high-pass

    var window: WindowFunction = window
        set(value) {
            field = value
            windowCoefficients = value.coefficients(fftSize)
            windowPowerSum = windowCoefficients.sumOf { it * it }
        }

    private var windowCoefficients = window.coefficients(fftSize)
    private var windowPowerSum = windowCoefficients.sumOf { it * it }

    /** Fraction (0..1) of the slowest level's buffer that already holds real audio. */
    val warmup: Double
        get() = min(1.0, levels.last().filled.toDouble() / fftSize)

    /** Frequency resolution (Hz per bin) of level [level]. */
    fun binWidthHz(level: Int): Double = sampleRate.toDouble() / (1 shl level) / fftSize

    /** Time span (seconds) covered by the slowest (finest) level. */
    val slowestWindowSeconds: Double
        get() = fftSize.toDouble() * (1 shl decimations) / sampleRate

    fun push(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset) {
        if (scratch[0].size < length) {
            scratch = Array(decimations + 1) { DoubleArray(length) }
        }
        val top = scratch[0]
        for (i in 0 until length) {
            val x = samples[offset + i].toDouble()
            if (removeDc) {
                val y = x - dcPrevIn + dcR * dcPrevOut
                dcPrevIn = x
                dcPrevOut = y
                top[i] = y
            } else {
                top[i] = x
            }
        }
        var count = length
        for (l in 0..decimations) {
            write(levels[l], scratch[l], count)
            if (l < decimations) {
                count = decimators[l].process(scratch[l], 0, count, scratch[l + 1])
            }
        }
    }

    private fun write(level: Level, data: DoubleArray, count: Int) {
        val ring = level.ring
        for (i in 0 until count) {
            ring[level.writePos] = data[i]
            level.writePos++
            if (level.writePos == ring.size) level.writePos = 0
        }
        level.filled += count
    }

    fun reset() {
        for (level in levels) {
            level.ring.fill(0.0)
            level.writePos = 0
            level.filled = 0
        }
        decimators.forEach { it.reset() }
        dcPrevIn = 0.0
        dcPrevOut = 0.0
    }

    /** Computes the current spectrum from the most recent [fftSize] samples of every level. */
    fun computeSpectrum(): Spectrum {
        val centers = ArrayList<Double>()
        val lows = ArrayList<Double>()
        val highs = ArrayList<Double>()
        val powers = ArrayList<Double>()
        val scale = 2.0 / (fftSize * windowPowerSum)

        for (l in decimations downTo 0) {
            val level = levels[l]
            val fsL = sampleRate.toDouble() / (1 shl l)
            val binWidth = fsL / fftSize
            val lower = if (l == decimations) 0.0 else 0.1 * fsL
            val upper = if (l == 0) 0.5 * fsL else 0.2 * fsL

            // Chronological copy of the ring buffer, windowed.
            var idx = level.writePos
            for (i in 0 until fftSize) {
                re[i] = level.ring[idx] * windowCoefficients[i]
                im[i] = 0.0
                idx++
                if (idx == fftSize) idx = 0
            }
            fft.transform(re, im)

            for (k in 1 until fftSize / 2) {
                val f = k * binWidth
                val lo = max(f - binWidth / 2, lower)
                val hi = min(f + binWidth / 2, upper)
                if (hi <= lo) continue
                val fraction = (hi - lo) / binWidth
                val p = (re[k] * re[k] + im[k] * im[k]) * scale * fraction
                centers.add(if (fraction < 1.0) (lo + hi) / 2 else f)
                lows.add(lo)
                highs.add(hi)
                powers.add(p)
            }
        }
        return Spectrum(
            centers.toDoubleArray(), lows.toDoubleArray(), highs.toDoubleArray(), powers.toDoubleArray(),
        )
    }
}
