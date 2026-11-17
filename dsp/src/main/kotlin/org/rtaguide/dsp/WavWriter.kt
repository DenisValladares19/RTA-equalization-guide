package org.rtaguide.dsp

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Writes 16-bit PCM mono WAV files. */
object WavWriter {
    fun write(out: OutputStream, sampleRate: Int, samples: ShortArray) {
        val dataBytes = samples.size * 2
        val header = ByteArray(44)
        fun putInt(pos: Int, v: Int) {
            header[pos] = (v and 0xFF).toByte()
            header[pos + 1] = (v shr 8 and 0xFF).toByte()
            header[pos + 2] = (v shr 16 and 0xFF).toByte()
            header[pos + 3] = (v shr 24 and 0xFF).toByte()
        }
        fun putShort(pos: Int, v: Int) {
            header[pos] = (v and 0xFF).toByte()
            header[pos + 1] = (v shr 8 and 0xFF).toByte()
        }
        "RIFF".toByteArray().copyInto(header, 0)
        putInt(4, 36 + dataBytes)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        putInt(16, 16)
        putShort(20, 1) // PCM
        putShort(22, 1) // mono
        putInt(24, sampleRate)
        putInt(28, sampleRate * 2)
        putShort(32, 2)
        putShort(34, 16)
        "data".toByteArray().copyInto(header, 36)
        putInt(40, dataBytes)
        out.write(header)
        val buffer = ByteArray(8192)
        var pos = 0
        for (s in samples) {
            buffer[pos++] = (s.toInt() and 0xFF).toByte()
            buffer[pos++] = (s.toInt() shr 8 and 0xFF).toByte()
            if (pos == buffer.size) {
                out.write(buffer)
                pos = 0
            }
        }
        if (pos > 0) out.write(buffer, 0, pos)
    }

    /** Generates a pink noise WAV (for playing through the mixing desk) as bytes. */
    fun pinkNoise(sampleRate: Int = 48000, seconds: Int = 60, levelDbFs: Double = -12.0): ByteArray {
        val samples = ShortArray(sampleRate * seconds)
        PinkNoiseGenerator(levelDbFs).fill(samples)
        // Short fade in/out to avoid clicks.
        val fade = sampleRate / 20
        for (i in 0 until fade) {
            val g = i.toDouble() / fade
            samples[i] = (samples[i] * g).toInt().toShort()
            val j = samples.size - 1 - i
            samples[j] = (samples[j] * g).toInt().toShort()
        }
        return ByteArrayOutputStream(samples.size * 2 + 44).also { write(it, sampleRate, samples) }.toByteArray()
    }
}
