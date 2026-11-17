package org.rtaguide.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import org.rtaguide.dsp.PinkNoiseGenerator
import kotlin.math.max

/** Plays pink noise through the phone output (speaker, headphone jack or USB interface). */
class PinkNoisePlayer {
    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    var levelDbFs: Double = -12.0

    val isPlaying: Boolean get() = running

    fun start(sampleRate: Int = 48000) {
        if (running) return
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBuffer, sampleRate / 5 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        running = true
        thread = Thread({
            val generator = PinkNoiseGenerator(levelDbFs)
            val buffer = ShortArray(sampleRate / 50)
            try {
                track.play()
                while (running) {
                    if (generator.levelDbFs != levelDbFs) generator.levelDbFs = levelDbFs
                    generator.fill(buffer)
                    track.write(buffer, 0, buffer.size)
                }
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }, "pink-noise").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }
}
