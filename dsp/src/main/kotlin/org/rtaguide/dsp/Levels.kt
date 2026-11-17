package org.rtaguide.dsp

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Level conversions.
 *
 * Power values are mean-square values of a signal normalized to full scale (±1.0).
 * A full-scale sine (amplitude 1.0) has a mean-square power of 0.5 and reads 0 dBFS.
 */
object Levels {
    const val FULL_SCALE_SINE_POWER = 0.5
    const val FLOOR_DB = -200.0

    fun powerToDbFs(power: Double): Double =
        if (power <= 0.0) FLOOR_DB else max(FLOOR_DB, 10.0 * log10(power / FULL_SCALE_SINE_POWER))

    fun dbFsToPower(db: Double): Double = FULL_SCALE_SINE_POWER * 10.0.pow(db / 10.0)

    /** Energy sum of levels in dB: 10·log10(Σ 10^(Lᵢ/10)). */
    fun sumDb(levels: DoubleArray): Double {
        var sum = 0.0
        for (l in levels) sum += 10.0.pow(l / 10.0)
        return if (sum <= 0.0) FLOOR_DB else 10.0 * log10(sum)
    }
}
