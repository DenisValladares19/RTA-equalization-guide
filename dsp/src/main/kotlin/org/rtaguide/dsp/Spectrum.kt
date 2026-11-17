package org.rtaguide.dsp

import kotlin.math.pow

/**
 * One-sided power spectrum made of contiguous frequency bins, sorted by frequency.
 *
 * Each bin holds the mean-square power (full-scale units) contained in [lowHz, highHz).
 * Bins can have different widths (multi-resolution analysis), so per-bin power must be
 * normalized before plotting; see [levelAsFractionalOctaveDb].
 */
class Spectrum(
    val centerHz: DoubleArray,
    val lowHz: DoubleArray,
    val highHz: DoubleArray,
    val power: DoubleArray,
) {
    init {
        require(centerHz.size == lowHz.size && lowHz.size == highHz.size && highHz.size == power.size)
    }

    val size: Int get() = power.size

    fun widthHz(i: Int): Double = highHz[i] - lowHz[i]

    /** Total mean-square power of the spectrum. */
    fun totalPower(): Double = power.sum()

    /**
     * Level of bin [i] expressed as the level a 1/[fraction]-octave band centered on the bin
     * would read if the spectrum were locally flat. Pink noise therefore plots flat, matching
     * the band (bar) view.
     */
    fun levelAsFractionalOctaveDb(i: Int, fraction: Int = 3): Double {
        val f = centerHz[i]
        val half = 2.0.pow(1.0 / (2.0 * fraction))
        val bandWidth = f * (half - 1.0 / half)
        val density = power[i] / widthHz(i)
        return Levels.powerToDbFs(density * bandWidth)
    }
}
