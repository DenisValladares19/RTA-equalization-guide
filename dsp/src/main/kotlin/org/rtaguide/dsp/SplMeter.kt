package org.rtaguide.dsp

import kotlin.math.pow

/**
 * Converts dBFS to dB SPL with a calibration offset and computes weighted overall levels.
 *
 * `dB SPL = dBFS + offsetDb`. The offset comes from a 94 dB SPL acoustic calibrator
 * ([offsetFromCalibrator]) or is entered manually (e.g. from a reference sound level meter).
 */
object SplMeter {
    const val CALIBRATOR_SPL = 94.0

    fun offsetFromCalibrator(measuredDbFs: Double, calibratorSpl: Double = CALIBRATOR_SPL): Double =
        calibratorSpl - measuredDbFs

    /** Overall weighted level from band levels (dB) at [centersHz]. */
    fun overallLevel(levelsDb: DoubleArray, centersHz: DoubleArray, weighting: Weighting, offsetDb: Double = 0.0): Double {
        var sum = 0.0
        for (i in levelsDb.indices) {
            sum += 10.0.pow((levelsDb[i] + weighting.gainDb(centersHz[i])) / 10.0)
        }
        return if (sum <= 0) Levels.FLOOR_DB else 10.0 * kotlin.math.log10(sum) + offsetDb
    }
}

/**
 * Spatial averaging: combines measurements taken at several listening positions.
 * Averaging is done in the power domain, as in professional RTA practice.
 */
class SpatialAverager {
    private val positions = ArrayList<DoubleArray>()

    val count: Int get() = positions.size

    fun add(levelsDb: DoubleArray) {
        require(positions.isEmpty() || positions[0].size == levelsDb.size) { "band count mismatch" }
        positions.add(levelsDb.copyOf())
    }

    fun removeLast() {
        if (positions.isNotEmpty()) positions.removeAt(positions.size - 1)
    }

    fun clear() = positions.clear()

    fun snapshot(): List<DoubleArray> = positions.map { it.copyOf() }

    /** Power average of all positions, or null if none were captured. */
    fun average(): DoubleArray? {
        if (positions.isEmpty()) return null
        val n = positions[0].size
        return DoubleArray(n) { i ->
            val mean = positions.sumOf { 10.0.pow(it[i] / 10.0) } / positions.size
            10.0 * kotlin.math.log10(mean)
        }
    }
}
