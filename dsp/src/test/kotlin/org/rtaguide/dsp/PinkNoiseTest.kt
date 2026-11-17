package org.rtaguide.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinkNoiseTest {
    @Test
    fun pinkNoiseIsFlatInThirdOctaves() {
        val fs = 48000
        val signal = FloatArray(fs * 30).also { PinkNoiseGenerator(-12.0, seed = 42).fill(it) }
        val bands = BandSet(3)
        val levels = averagedBandLevels(signal, MultiResolutionAnalyzer(fs), bands)
        val inRange = bands.bands.filter { it.nominalHz in 31.5..16000.0 }.map { levels[it.index] }
        val mean = inRange.average()
        val worst = inRange.maxOf { abs(it - mean) }
        assertTrue(worst < 1.5, "pink noise deviates $worst dB from flat: $inRange")
    }

    @Test
    fun outputLevelMatchesRequestedRms() {
        val gen = PinkNoiseGenerator(-20.0, seed = 3)
        val n = 48000 * 10
        var sum = 0.0
        repeat(n) { val x = gen.next(); sum += x * x }
        assertEquals(-20.0, Levels.powerToDbFs(sum / n), 0.5)
    }

    @Test
    fun wavHeaderIsValid() {
        val bytes = WavWriter.pinkNoise(48000, 1, -12.0)
        assertEquals(44 + 48000 * 2, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals("data", String(bytes, 36, 4))
    }
}
