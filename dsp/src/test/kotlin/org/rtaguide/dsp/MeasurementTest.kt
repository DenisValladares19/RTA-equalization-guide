package org.rtaguide.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementTest {
    @Test
    fun weightingReferenceValues() {
        assertEquals(0.0, Weighting.A.gainDb(1000.0), 0.05)
        assertEquals(-19.1, Weighting.A.gainDb(100.0), 0.1)
        assertEquals(-50.5, Weighting.A.gainDb(20.0), 0.2)
        assertEquals(0.0, Weighting.C.gainDb(1000.0), 0.05)
        assertEquals(-3.0, Weighting.C.gainDb(31.5), 0.1)
        assertEquals(0.0, Weighting.Z.gainDb(50.0))
    }

    @Test
    fun parsesUmikCalibrationFile() {
        val text = """
            "Sens Factor =-1.378dB, AGain =18dB, SERNO: 7001234"
            10.054	-2.5937	 0.0
            100.0	-0.2
            1000	0.0
            10000	1.5
            20000	3.0
        """.trimIndent()
        val cal = MicCalibration.parse(text)
        assertEquals(-1.378, cal.sensitivityFactorDb!!, 1e-9)
        assertEquals("7001234", cal.serialNumber)
        assertEquals(5, cal.points.size)
        assertEquals(0.75, cal.deviationDb(kotlin.math.sqrt(1000.0 * 10000.0)), 1e-9)
        assertEquals(3.0, cal.deviationDb(25000.0))
        val corrected = cal.correct(doubleArrayOf(-10.0), doubleArrayOf(10000.0))
        assertEquals(-11.5, corrected[0], 1e-9)
    }

    @Test
    fun calibratorOffsetAndOverallLevel() {
        val offset = SplMeter.offsetFromCalibrator(-30.0)
        assertEquals(124.0, offset)
        val level = SplMeter.overallLevel(doubleArrayOf(-30.0, -30.0), doubleArrayOf(1000.0, 1000.0), Weighting.Z, offset)
        assertEquals(97.01, level, 0.01)
    }

    @Test
    fun spatialAverageIsPowerAverage() {
        val avg = SpatialAverager()
        assertNull(avg.average())
        avg.add(doubleArrayOf(0.0, -10.0))
        avg.add(doubleArrayOf(-100.0, -10.0))
        val result = avg.average()!!
        assertEquals(-3.01, result[0], 0.01)
        assertEquals(-10.0, result[1], 1e-9)
    }

    @Test
    fun qualityWarnings() {
        val (warnings, lowSnr) = SignalQuality.assess(1.0, -80.0, doubleArrayOf(-50.0, -20.0), doubleArrayOf(-55.0, -60.0))
        assertTrue(QualityWarning.CLIPPING in warnings)
        assertTrue(QualityWarning.LEVEL_TOO_LOW in warnings)
        assertTrue(QualityWarning.LOW_SNR in warnings)
        assertEquals(setOf(0), lowSnr)
    }

    @Test
    fun detectsFeedbackTone() {
        val fs = 48000
        val analyzer = MultiResolutionAnalyzer(fs)
        val detector = FeedbackDetector()
        val noise = PinkNoiseGenerator(-30.0, seed = 9)
        val block = 1024
        var found: List<FeedbackCandidate> = emptyList()
        var t = 0
        repeat(60) {
            val buf = FloatArray(block) { i ->
                (noise.next() + 0.3 * kotlin.math.sin(2 * Math.PI * 2500.0 * (t + i) / fs)).toFloat()
            }
            t += block
            analyzer.push(buf)
            found = detector.update(analyzer.computeSpectrum())
        }
        assertTrue(found.isNotEmpty(), "feedback tone not detected")
        assertEquals(2500.0, found.first().frequencyHz, 2500.0 * 0.02)
    }

    @Test
    fun pinkNoiseAloneIsNotFeedback() {
        val fs = 48000
        val analyzer = MultiResolutionAnalyzer(fs)
        val detector = FeedbackDetector()
        val noise = PinkNoiseGenerator(-20.0, seed = 5)
        var found: List<FeedbackCandidate> = emptyList()
        repeat(60) {
            val buf = FloatArray(1024).also { noise.fill(it) }
            analyzer.push(buf)
            found = detector.update(analyzer.computeSpectrum())
        }
        assertTrue(found.isEmpty(), "false positives: $found")
    }

    @Test
    fun testSignalDetection() {
        val bands = BandSet(3)
        val centers = bands.centers
        // Pink noise through a system: flat-ish in third-octaves (a mild treble roll-off is fine).
        val pink = DoubleArray(centers.size) { if (centers[it] > 4000) -24.0 else -20.0 }
        assertTrue(SignalQuality.hasTestSignal(centers, pink))
        // Ambient noise: strong low end, highs ~25 dB below the mids.
        val ambient = DoubleArray(centers.size) { i ->
            val f = centers[i]
            when {
                f < 300 -> -30.0
                f < 2000 -> -45.0
                else -> -70.0
            }
        }
        assertTrue(!SignalQuality.hasTestSignal(centers, ambient))
        // Flat but barely above the captured background noise: not a usable measurement.
        val floor = DoubleArray(centers.size) { -25.0 }
        assertTrue(!SignalQuality.hasTestSignal(centers, pink, floor))
        assertTrue(SignalQuality.hasTestSignal(centers, pink, DoubleArray(centers.size) { -50.0 }))
    }
}
