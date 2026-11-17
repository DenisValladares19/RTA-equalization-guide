package org.rtaguide.dsp

/** Warnings about the reliability of a measurement. */
enum class QualityWarning(val message: String) {
    CLIPPING("La señal está saturando: baja el nivel o aleja el micrófono."),
    LEVEL_TOO_LOW("Nivel muy bajo: sube el ruido rosa para medir por encima del ruido de fondo."),
    LOW_SNR("Algunas bandas están a menos de 10 dB del ruido de fondo: la lectura no es fiable ahí."),
    WARMING_UP("Llenando el búfer de graves: espera un par de segundos."),
    UNPROCESSED_UNAVAILABLE("Este teléfono no ofrece audio sin procesar: la medición puede estar alterada."),
    NO_TEST_SIGNAL("Sin señal de prueba: reproduce ruido rosa por el sistema para compararlo con la guía."),
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

    /**
     * Whether the measurement looks like a broadband test signal (pink noise through the system)
     * rather than ambient noise. Ambient noise (HVAC, traffic, handling, voices) is concentrated
     * in the low end, so its 2–8 kHz bands sit far below the 500 Hz–2 kHz bands; pink noise through
     * any reasonable system keeps them within [maxTiltDb]. With a captured [noiseFloorDb], the
     * mid and high bands must also be at least [minSnrDb] above it.
     */
    fun hasTestSignal(
        centersHz: DoubleArray,
        levelsDb: DoubleArray,
        noiseFloorDb: DoubleArray? = null,
        maxTiltDb: Double = 12.0,
        minSnrDb: Double = 10.0,
    ): Boolean {
        fun mean(values: DoubleArray, lo: Double, hi: Double): Double? {
            val idx = centersHz.indices.filter { centersHz[it] in lo..hi }
            return if (idx.isEmpty()) null else idx.map { values[it] }.average()
        }
        val mids = mean(levelsDb, 500.0, 2000.0) ?: return false
        val highs = mean(levelsDb, 2000.0, 8000.0) ?: return false
        if (highs < mids - maxTiltDb) return false
        if (noiseFloorDb != null && noiseFloorDb.size == levelsDb.size) {
            val floorMids = mean(noiseFloorDb, 500.0, 2000.0)!!
            val floorHighs = mean(noiseFloorDb, 2000.0, 8000.0)!!
            if (mids - floorMids < minSnrDb || highs - floorHighs < minSnrDb) return false
        }
        return true
    }
}
