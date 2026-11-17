package org.rtaguide.targets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.rtaguide.dsp.BandSet
import org.rtaguide.dsp.formatFrequency
import org.rtaguide.dsp.interpolateLogFrequency
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Common graphic-equalizer layouts. */
enum class GraphicEqLayout(val label: String, val nominalHz: List<Double>) {
    THIRTY_ONE("31 bandas (1/3 de octava)", BandSet.ISO_NOMINAL.filter { it in 20.0..20000.0 }),
    FIFTEEN(
        "15 bandas (2/3 de octava)",
        listOf(25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0, 1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0),
    ),
    TEN("10 bandas (octava)", listOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0));

    companion object {
        /** Exact base-10 frequency behind an ISO nominal value (31.5 → 31.62 Hz). */
        fun exactHz(nominal: Double): Double = 10.0.pow((10 * log10(nominal)).roundToInt() / 10.0)
    }
}

@Serializable
data class GraphicEqBand(
    @SerialName("etiqueta") val label: String,
    @SerialName("hz") val nominalHz: Double,
    @SerialName("ganancia_db") val gainDb: Double,
)

object GraphicEqMapper {
    /** Maps a band-by-band suggestion onto the sliders of a graphic EQ. */
    fun map(suggestion: EqSuggestion, layout: GraphicEqLayout = GraphicEqLayout.THIRTY_ONE): List<GraphicEqBand> {
        val rules = suggestion.rules
        val xs = suggestion.centersHz.toList()
        val ys = suggestion.corrections.toList()
        return layout.nominalHz.map { nominal ->
            val f = GraphicEqLayout.exactHz(nominal)
            val gain = if (xs.isEmpty() || f < rules.lowLimitHz || f > rules.highLimitHz) 0.0
            else interpolateLogFrequency(xs, ys, f).coerceIn(-rules.maxCutDb, rules.maxBoostDb)
            GraphicEqBand(formatFrequency(nominal), nominal, EqAdvisor.roundTo(gain, rules.roundingStepDb))
        }
    }

    /** Smooth response of a graphic EQ (log-frequency interpolation of the sliders). */
    fun responseDb(eq: List<GraphicEqBand>, hz: Double): Double {
        if (eq.isEmpty()) return 0.0
        return interpolateLogFrequency(eq.map { GraphicEqLayout.exactHz(it.nominalHz) }, eq.map { it.gainDb }, hz)
    }
}
