package org.rtaguide.dsp

import kotlin.math.PI
import kotlin.math.cos

/** FFT window functions (periodic / DFT-even form). */
enum class WindowFunction(val label: String) {
    RECTANGULAR("Rectangular"),
    HANN("Hann"),
    HAMMING("Hamming"),
    BLACKMAN("Blackman"),
    BLACKMAN_HARRIS("Blackman-Harris"),
    FLAT_TOP("Flat-top");

    fun coefficients(n: Int): DoubleArray = DoubleArray(n) { i ->
        val x = 2.0 * PI * i / n
        when (this) {
            RECTANGULAR -> 1.0
            HANN -> 0.5 - 0.5 * cos(x)
            HAMMING -> 0.54 - 0.46 * cos(x)
            BLACKMAN -> 0.42 - 0.5 * cos(x) + 0.08 * cos(2 * x)
            BLACKMAN_HARRIS -> 0.35875 - 0.48829 * cos(x) + 0.14128 * cos(2 * x) - 0.01168 * cos(3 * x)
            FLAT_TOP -> 0.21557895 - 0.41663158 * cos(x) + 0.277263158 * cos(2 * x) -
                0.083578947 * cos(3 * x) + 0.006947368 * cos(4 * x)
        }
    }
}
