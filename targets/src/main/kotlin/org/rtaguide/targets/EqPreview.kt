package org.rtaguide.targets

/** Predicts what the spectrum would look like after applying an EQ. */
object EqPreview {
    fun withCorrections(measuredDb: DoubleArray, suggestion: EqSuggestion): DoubleArray =
        DoubleArray(measuredDb.size) { measuredDb[it] + suggestion.items[it].correctionDb }

    fun withGraphic(measuredDb: DoubleArray, centersHz: DoubleArray, eq: List<GraphicEqBand>): DoubleArray =
        DoubleArray(measuredDb.size) { measuredDb[it] + GraphicEqMapper.responseDb(eq, centersHz[it]) }

    fun withParametric(measuredDb: DoubleArray, centersHz: DoubleArray, filters: List<PeakingFilter>): DoubleArray =
        DoubleArray(measuredDb.size) { measuredDb[it] + ParametricFitter.responseDb(filters, centersHz[it]) }
}
