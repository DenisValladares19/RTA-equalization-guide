package org.rtaguide.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BandsTest {
    @Test
    fun thirdOctaveHas31IsoBands() {
        val bands = BandSet(3)
        assertEquals(31, bands.size)
        assertEquals(20.0, bands.bands.first().nominalHz)
        assertEquals(20000.0, bands.bands.last().nominalHz)
        assertEquals(listOf("20", "25", "31.5", "40", "50", "63"), bands.bands.take(6).map { it.label })
        assertEquals("1k", bands.bands.first { it.nominalHz == 1000.0 }.label)
        assertEquals("12.5k", bands.bands.first { it.nominalHz == 12500.0 }.label)
    }

    @Test
    fun bandsAreContiguous() {
        for (b in listOf(1, 3, 6, 12)) {
            val bands = BandSet(b).bands
            for (i in 1 until bands.size) assertEquals(bands[i - 1].highHz, bands[i].lowHz, 1e-6 * bands[i].lowHz)
        }
    }

    @Test
    fun octaveBands() {
        val bands = BandSet(1)
        assertEquals(listOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0), bands.bands.map { it.nominalHz })
    }

    @Test
    fun sixthOctaveHasTwiceAsManyBands() {
        assertTrue(BandSet(6).size in 60..62)
    }

    @Test
    fun bandPowersPreserveTotal() {
        val spectrum = Spectrum(
            doubleArrayOf(150.0, 450.0), doubleArrayOf(100.0, 300.0), doubleArrayOf(300.0, 600.0), doubleArrayOf(1.0, 2.0),
        )
        val bands = BandSet(1, 20.0, 20000.0)
        assertEquals(3.0, bands.bandPowers(spectrum).sum(), 1e-9)
    }
}
