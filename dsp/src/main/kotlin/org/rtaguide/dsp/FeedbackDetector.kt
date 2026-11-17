package org.rtaguide.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** A narrow, persistent peak that looks like acoustic feedback (a "ringing" frequency). */
data class FeedbackCandidate(
    val frequencyHz: Double,
    val prominenceDb: Double,
    val frames: Int,
) {
    /** Suggested notch: narrow cut at the ringing frequency. */
    val suggestedCutDb: Double get() = -(prominenceDb / 2).coerceIn(3.0, 12.0)
    val suggestedQ: Double get() = 20.0
}

/**
 * Detects feedback-like peaks in the fine spectrum: a local maximum that stands out at least
 * [minProminenceDb] above the average level of the surrounding third-octave and that persists
 * (same frequency within ±2 %) for at least [minFrames] consecutive frames.
 */
class FeedbackDetector(
    val minProminenceDb: Double = 15.0,
    val minFrames: Int = 8,
    val minHz: Double = 80.0,
    val maxHz: Double = 16000.0,
) {
    private data class Track(var hz: Double, var prominence: Double, var frames: Int, var seen: Boolean)

    private val tracks = ArrayList<Track>()

    fun reset() = tracks.clear()

    fun update(spectrum: Spectrum): List<FeedbackCandidate> {
        val n = spectrum.size
        if (n < 8) return emptyList()
        val levels = DoubleArray(n) { spectrum.levelAsFractionalOctaveDb(it, 3) }
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + levels[i]
        val halfWindow = 2.0.pow(1.0 / 6.0)

        tracks.forEach { it.seen = false }
        var lo = 0
        var hi = 0
        for (i in 2 until n - 2) {
            val f = spectrum.centerHz[i]
            if (f < minHz || f > maxHz) continue
            val l = levels[i]
            if (l < levels[i - 1] || l < levels[i + 1] || l < levels[i - 2] || l < levels[i + 2]) continue
            while (lo < n && spectrum.centerHz[lo] < f / halfWindow) lo++
            while (hi < n && spectrum.centerHz[hi] <= f * halfWindow) hi++
            // Mean of the neighborhood, excluding the peak itself (±2 bins).
            val excludedFrom = maxOf(lo, i - 2)
            val excludedTo = minOf(hi, i + 3)
            val count = (hi - lo) - (excludedTo - excludedFrom)
            if (count < 4) continue
            val sum = (prefix[hi] - prefix[lo]) - (prefix[excludedTo] - prefix[excludedFrom])
            val prominence = l - sum / count
            if (prominence < minProminenceDb) continue
            val track = tracks.firstOrNull { abs(ln(it.hz / f)) < 0.02 }
            if (track != null) {
                if (!track.seen) {
                    track.frames++
                    track.seen = true
                }
                track.hz = f
                track.prominence = maxOf(track.prominence, prominence)
            } else {
                tracks.add(Track(f, prominence, 1, true))
            }
        }
        tracks.removeAll { !it.seen }
        return tracks.filter { it.frames >= minFrames }
            .sortedByDescending { it.prominence }
            .map { FeedbackCandidate(it.hz, it.prominence, it.frames) }
    }
}
