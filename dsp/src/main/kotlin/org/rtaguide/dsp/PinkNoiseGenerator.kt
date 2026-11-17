package org.rtaguide.dsp

import kotlin.random.Random

/**
 * Pink noise (−3 dB/octave, equal energy per octave) generator.
 *
 * White noise filtered by Paul Kellet's "refined" filter bank (accurate to about ±0.05 dB
 * above 9 Hz). Measured in fractional-octave bands, pink noise reads flat, which is why it is
 * the standard test signal for equalizing sound systems with an RTA.
 *
 * @param levelDbFs approximate RMS level of the output relative to a full-scale sine.
 */
class PinkNoiseGenerator(levelDbFs: Double = -12.0, seed: Long = System.nanoTime()) {
    private val random = Random(seed)
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0
    private var gain = 0.0

    var levelDbFs: Double = levelDbFs
        set(value) {
            field = value
            gain = gainFor(value)
        }

    init {
        gain = gainFor(levelDbFs)
    }

    private fun gainFor(db: Double): Double {
        // Unscaled Kellet output with uniform white noise in [−1, 1] has an RMS of ≈ 1.75
        // (white RMS 0.577 × filter gain). Target RMS = √0.5 · 10^(dB/20).
        val targetRms = Math.sqrt(Levels.FULL_SCALE_SINE_POWER) * Math.pow(10.0, db / 20.0)
        return targetRms / UNSCALED_RMS
    }

    fun next(): Double {
        val white = random.nextDouble() * 2.0 - 1.0
        b0 = 0.99886 * b0 + white * 0.0555179
        b1 = 0.99332 * b1 + white * 0.0750759
        b2 = 0.96900 * b2 + white * 0.1538520
        b3 = 0.86650 * b3 + white * 0.3104856
        b4 = 0.55000 * b4 + white * 0.5329522
        b5 = -0.7616 * b5 - white * 0.0168980
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
        b6 = white * 0.115926
        return (pink * gain).coerceIn(-1.0, 1.0)
    }

    fun fill(buffer: FloatArray, offset: Int = 0, length: Int = buffer.size - offset) {
        for (i in offset until offset + length) buffer[i] = next().toFloat()
    }

    fun fill(buffer: ShortArray, offset: Int = 0, length: Int = buffer.size - offset) {
        for (i in offset until offset + length) buffer[i] = (next() * 32767.0).toInt().toShort()
    }

    companion object {
        internal const val UNSCALED_RMS = 1.75
    }
}
