package org.rtaguide.dsp

import kotlin.math.log10
import kotlin.math.sqrt

/** Frequency weightings from IEC 61672-1. */
enum class Weighting(val label: String) {
    Z("Z (sin ponderación)"),
    A("A"),
    C("C");

    /** Weighting gain in dB at [frequencyHz]. */
    fun gainDb(frequencyHz: Double): Double {
        val f2 = frequencyHz * frequencyHz
        return when (this) {
            Z -> 0.0
            A -> {
                val num = 12194.0 * 12194.0 * f2 * f2
                val den = (f2 + 20.6 * 20.6) *
                    sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9)) *
                    (f2 + 12194.0 * 12194.0)
                20.0 * log10(num / den) + 2.00
            }
            C -> {
                val num = 12194.0 * 12194.0 * f2
                val den = (f2 + 20.6 * 20.6) * (f2 + 12194.0 * 12194.0)
                20.0 * log10(num / den) + 0.06
            }
        }
    }
}
