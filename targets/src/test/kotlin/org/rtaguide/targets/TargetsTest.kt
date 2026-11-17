package org.rtaguide.targets

import org.rtaguide.dsp.BandSet
import org.rtaguide.targets.session.CsvExport
import org.rtaguide.targets.session.Session
import org.rtaguide.targets.session.SessionBand
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TargetsTest {
    private val bandSet = BandSet(3)
    private val bands = bandSet.bands
    private fun idx(nominal: Double) = bands.first { it.nominalHz == nominal }.index

    @Test
    fun curveInterpolatesInLogFrequency() {
        val c = TargetCurve("t", "t", points = listOf(listOf(100.0, 0.0), listOf(10000.0, -10.0)))
        assertEquals(0.0, c.gainAt(50.0))
        assertEquals(-5.0, c.gainAt(1000.0), 1e-9)
        assertEquals(-10.0, c.gainAt(20000.0))
        assertEquals(0.0, c.relativeGainAt(1000.0), 1e-9)
    }

    @Test
    fun builtInCurvesAreValidAndUnique() {
        assertEquals(8, BuiltInCurves.ALL.size)
        assertEquals(BuiltInCurves.ALL.size, BuiltInCurves.ALL.map { it.id }.toSet().size)
        for (c in BuiltInCurves.ALL) assertEquals(0.0, c.gainAt(1000.0), 1e-9, c.id)
        // X curve: −3 dB/octave above 2 kHz.
        assertEquals(-6.0, BuiltInCurves.X_CURVE.gainAt(8000.0), 1e-9)
        // Bass guide: +16 dB at 31.5 Hz, flat from 500 Hz up.
        assertEquals(16.0, BuiltInCurves.BASS_GUIDE.relativeGainAt(31.5), 1e-9)
        assertEquals(0.0, BuiltInCurves.BASS_GUIDE.relativeGainAt(8000.0), 1e-9)
        assertEquals(4.5, BuiltInCurves.BASS_GUIDE.relativeGainAt(kotlin.math.sqrt(125.0 * 250.0)), 1e-9)
    }

    @Test
    fun curveJsonRoundTrip() {
        val json = BuiltInCurves.LIVE_PA.toJson()
        assertTrue("\"nombre\"" in json && "\"puntos\"" in json)
        assertEquals(BuiltInCurves.LIVE_PA, TargetCurve.fromJson(json))
        val custom = TargetCurve.fromJson(
            """{"id":"mi","nombre":"Mi curva","puntos":[[20,3],[1000,0],[20000,-6]]}""",
        )
        assertEquals(3.0, custom.toleranceDb)
        assertEquals(-3.0, custom.gainAt(kotlin.math.sqrt(1000.0 * 20000.0)), 1e-9)
    }

    @Test
    fun alignmentPutsCurveOnMeasuredLevel() {
        val measured = DoubleArray(bands.size) { -30.0 }
        val cmp = CurveComparator.compare(bands, measured, BuiltInCurves.FLAT)
        assertEquals(-30.0, cmp.offsetDb, 1e-9)
        assertTrue(cmp.items.all { it.status == BandStatus.OK })
        assertEquals(1.0, cmp.score)

        val atFreq = CurveComparator.compare(bands, measured, BuiltInCurves.X_CURVE, Alignment(Alignment.Mode.FREQUENCY, frequencyHz = 1000.0))
        assertEquals(-30.0, atFreq.offsetDb, 1e-9)
        val manual = CurveComparator.compare(bands, measured, BuiltInCurves.FLAT, Alignment(Alignment.Mode.MANUAL, manualOffsetDb = -20.0))
        assertEquals(-10.0, manual.items[idx(1000.0)].deviationDb, 1e-9)
        assertTrue(manual.items.all { it.status == BandStatus.OUT })
    }

    @Test
    fun roomPeakAt125HzSuggestsCut() {
        val measured = DoubleArray(bands.size) { -30.0 }
        measured[idx(125.0)] = -24.0
        val cmp = CurveComparator.compare(bands, measured, BuiltInCurves.FLAT)
        assertEquals(BandStatus.WARNING, cmp.items[idx(125.0)].status)
        val s = EqAdvisor.suggest(cmp)
        val c125 = s.items[idx(125.0)]
        assertEquals(-6.0, c125.correctionDb, 0.51)
        assertEquals(CorrectionFlag.NONE, c125.flag)
        // The other bands need nothing: alignment uses 500 Hz–2 kHz, which the peak does not touch.
        assertTrue(s.items.filter { it.band.index != idx(125.0) }.all { it.correctionDb == 0.0 })
    }

    @Test
    fun deepNarrowDipIsNotBoosted() {
        val measured = DoubleArray(bands.size) { -30.0 }
        measured[idx(80.0)] = -42.0
        val s = EqAdvisor.suggest(CurveComparator.compare(bands, measured, BuiltInCurves.FLAT))
        assertEquals(CorrectionFlag.ACOUSTIC_NULL, s.items[idx(80.0)].flag)
        assertEquals(0.0, s.items[idx(80.0)].correctionDb)
    }

    @Test
    fun boostsAreLimitedAndRangeIsRespected() {
        val measured = DoubleArray(bands.size) { -30.0 }
        // Broad lack of highs: −8 dB from 5 kHz up.
        for (b in bands) if (b.centerHz > 4500) measured[b.index] = -38.0
        val s = EqAdvisor.suggest(CurveComparator.compare(bands, measured, BuiltInCurves.FLAT), EqRules(maxBoostDb = 3.0))
        assertEquals(3.0, s.items[idx(8000.0)].correctionDb)
        assertEquals(CorrectionFlag.BOOST_LIMITED, s.items[idx(8000.0)].flag)
        assertEquals(CorrectionFlag.OUT_OF_RANGE, s.items[idx(20000.0)].flag)
        assertEquals(CorrectionFlag.OUT_OF_RANGE, s.items[idx(25.0)].flag)
    }

    @Test
    fun smallDeviationsAreLeftAlone() {
        val measured = DoubleArray(bands.size) { -30.0 }
        measured[idx(1600.0)] = -29.4
        val s = EqAdvisor.suggest(CurveComparator.compare(bands, measured, BuiltInCurves.FLAT, Alignment(Alignment.Mode.MANUAL, manualOffsetDb = -30.0)))
        assertEquals(0.0, s.items[idx(1600.0)].correctionDb)
        assertEquals(CorrectionFlag.BELOW_THRESHOLD, s.items[idx(1600.0)].flag)
    }

    @Test
    fun lowSnrBandsAreFlagged() {
        val measured = DoubleArray(bands.size) { -30.0 }
        val cmp = CurveComparator.compare(bands, measured, BuiltInCurves.FLAT, ignored = setOf(idx(63.0)))
        val s = EqAdvisor.suggest(cmp, lowSnrBands = setOf(idx(63.0)))
        assertEquals(CorrectionFlag.LOW_SNR, s.items[idx(63.0)].flag)
    }

    @Test
    fun graphicEqLayouts() {
        val measured = DoubleArray(bands.size) { -30.0 }
        measured[idx(250.0)] = -26.0
        val s = EqAdvisor.suggest(CurveComparator.compare(bands, measured, BuiltInCurves.FLAT))
        val g31 = GraphicEqMapper.map(s, GraphicEqLayout.THIRTY_ONE)
        assertEquals(31, g31.size)
        assertEquals(-4.0, g31.first { it.nominalHz == 250.0 }.gainDb)
        assertEquals("31.5", g31[2].label)
        assertEquals(15, GraphicEqMapper.map(s, GraphicEqLayout.FIFTEEN).size)
        val g10 = GraphicEqMapper.map(s, GraphicEqLayout.TEN)
        assertEquals(10, g10.size)
        assertEquals(-4.0, g10.first { it.nominalHz == 250.0 }.gainDb)
        assertEquals(31.62, GraphicEqLayout.exactHz(31.5), 0.01)
    }

    @Test
    fun parametricFitRecoversARoomPeak() {
        val peak = PeakingFilter(125.0, 6.0, 4.0)
        val measured = DoubleArray(bands.size) { -30.0 + peak.magnitudeDb(bands[it].centerHz) }
        val s = EqAdvisor.suggest(CurveComparator.compare(bands, measured, BuiltInCurves.FLAT), EqRules(deadbandDb = 0.5, roundingStepDb = 0.1))
        val filters = ParametricFitter.fit(s, maxFilters = 3)
        assertTrue(filters.isNotEmpty())
        val main = filters.minByOrNull { it.gainDb }!!
        assertTrue(abs(kotlin.math.log2(main.frequencyHz / 125.0)) <= 1.0 / 6.0, "freq ${main.frequencyHz}")
        assertTrue(main.gainDb in -7.5..-4.0, "gain ${main.gainDb}")
        assertTrue(main.q in 1.4..10.0, "q ${main.q}")
        assertTrue(ParametricFitter.rmsError(s, filters) < 1.0)
        // Applying the filters brings the measurement back to flat.
        val after = EqPreview.withParametric(measured, bandSet.centers, filters)
        assertTrue(after.all { abs(it + 30.0) < 1.5 }, after.joinToString())
    }

    @Test
    fun peakingFilterBasics() {
        val f = PeakingFilter(1000.0, -6.0, 1.41)
        assertEquals(-6.0, f.magnitudeDb(1000.0), 0.01)
        assertTrue(abs(f.magnitudeDb(20.0)) < 0.1)
        assertEquals(1.0, f.bandwidthOctaves, 0.01)
    }

    @Test
    fun previewAddsCorrections() {
        val measured = DoubleArray(bands.size) { -30.0 }
        measured[idx(125.0)] = -24.0
        val s = EqAdvisor.suggest(CurveComparator.compare(bands, measured, BuiltInCurves.FLAT))
        val after = EqPreview.withCorrections(measured, s)
        assertEquals(-30.0, after[idx(125.0)], 0.5)
        val g = GraphicEqMapper.map(s)
        val afterG = EqPreview.withGraphic(measured, bandSet.centers, g)
        assertEquals(-30.0, afterG[idx(125.0)], 0.5)
    }

    @Test
    fun curveEditorOperations() {
        var c = CurveEditor.duplicate(BuiltInCurves.FLAT, "mia", "Mía")
        c = CurveEditor.withPoint(c, 100.0, 4.0)
        assertEquals(3, c.points.size)
        assertEquals(4.0, c.gainAt(100.0))
        c = CurveEditor.withPointChanged(c, 1, 120.0, 5.0)
        assertEquals(5.0, c.gainAt(120.0))
        c = CurveEditor.withoutPoint(c, 1)
        assertEquals(2, c.points.size)
        val measured = DoubleArray(bands.size) { -30.0 + if (bands[it].centerHz < 100) 6.0 else 0.0 }
        val fromM = CurveEditor.fromMeasurement("m", "Medida", bands, measured, smoothingBands = 0)
        assertEquals(6.0, fromM.gainAt(50.0), 1e-9)
        assertEquals(0.0, fromM.gainAt(1000.0), 1e-9)
    }

    @Test
    fun sessionJsonAndCsv() {
        val s = Session(
            id = "1", name = "Iglesia \"Central\"", notes = "Posición FOH", createdAtMs = 0, sampleRate = 48000,
            bandsPerOctave = 3, curveId = "voz", curveName = "Voz",
            bands = listOf(SessionBand(1000.0, "1k", -30.0, -31.0, 1.0)),
            graphicEq = listOf(GraphicEqBand("1k", 1000.0, 1.0)),
            parametric = listOf(PeakingFilter(125.0, -4.0, 3.0)),
        )
        assertEquals(s, Session.fromJson(s.toJson()))
        val csv = CsvExport.session(s)
        assertTrue("1000.00,\"1k\",-30.00,-31.00,1.00,1.00" in csv, csv)
        assertTrue("1,125.00,-4.00,3.00" in csv)
        assertTrue("\"\"Central\"\"" !in csv.lines().first()) // header line is a comment, not quoted
    }

    @Test
    fun endToEndWithPinkNoiseThroughAColoredSystem() {
        // Simulated system: pink noise through a +6 dB bump at 100 Hz and a −5 dB dip at 3 kHz.
        val sys = listOf(PeakingFilter(100.0, 6.0, 2.0), PeakingFilter(3000.0, -5.0, 1.5))
        val measured = DoubleArray(bands.size) { -25.0 + ParametricFitter.responseDb(sys, bands[it].centerHz) }
        val cmp = CurveComparator.compare(bands, measured, BuiltInCurves.FLAT)
        val s = EqAdvisor.suggest(cmp, EqRules(maxBoostDb = 6.0))
        val g = GraphicEqMapper.map(s)
        val after = EqPreview.withGraphic(measured, bandSet.centers, g)
        val cmpAfter = CurveComparator.compare(bands, after, BuiltInCurves.FLAT, ratedRangeHz = 40.0..16000.0)
        assertTrue(cmpAfter.items.filter { it.status != BandStatus.IGNORED }.all { abs(it.deviationDb) <= 3.0 })
        assertTrue(cmpAfter.score > cmp.score)
        val rms = sqrt(cmpAfter.items.filter { it.status != BandStatus.IGNORED }.sumOf { it.deviationDb * it.deviationDb } / cmpAfter.ratedCount)
        assertTrue(rms < 1.0, "rms $rms")
    }
}
