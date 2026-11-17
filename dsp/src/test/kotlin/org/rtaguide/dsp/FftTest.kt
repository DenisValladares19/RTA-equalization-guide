package org.rtaguide.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FftTest {
    @Test
    fun impulseGivesFlatSpectrum() {
        val fft = Fft(16)
        val re = DoubleArray(16).also { it[0] = 1.0 }
        val im = DoubleArray(16)
        fft.transform(re, im)
        for (k in 0 until 16) assertEquals(1.0, hypot(re[k], im[k]), 1e-12)
    }

    @Test
    fun cosineLandsInItsBin() {
        val n = 256
        val fft = Fft(n)
        val re = DoubleArray(n) { cos(2 * PI * 10 * it / n) }
        val im = DoubleArray(n)
        fft.transform(re, im)
        assertEquals(n / 2.0, hypot(re[10], im[10]), 1e-9)
        for (k in 0 until n / 2) if (k != 10) assertTrue(hypot(re[k], im[k]) < 1e-9)
    }

    @Test
    fun rejectsNonPowerOfTwo() {
        assertFailsWith<IllegalArgumentException> { Fft(100) }
    }

    @Test
    fun parsevalHolds() {
        val n = 512
        val x = DoubleArray(n) { kotlin.math.sin(it * 0.37) + 0.3 * cos(it * 1.9) }
        val re = x.copyOf()
        val im = DoubleArray(n)
        Fft(n).transform(re, im)
        val timeEnergy = x.sumOf { it * it }
        val freqEnergy = (0 until n).sumOf { re[it] * re[it] + im[it] * im[it] } / n
        assertTrue(abs(timeEnergy - freqEnergy) < 1e-9 * timeEnergy)
    }
}
