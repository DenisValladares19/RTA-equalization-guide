package org.rtaguide.targets

import org.rtaguide.dsp.Band
import org.rtaguide.dsp.interpolateLogFrequency
import kotlin.math.roundToLong

/** Editing helpers for user-defined target curves. */
object CurveEditor {
    fun withPoint(curve: TargetCurve, hz: Double, db: Double): TargetCurve {
        require(hz > 0)
        val points = curve.points.filter { it[0] != hz } + listOf(listOf(hz, db))
        return curve.copy(points = points.sortedBy { it[0] }, builtIn = false)
    }

    fun withPointChanged(curve: TargetCurve, index: Int, hz: Double, db: Double): TargetCurve {
        require(hz > 0)
        val sorted = curve.points.sortedBy { it[0] }.toMutableList()
        require(index in sorted.indices)
        sorted[index] = listOf(hz, db)
        return curve.copy(points = sorted.sortedBy { it[0] }, builtIn = false)
    }

    fun withoutPoint(curve: TargetCurve, index: Int): TargetCurve {
        val sorted = curve.points.sortedBy { it[0] }
        require(sorted.size > 1) { "a curve needs at least one point" }
        require(index in sorted.indices)
        return curve.copy(points = sorted.filterIndexed { i, _ -> i != index }, builtIn = false)
    }

    /** Copies a curve to a new editable curve. */
    fun duplicate(curve: TargetCurve, id: String, name: String): TargetCurve =
        curve.copy(id = id, name = name, builtIn = false)

    /**
     * Builds a target from a measurement ("I want it to sound like this system"):
     * smoothed over ±[smoothingBands] bands and normalized to 0 dB at [referenceHz].
     */
    fun fromMeasurement(
        id: String,
        name: String,
        bands: List<Band>,
        levelsDb: DoubleArray,
        referenceHz: Double = 1000.0,
        smoothingBands: Int = 1,
        toleranceDb: Double = 3.0,
    ): TargetCurve {
        require(bands.size == levelsDb.size && bands.isNotEmpty())
        val smoothed = DoubleArray(levelsDb.size) { i ->
            val from = maxOf(0, i - smoothingBands)
            val to = minOf(levelsDb.size - 1, i + smoothingBands)
            (from..to).map { levelsDb[it] }.average()
        }
        val ref = interpolateLogFrequency(bands.map { it.centerHz }, smoothed.toList(), referenceHz)
        val points = bands.indices.map { i ->
            listOf(bands[i].nominalHz, ((smoothed[i] - ref) * 10).roundToLong() / 10.0)
        }
        return TargetCurve(
            id = id, name = name, description = "Creada a partir de una medición",
            referenceHz = referenceHz, toleranceDb = toleranceDb, points = points, builtIn = false,
        )
    }
}
