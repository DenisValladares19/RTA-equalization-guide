package org.rtaguide.targets.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.rtaguide.targets.GraphicEqBand
import org.rtaguide.targets.PeakingFilter
import java.util.Locale

/** One band row of a saved measurement. */
@Serializable
data class SessionBand(
    @SerialName("hz") val centerHz: Double,
    @SerialName("etiqueta") val label: String,
    @SerialName("medido_db") val measuredDb: Double,
    @SerialName("objetivo_db") val targetDb: Double? = null,
    @SerialName("correccion_db") val correctionDb: Double? = null,
    @SerialName("nota") val note: String = "",
)

/** A saved measurement with its target, suggestions and notes (before/after comparisons, reports). */
@Serializable
data class Session(
    val id: String,
    @SerialName("nombre") val name: String,
    @SerialName("notas") val notes: String = "",
    @SerialName("creada_ms") val createdAtMs: Long,
    @SerialName("frecuencia_muestreo") val sampleRate: Int,
    @SerialName("bandas_por_octava") val bandsPerOctave: Int,
    @SerialName("posiciones") val positions: Int = 1,
    @SerialName("unidad") val unit: String = "dBFS",
    @SerialName("curva_id") val curveId: String? = null,
    @SerialName("curva_nombre") val curveName: String? = null,
    @SerialName("tolerancia_db") val toleranceDb: Double? = null,
    @SerialName("bandas") val bands: List<SessionBand>,
    @SerialName("eq_grafico") val graphicEq: List<GraphicEqBand> = emptyList(),
    @SerialName("eq_parametrico") val parametric: List<PeakingFilter> = emptyList(),
) {
    val centersHz: DoubleArray get() = DoubleArray(bands.size) { bands[it].centerHz }
    val measuredDb: DoubleArray get() = DoubleArray(bands.size) { bands[it].measuredDb }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(text: String): Session = json.decodeFromString(serializer(), text)
        fun listToJson(list: List<Session>): String = json.encodeToString(ListSerializer(serializer()), list)
    }
}

/** CSV export (comma separated, dot as decimal mark) for spreadsheets and other tools. */
object CsvExport {
    private fun n(v: Double?): String = v?.let { String.format(Locale.ROOT, "%.2f", it) } ?: ""
    private fun q(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    fun session(s: Session): String = buildString {
        appendLine("# ${s.name}")
        if (s.notes.isNotBlank()) appendLine("# ${s.notes.replace("\n", " ")}")
        appendLine("# curva: ${s.curveName ?: "-"}; unidad: ${s.unit}; posiciones: ${s.positions}")
        appendLine("hz,etiqueta,medido_db,objetivo_db,desviacion_db,correccion_db,nota")
        for (b in s.bands) {
            val dev = b.targetDb?.let { b.measuredDb - it }
            appendLine(listOf(n(b.centerHz), q(b.label), n(b.measuredDb), n(b.targetDb), n(dev), n(b.correctionDb), q(b.note)).joinToString(","))
        }
        if (s.graphicEq.isNotEmpty()) {
            appendLine()
            appendLine("eq_grafico_hz,etiqueta,ganancia_db")
            for (g in s.graphicEq) appendLine("${n(g.nominalHz)},${q(g.label)},${n(g.gainDb)}")
        }
        if (s.parametric.isNotEmpty()) {
            appendLine()
            appendLine("filtro,frecuencia_hz,ganancia_db,q")
            s.parametric.forEachIndexed { i, f -> appendLine("${i + 1},${n(f.frequencyHz)},${n(f.gainDb)},${n(f.q)}") }
        }
    }
}
