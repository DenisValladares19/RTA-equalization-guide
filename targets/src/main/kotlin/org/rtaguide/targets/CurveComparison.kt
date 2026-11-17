package org.rtaguide.targets

import kotlinx.serialization.Serializable
import org.rtaguide.dsp.Band
import kotlin.math.abs

/** How the target curve is anchored to the measured level ("level alignment"). */
@Serializable
data class Alignment(
    val mode: Mode = Mode.RANGE,
    val lowHz: Double = 500.0,
    val highHz: Double = 2000.0,
    val frequencyHz: Double = 1000.0,
    /** Manual offset in dB used when [mode] is [Mode.MANUAL]. */
    val manualOffsetDb: Double = 0.0,
) {
    enum class Mode(val label: String) {
        RANGE("Promedio de un rango"),
        FREQUENCY("Una frecuencia"),
        MANUAL("Manual"),
    }
}

enum class BandStatus { OK, WARNING, OUT, IGNORED }

data class BandComparison(
    val band: Band,
    val measuredDb: Double,
    /** Target level after alignment (absolute, same units as [measuredDb]). */
    val targetDb: Double,
    val status: BandStatus,
) {
    /** Positive = too loud (excess), negative = missing. */
    val deviationDb: Double get() = measuredDb - targetDb
}

data class CurveComparison(
    val curve: TargetCurve,
    val toleranceDb: Double,
    /** Offset (dB) added to the curve so that it sits on the measured level. */
    val offsetDb: Double,
    val items: List<BandComparison>,
) {
    val targetDb: DoubleArray get() = DoubleArray(items.size) { items[it].targetDb }
    val okCount: Int get() = items.count { it.status == BandStatus.OK }
    val ratedCount: Int get() = items.count { it.status != BandStatus.IGNORED }

    /** Share (0..1) of rated bands that are within tolerance. */
    val score: Double get() = if (ratedCount == 0) 0.0 else okCount.toDouble() / ratedCount
}

object CurveComparator {
    /**
     * Compares measured band levels with [curve].
     *
     * @param ignored band indices to leave out of the alignment and rating (e.g. low SNR or
     *   outside the system's range); they get [BandStatus.IGNORED].
     * @param ratedRangeHz bands outside this range are [BandStatus.IGNORED] too.
     */
    fun compare(
        bands: List<Band>,
        measuredDb: DoubleArray,
        curve: TargetCurve,
        alignment: Alignment = Alignment(),
        toleranceDb: Double = curve.toleranceDb,
        ignored: Set<Int> = emptySet(),
        ratedRangeHz: ClosedFloatingPointRange<Double> = 0.0..Double.MAX_VALUE,
    ): CurveComparison {
        require(bands.size == measuredDb.size) { "bands and levels differ in size" }
        val relative = DoubleArray(bands.size) { curve.relativeGainAt(bands[it].centerHz) }
        val usable = bands.indices.filter { it !in ignored && bands[it].centerHz in ratedRangeHz }

        val offset = when (alignment.mode) {
            Alignment.Mode.MANUAL -> alignment.manualOffsetDb
            Alignment.Mode.FREQUENCY -> {
                val nearest = usable.minByOrNull { abs(kotlin.math.ln(bands[it].centerHz / alignment.frequencyHz)) }
                if (nearest == null) 0.0 else measuredDb[nearest] - relative[nearest]
            }
            Alignment.Mode.RANGE -> {
                val inRange = usable.filter { bands[it].centerHz in alignment.lowHz..alignment.highHz }
                val pool = inRange.ifEmpty { usable }
                if (pool.isEmpty()) 0.0 else pool.map { measuredDb[it] - relative[it] }.average()
            }
        }

        val items = bands.indices.map { i ->
            val target = relative[i] + offset
            val dev = abs(measuredDb[i] - target)
            val status = when {
                i !in usable -> BandStatus.IGNORED
                dev <= toleranceDb -> BandStatus.OK
                dev <= 2 * toleranceDb -> BandStatus.WARNING
                else -> BandStatus.OUT
            }
            BandComparison(bands[i], measuredDb[i], target, status)
        }
        return CurveComparison(curve, toleranceDb, offset, items)
    }
}
