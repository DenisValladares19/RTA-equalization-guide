package org.rtaguide.dsp

import kotlin.math.PI
import kotlin.math.sin

internal fun sine(sampleRate: Int, hz: Double, seconds: Double, amplitude: Double = 1.0): FloatArray {
    val n = (sampleRate * seconds).toInt()
    return FloatArray(n) { (amplitude * sin(2 * PI * hz * it / sampleRate)).toFloat() }
}

/** Pushes [signal] in blocks and returns the infinitely averaged band levels. */
internal fun averagedBandLevels(
    signal: FloatArray,
    analyzer: MultiResolutionAnalyzer,
    bands: BandSet,
    block: Int = 2048,
    skipSeconds: Double = analyzer.slowestWindowSeconds,
): DoubleArray {
    val averager = PowerAverager(AveragingMode.INFINITE)
    var pos = 0
    var result: DoubleArray? = null
    val skip = (skipSeconds * analyzer.sampleRate).toInt()
    while (pos + block <= signal.size) {
        analyzer.push(signal, pos, block)
        pos += block
        if (pos >= skip) result = averager.update(bands.bandPowers(analyzer.computeSpectrum()), 0.0)
    }
    return DoubleArray(result!!.size) { Levels.powerToDbFs(result[it]) }
}
