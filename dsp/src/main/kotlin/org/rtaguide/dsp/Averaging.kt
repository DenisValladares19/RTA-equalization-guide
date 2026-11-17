package org.rtaguide.dsp

import kotlin.math.exp
import kotlin.math.max

/** Time averaging applied to band powers (always in the linear power domain). */
enum class AveragingMode(val label: String, val timeConstantSeconds: Double?) {
    NONE("Instantáneo", 0.0),
    FAST("Rápido (125 ms)", 0.125),
    SLOW("Lento (1 s)", 1.0),
    INFINITE("Infinito (acumulado)", null),
}

class PowerAverager(var mode: AveragingMode = AveragingMode.SLOW) {
    private var state: DoubleArray? = null

    /** Number of frames accumulated since the last reset. */
    var frames: Long = 0
        private set

    fun reset() {
        state = null
        frames = 0
    }

    /** Feeds a new frame of band powers, [dtSeconds] after the previous one; returns the average. */
    fun update(powers: DoubleArray, dtSeconds: Double): DoubleArray {
        val current = state
        if (current == null || current.size != powers.size) {
            state = powers.copyOf()
            frames = 1
            return powers.copyOf()
        }
        frames++
        val tau = mode.timeConstantSeconds
        val alpha = when {
            tau == null -> 1.0 / frames
            tau <= 0.0 -> 1.0
            else -> 1.0 - exp(-dtSeconds / tau)
        }
        for (i in powers.indices) current[i] += alpha * (powers[i] - current[i])
        return current.copyOf()
    }
}

/** Keeps the maximum level seen per band ("max hold"). */
class MaxHold {
    var levels: DoubleArray? = null
        private set

    fun update(levelsDb: DoubleArray): DoubleArray {
        val current = levels
        if (current == null || current.size != levelsDb.size) {
            levels = levelsDb.copyOf()
        } else {
            for (i in levelsDb.indices) current[i] = max(current[i], levelsDb[i])
        }
        return levels!!.copyOf()
    }

    fun reset() {
        levels = null
    }
}
