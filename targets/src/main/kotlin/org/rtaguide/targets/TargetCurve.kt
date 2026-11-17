package org.rtaguide.targets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.rtaguide.dsp.interpolateLogFrequency

/**
 * Target ("ideal") response curve: the silhouette drawn behind the spectrum.
 *
 * Points are `[Hz, dB]` pairs relative to [referenceHz] (normally 1 kHz = 0 dB) and are
 * interpolated linearly over log-frequency; beyond the first/last point the value is held.
 * Curves are meant to be compared against pink noise measured in fractional-octave bands.
 */
@Serializable
data class TargetCurve(
    val id: String,
    @SerialName("nombre") val name: String,
    @SerialName("descripcion") val description: String = "",
    @SerialName("referencia_hz") val referenceHz: Double = 1000.0,
    @SerialName("tolerancia_db") val toleranceDb: Double = 3.0,
    @SerialName("puntos") val points: List<List<Double>>,
    @SerialName("integrada") val builtIn: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(points.isNotEmpty()) { "a curve needs at least one point" }
        require(points.all { it.size == 2 && it[0] > 0 }) { "points must be [Hz > 0, dB] pairs" }
        require(toleranceDb > 0) { "tolerance must be positive" }
    }

    private val sorted: List<List<Double>> by lazy { points.sortedBy { it[0] } }
    private val xs: List<Double> by lazy { sorted.map { it[0] } }
    private val ys: List<Double> by lazy { sorted.map { it[1] } }

    val frequenciesHz: List<Double> get() = xs
    val gainsDb: List<Double> get() = ys

    /** Curve value at [hz], relative to the curve's own 0 dB. */
    fun gainAt(hz: Double): Double = interpolateLogFrequency(xs, ys, hz)

    /** Curve value at [hz] relative to its value at [referenceHz]. */
    fun relativeGainAt(hz: Double): Double = gainAt(hz) - gainAt(referenceHz)

    fun gainsAt(centersHz: DoubleArray): DoubleArray = DoubleArray(centersHz.size) { relativeGainAt(centersHz[it]) }

    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String): TargetCurve = json.decodeFromString(serializer(), text)
        fun listFromJson(text: String): List<TargetCurve> =
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(serializer()), text)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)
}

private fun p(hz: Double, db: Double) = listOf(hz, db)

/** Curves shipped with the app. Values are documented in docs/guide/curvas.md. */
object BuiltInCurves {
    val FLAT = TargetCurve(
        id = "plana",
        name = "Plana (referencia)",
        description = "0 dB en todo el rango. Para verificar el sistema y el micrófono, o como punto de partida.",
        toleranceDb = 3.0,
        points = listOf(p(20.0, 0.0), p(20000.0, 0.0)),
        builtIn = true,
    )

    val STUDIO = TargetCurve(
        id = "estudio",
        name = "Monitores de estudio",
        description = "Mezcla en campo cercano: plana hasta 1 kHz y caída suave de ≈1 dB/octava en agudos.",
        toleranceDb = 2.0,
        points = listOf(
            p(20.0, 0.0), p(1000.0, 0.0), p(2000.0, -1.0), p(4000.0, -2.0),
            p(8000.0, -3.0), p(16000.0, -4.0), p(20000.0, -4.3),
        ),
        builtIn = true,
    )

    val HARMAN_ROOM = TargetCurve(
        id = "sala-harman",
        name = "Sala doméstica (tipo Harman)",
        description = "Hi-fi y cine en casa: realce de graves de ≈6 dB por debajo de 100 Hz y pendiente suave hacia los agudos.",
        toleranceDb = 3.0,
        points = listOf(
            p(20.0, 6.5), p(40.0, 6.0), p(63.0, 5.0), p(100.0, 3.5), p(160.0, 1.5), p(250.0, 0.8),
            p(500.0, 0.4), p(1000.0, 0.0), p(2000.0, -1.0), p(4000.0, -2.0), p(8000.0, -3.0),
            p(16000.0, -4.0), p(20000.0, -4.5),
        ),
        builtIn = true,
    )

