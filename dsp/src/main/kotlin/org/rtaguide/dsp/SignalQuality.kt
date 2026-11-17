package org.rtaguide.dsp

/** Warnings about the reliability of a measurement. */
enum class QualityWarning(val message: String) {
    CLIPPING("La señal está saturando: baja el nivel o aleja el micrófono."),
    LEVEL_TOO_LOW("Nivel muy bajo: sube el ruido rosa para medir por encima del ruido de fondo."),
    LOW_SNR("Algunas bandas están a menos de 10 dB del ruido de fondo: la lectura no es fiable ahí."),
    WARMING_UP("Llenando el búfer de graves: espera un par de segundos."),
    UNPROCESSED_UNAVAILABLE("Este teléfono no ofrece audio sin procesar: la medición puede estar alterada."),
}

object SignalQuality {
    /**
     * @param peak absolute sample peak of the last block (full scale = 1.0)
     * @param overallDbFs overall level of the measurement
     * @param bandLevelsDb current band levels
     * @param noiseFloorDb previously captured background-noise band levels, if any
     * @return the warnings plus the indices of bands with a signal-to-noise ratio below [minSnrDb]
     */
    fun assess(
        peak: Double,
        overallDbFs: Double,
        bandLevelsDb: DoubleArray,
        noiseFloorDb: DoubleArray?,
        warmup: Double = 1.0,
        minSnrDb: Double = 10.0,
    ): Pair<List<QualityWarning>, Set<Int>> {
        val warnings = ArrayList<QualityWarning>()
        if (peak >= 0.99) warnings.add(QualityWarning.CLIPPING)
        if (overallDbFs < -70.0) warnings.add(QualityWarning.LEVEL_TOO_LOW)
        if (warmup < 1.0) warnings.add(QualityWarning.WARMING_UP)
        val lowSnr = HashSet<Int>()
        if (noiseFloorDb != null && noiseFloorDb.size == bandLevelsDb.size) {
            for (i in bandLevelsDb.indices) {
                if (bandLevelsDb[i] - noiseFloorDb[i] < minSnrDb) lowSnr.add(i)
            }
            if (lowSnr.isNotEmpty()) warnings.add(QualityWarning.LOW_SNR)
        }
        return warnings to lowSnr
    }
}
