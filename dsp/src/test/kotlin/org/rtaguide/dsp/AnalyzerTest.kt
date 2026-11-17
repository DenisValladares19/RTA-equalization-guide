package org.rtaguide.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyzerTest {
    private val fs = 48000

    private fun bandOf(bands: BandSet, nominal: Double) = bands.bands.first { it.nominalHz == nominal }.index

    @Test
    fun fullScaleSineAt1kReadsZeroDbFs() {
        val analyzer = MultiResolutionAnalyzer(fs)
        val bands = BandSet(3)
        val levels = averagedBandLevels(sine(fs, 1000.0, 3.0), analyzer, bands)
        assertEquals(0.0, levels[bandOf(bands, 1000.0)], 0.3)
        // Neighbors are far below (window leakage stays small).
        assertTrue(levels[bandOf(bands, 500.0)] < -60)
        assertTrue(levels[bandOf(bands, 2000.0)] < -60)
    }

    @Test
    fun lowFrequencySineGoesThroughDecimationPath() {
        val analyzer = MultiResolutionAnalyzer(fs)
        val bands = BandSet(3)
        for (hz in listOf(25.0, 50.0, 125.0, 400.0)) {
            analyzer.reset()
            val levels = averagedBandLevels(sine(fs, hz, 5.0, amplitude = 0.1), analyzer, bands)
            assertEquals(-20.0, levels[bandOf(bands, hz)], 0.5, "band $hz Hz")
        }
    }

    @Test
    fun highFrequencySine() {
        val analyzer = MultiResolutionAnalyzer(fs)
        val bands = BandSet(3)
        for (hz in listOf(4000.0, 10000.0, 16000.0)) {
            analyzer.reset()
            val levels = averagedBandLevels(sine(fs, hz, 2.0, amplitude = 0.5), analyzer, bands)
            assertEquals(-6.02, levels[bandOf(bands, hz)], 0.5, "band $hz Hz")
        }
    }

    @Test
    fun spectrumBinsAreContiguousAndSorted() {
        val analyzer = MultiResolutionAnalyzer(fs, 1024, 4)
        analyzer.push(sine(fs, 300.0, 2.0))
        val s = analyzer.computeSpectrum()
        for (i in 1 until s.size) {
            assertTrue(s.centerHz[i] > s.centerHz[i - 1])
            assertEquals(s.highHz[i - 1], s.lowHz[i], 1e-6)
        }
        assertEquals(fs / 2.0, s.highHz.last(), fs / 1024.0)
    }

    @Test
    fun totalPowerMatchesSignalPower() {
        val analyzer = MultiResolutionAnalyzer(fs, 2048, 0, WindowFunction.HANN)
        val noise = PinkNoiseGenerator(-10.0, seed = 1)
        val signal = FloatArray(4096).also { noise.fill(it) }
        analyzer.push(signal)
        val tail = signal.copyOfRange(2048, 4096)
        val meanSquare = tail.sumOf { it.toDouble() * it } / tail.size
        val total = analyzer.computeSpectrum().totalPower()
        assertEquals(meanSquare, total, meanSquare * 0.25)
    }
}