    val X_CURVE = TargetCurve(
        id = "curva-x",
        name = "Curva X de cine (SMPTE ST 202)",
        description = "Salas de cine grandes: plana hasta 2 kHz y después −3 dB/octava.",
        toleranceDb = 3.0,
        points = listOf(
            p(20.0, 0.0), p(2000.0, 0.0), p(4000.0, -3.0), p(8000.0, -6.0),
            p(16000.0, -9.0), p(20000.0, -10.0),
        ),
        builtIn = true,
    )

    val LIVE_PA = TargetCurve(
        id = "pa-en-vivo",
        name = "PA en vivo / concierto",
        description = "Rock, pop y música en directo: subgraves marcados, medios planos y agudos con caída.",
        toleranceDb = 3.0,
        points = listOf(
            p(20.0, 6.0), p(40.0, 9.0), p(80.0, 8.0), p(150.0, 3.0), p(300.0, 0.0),
            p(1000.0, 0.0), p(4000.0, -1.0), p(10000.0, -4.0), p(16000.0, -6.0), p(20000.0, -9.0),
        ),
        builtIn = true,
    )

    val CLUB = TargetCurve(
        id = "club",
        name = "Club / electrónica",
        description = "Discotecas y EDM: subgraves muy marcados (40–60 Hz), medios planos y agudos suaves.",
        toleranceDb = 3.0,
        points = listOf(
            p(20.0, 8.0), p(31.5, 11.0), p(40.0, 12.0), p(63.0, 11.0), p(100.0, 6.0), p(160.0, 2.0),
            p(250.0, 0.5), p(500.0, 0.0), p(1000.0, 0.0), p(4000.0, -1.0), p(8000.0, -3.0),
            p(12500.0, -5.0), p(16000.0, -6.0), p(20000.0, -8.0),
        ),
        builtIn = true,
    )

    val SPEECH = TargetCurve(
        id = "voz",
        name = "Voz / iglesia / conferencias",
        description = "Inteligibilidad de la palabra: graves recortados, presencia en 2–4 kHz y caída por encima de 8 kHz.",
        toleranceDb = 3.0,
        points = listOf(
            p(20.0, -12.0), p(50.0, -8.0), p(80.0, -4.0), p(125.0, -1.5), p(200.0, 0.0), p(500.0, 0.0),
            p(1000.0, 0.0), p(2000.0, 1.5), p(3150.0, 2.5), p(4000.0, 2.0), p(6300.0, 0.0),
            p(8000.0, -2.0), p(12500.0, -6.0), p(16000.0, -9.0), p(20000.0, -12.0),
        ),
        builtIn = true,
    )

    /**
     * Popular live-sound guide (as seen on graphic-EQ/RTA tutorials): strong low end that falls
     * ≈3.5 dB/octave from 31.5 Hz to 500 Hz, then flat up to 16 kHz, with a narrow ±2 dB band.
     */
    val BASS_GUIDE = TargetCurve(
        id = "graves-pronunciados",
        name = "Graves pronunciados (guía de directo)",
        description = "Graves +16 dB en 31,5 Hz que bajan ≈3,5 dB/octava hasta 500 Hz; plana de 500 Hz a 16 kHz. Banda estrecha de ±2 dB.",
        toleranceDb = 2.0,
        points = listOf(
            p(20.0, 18.5), p(31.5, 16.0), p(63.0, 12.5), p(125.0, 6.5), p(250.0, 2.5),
            p(500.0, 0.0), p(1000.0, 0.0), p(16000.0, 0.0), p(20000.0, 0.0),
        ),
        builtIn = true,
    )

    val ALL: List<TargetCurve> = listOf(FLAT, STUDIO, HARMAN_ROOM, X_CURVE, LIVE_PA, CLUB, SPEECH, BASS_GUIDE)

    fun byId(id: String): TargetCurve? = ALL.firstOrNull { it.id == id }
}
