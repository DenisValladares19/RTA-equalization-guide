package org.rtaguide.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Low-pass FIR filter followed by downsampling by 2.
 *
 * Windowed-sinc design (Blackman window). With the default cutoff of 0.17·fs the response is
 * flat up to ~0.12·fs and strongly attenuated above ~0.22·fs, which keeps the octave that the
 * next analysis level uses (≤ 0.1·fs of the input rate) free from aliasing.
 */
class Decimator(taps: Int = 63, cutoff: Double = 0.17) {
    private val coefficients: DoubleArray
    private val history: DoubleArray
    private var position = 0
    private var phase = 0

    init {
        require(taps % 2 == 1) { "taps must be odd" }
        val middle = (taps - 1) / 2.0
        val raw = DoubleArray(taps) { i ->
            val m = i - middle
            val sinc = if (m == 0.0) 2.0 * cutoff else sin(2.0 * PI * cutoff * m) / (PI * m)
            val window = 0.42 - 0.5 * cos(2.0 * PI * i / (taps - 1)) + 0.08 * cos(4.0 * PI * i / (taps - 1))
            sinc * window
        }
        val sum = raw.sum()
        coefficients = DoubleArray(taps) { raw[it] / sum }
        history = DoubleArray(taps)
    }

    /**
     * Filters [length] samples of [input] starting at [offset] and writes every second output
     * sample to [output]. Returns the number of samples written.
     */
    fun process(input: DoubleArray, offset: Int, length: Int, output: DoubleArray): Int {
        var written = 0
        val n = history.size
        for (i in offset until offset + length) {
            history[position] = input[i]
            position = (position + 1) % n
            if (phase == 0) {
                var acc = 0.0
                var idx = position
                for (c in coefficients) {
                    acc += c * history[idx]
                    idx++
                    if (idx == n) idx = 0
                }
                output[written++] = acc
            }
            phase = phase xor 1
        }
        return written
    }

    fun reset() {
        history.fill(0.0)
        position = 0
        phase = 0
    }
}
