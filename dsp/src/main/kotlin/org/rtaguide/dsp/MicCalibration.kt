package org.rtaguide.dsp

import kotlin.math.ln

/** A point of a microphone frequency-response calibration file. */
data class CalibrationPoint(val hz: Double, val db: Double)

/**
 * Microphone calibration (frequency response deviation of the microphone).
 *
 * Reads the plain-text format used by miniDSP UMIK-1/UMIK-2, Dayton Audio iMM-6/EMM-6 and
 * REW: one "frequency  dB  [phase]" pair per line; comment lines start with `*`, `#`, `;`
 * or `"`. A header such as `"Sens Factor =-1.378dB, SERNO: 7001234"` is parsed when present.
 *
 * The measured level is corrected by subtracting the microphone deviation:
 * `corrected = measured − deviation(f)`.
 */
class MicCalibration(
    points: List<CalibrationPoint>,
    val name: String = "Calibración",
    val sensitivityFactorDb: Double? = null,
    val serialNumber: String? = null,
) {
    val points: List<CalibrationPoint> = points.sortedBy { it.hz }

    init {
        require(this.points.isNotEmpty()) { "calibration needs at least one point" }
    }

    /** Microphone deviation in dB at [hz] (log-frequency interpolation, clamped at the ends). */
    fun deviationDb(hz: Double): Double = interpolateLogFrequency(points.map { it.hz }, points.map { it.db }, hz)

    /** Applies the correction to band levels measured at [centersHz]. */
    fun correct(levelsDb: DoubleArray, centersHz: DoubleArray): DoubleArray =
        DoubleArray(levelsDb.size) { levelsDb[it] - deviationDb(centersHz[it]) }

    companion object {
        private val sensRegex = Regex("""Sens\s*Factor\s*=\s*(-?[0-9.]+)\s*dB""", RegexOption.IGNORE_CASE)
        private val serialRegex = Regex("""SERNO:\s*([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)

        fun parse(text: String, name: String = "Calibración"): MicCalibration {
            var sens: Double? = null
            var serial: String? = null
            val points = ArrayList<CalibrationPoint>()
            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (line[0] in "*#;\"" || line[0].isLetter()) {
                    sensRegex.find(line)?.let { sens = it.groupValues[1].toDoubleOrNull() }
                    serialRegex.find(line)?.let { serial = it.groupValues[1] }
                    continue
                }
                val parts = line.split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }
                if (parts.size < 2) continue
                val hz = parts[0].toDoubleOrNull() ?: continue
                val db = parts[1].toDoubleOrNull() ?: continue
                if (hz > 0) points.add(CalibrationPoint(hz, db))
            }
            require(points.isNotEmpty()) { "No se encontraron datos \"frecuencia dB\" en el archivo." }
            return MicCalibration(points, name, sens, serial)
        }
    }
}

/** Linear interpolation over log-frequency; values beyond the ends are clamped. */
fun interpolateLogFrequency(xsHz: List<Double>, ys: List<Double>, hz: Double): Double {
    require(xsHz.size == ys.size && xsHz.isNotEmpty())
    if (hz <= xsHz.first()) return ys.first()
    if (hz >= xsHz.last()) return ys.last()
    var lo = 0
    var hi = xsHz.size - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) / 2
        if (xsHz[mid] <= hz) lo = mid else hi = mid
    }
    val t = (ln(hz) - ln(xsHz[lo])) / (ln(xsHz[hi]) - ln(xsHz[lo]))
    return ys[lo] + t * (ys[hi] - ys[lo])
}
