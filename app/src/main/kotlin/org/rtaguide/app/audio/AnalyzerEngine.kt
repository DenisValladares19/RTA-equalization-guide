package org.rtaguide.app.audio

import org.rtaguide.app.data.AppSettings
import org.rtaguide.dsp.Band
import org.rtaguide.dsp.BandSet
import org.rtaguide.dsp.FeedbackCandidate
import org.rtaguide.dsp.FeedbackDetector
import org.rtaguide.dsp.Levels
import org.rtaguide.dsp.MaxHold
import org.rtaguide.dsp.MicCalibration
import org.rtaguide.dsp.MultiResolutionAnalyzer
import org.rtaguide.dsp.PowerAverager
import org.rtaguide.dsp.Spectrum
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/** One analysis result, published to the UI a few dozen times per second. Levels are dBFS. */
class Frame(
    val sampleRate: Int,
    val bands: List<Band>,
    val bandsPerOctave: Int,
    val instantDb: DoubleArray,
    val averagedDb: DoubleArray,
    val maxHoldDb: DoubleArray,
    val fineHz: DoubleArray,
    val fineDb: DoubleArray,
    val overallDbFs: Double,
    val peak: Double,
    val warmup: Double,
    val averagedFrames: Long,
    val feedback: List<FeedbackCandidate>,
)

/** Glue between the capture thread and the DSP: analysis, bands, averaging, calibration. */
class AnalyzerEngine(settings: AppSettings, sampleRate: Int) {
    private val lock = Any()
    private var settings = settings
    private var sampleRate = sampleRate
    private var analyzer = build(settings, sampleRate)
    private var bandSet = BandSet(settings.bandsPerOctave)
    private val averager = PowerAverager(settings.averagingEnum)
    private val maxHold = MaxHold()
    private val feedback = FeedbackDetector()
    private var samplesSinceFrame = 0
    private var peakSinceFrame = 0f

    @Volatile
    var calibration: MicCalibration? = null

    val bands: List<Band> get() = synchronized(lock) { bandSet.bands }

    private fun build(s: AppSettings, rate: Int) =
        MultiResolutionAnalyzer(rate, s.fftSize, s.decimations, s.windowEnum, s.removeDc)

    fun configure(newSettings: AppSettings, newSampleRate: Int = sampleRate) = synchronized(lock) {
        val rebuild = newSettings.needsEngineRebuild(settings) || newSampleRate != sampleRate
        settings = newSettings
        sampleRate = newSampleRate
        if (rebuild) {
            analyzer = build(newSettings, newSampleRate)
            bandSet = BandSet(newSettings.bandsPerOctave)
            resetLocked()
        } else {
            analyzer.window = newSettings.windowEnum
            analyzer.removeDc = newSettings.removeDc
            if (averager.mode != newSettings.averagingEnum) {
                averager.mode = newSettings.averagingEnum
                averager.reset()
            }
        }
        Unit
    }

    fun resetAveraging() = synchronized(lock) {
        averager.reset()
        maxHold.reset()
        feedback.reset()
    }

    private fun resetLocked() {
        averager.reset()
        maxHold.reset()
        feedback.reset()
        samplesSinceFrame = 0
    }

    /** Feeds audio; returns a new frame when the update interval has elapsed. */
    fun process(block: FloatArray, length: Int, peak: Float): Frame? = synchronized(lock) {
        analyzer.push(block, 0, length)
        samplesSinceFrame += length
        peakSinceFrame = max(peakSinceFrame, peak)
        val interval = sampleRate * settings.updateIntervalMs / 1000
        if (samplesSinceFrame < interval) return null
        val dt = samplesSinceFrame.toDouble() / sampleRate
        samplesSinceFrame = 0
        val framePeak = peakSinceFrame.toDouble()
        peakSinceFrame = 0f

        val spectrum = analyzer.computeSpectrum()
        val powers = bandSet.bandPowers(spectrum)
        val averaged = averager.update(powers, dt)
        val centers = bandSet.centers
        val cal = if (settings.useCalibration) calibration else null
        var instantDb = DoubleArray(powers.size) { Levels.powerToDbFs(powers[it]) }
        var averagedDb = DoubleArray(averaged.size) { Levels.powerToDbFs(averaged[it]) }
        if (cal != null) {
            instantDb = cal.correct(instantDb, centers)
            averagedDb = cal.correct(averagedDb, centers)
        }
        val hold = maxHold.update(averagedDb)
        val (fineHz, fineDb) = reduceForDisplay(spectrum, settings.bandsPerOctave, cal)
        val fb = if (settings.feedbackDetection) feedback.update(spectrum) else emptyList()

        Frame(
            sampleRate = sampleRate,
            bands = bandSet.bands,
            bandsPerOctave = settings.bandsPerOctave,
            instantDb = instantDb,
            averagedDb = averagedDb,
            maxHoldDb = hold,
            fineHz = fineHz,
            fineDb = fineDb,
            overallDbFs = Levels.sumDb(averagedDb),
            peak = framePeak,
            warmup = analyzer.warmup,
            averagedFrames = averager.frames,
            feedback = fb,
        )
    }

    /** Keeps the maximum per 1/48-octave bucket so the fine line stays light to draw. */
    private fun reduceForDisplay(s: Spectrum, fraction: Int, cal: MicCalibration?): Pair<DoubleArray, DoubleArray> {
        val buckets = 48 * 10 + 1
        val hz = DoubleArray(buckets)
        val db = DoubleArray(buckets) { Double.NEGATIVE_INFINITY }
        for (i in 0 until s.size) {
            val f = s.centerHz[i]
            if (f < 20.0 || f > 20000.0) continue
            val b = (48.0 * ln(f / 20.0) / ln(2.0)).toInt().coerceIn(0, buckets - 1)
            var level = s.levelAsFractionalOctaveDb(i, fraction)
            if (cal != null) level -= cal.deviationDb(f)
            if (level > db[b]) {
                db[b] = level
                hz[b] = f
            }
        }
        val used = (0 until buckets).filter { db[it] > Double.NEGATIVE_INFINITY }
        return DoubleArray(used.size) { hz[used[it]] } to DoubleArray(used.size) { db[used[it]] }
    }

    companion object {
        /** Linear-in-log-frequency interpolation of [values] defined at [fromHz] onto [toHz]. */
        fun resample(fromHz: DoubleArray, values: DoubleArray, toHz: DoubleArray): DoubleArray {
            if (fromHz.isEmpty()) return DoubleArray(toHz.size) { Levels.FLOOR_DB }
            val xs = fromHz.toList()
            val ys = values.toList()
            return DoubleArray(toHz.size) { org.rtaguide.dsp.interpolateLogFrequency(xs, ys, toHz[it]) }
        }

        fun octaveEdge(bandsPerOctave: Int): Double = 2.0.pow(1.0 / (2.0 * bandsPerOctave))
    }
}
