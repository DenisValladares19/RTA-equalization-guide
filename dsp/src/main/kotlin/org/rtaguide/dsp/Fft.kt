package org.rtaguide.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 complex FFT.
 *
 * The transform is unnormalized: X[k] = Σ x[n]·e^(−2πi·k·n/N).
 */
class Fft(val size: Int) {
    init {
        require(size >= 2 && size and (size - 1) == 0) { "size must be a power of 2 (got $size)" }
    }

    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2.0 * PI * it / size) }
    private val bitReversed = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun transform(re: DoubleArray, im: DoubleArray) {
        require(re.size == size && im.size == size) { "arrays must have length $size" }
        for (i in 0 until size) {
            val j = bitReversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = -sinTable[k]
                    val o = j + half
                    val xr = re[o] * wr - im[o] * wi
                    val xi = re[o] * wi + im[o] * wr
                    re[o] = re[j] - xr
                    im[o] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += length
            }
            length *= 2
        }
    }
}
