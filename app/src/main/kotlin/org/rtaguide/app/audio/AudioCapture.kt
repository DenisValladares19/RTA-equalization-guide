package org.rtaguide.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import org.rtaguide.app.data.InputSource
import kotlin.math.abs
import kotlin.math.max

/** Microphone capture on a dedicated thread (mono, 16-bit PCM, converted to float). */
class AudioCapture(context: Context) {
    interface Listener {
        fun onAudio(block: FloatArray, length: Int, peak: Float)
        fun onCaptureError(message: String)
    }

    data class Started(val sampleRate: Int, val sourceLabel: String, val unprocessedSupported: Boolean)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    val unprocessedSupported: Boolean
        get() = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

    fun inputDevices(): List<Pair<Int, String>> =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.id to describe(it) }

    /** Opens the microphone. Throws [IllegalStateException] with a user-facing message on failure. */
    @SuppressLint("MissingPermission")
    fun start(source: InputSource, sampleRate: Int, deviceId: Int, listener: Listener): Started {
        stop()
        val unprocessed = unprocessedSupported
        val androidSource = when (source) {
            InputSource.AUTO -> if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
            else -> source.androidSource
        }
        val sourceLabel = when {
            source == InputSource.AUTO && unprocessed -> "Sin procesar"
            source == InputSource.AUTO -> "Reconocimiento de voz"
            else -> source.label
        }
        var record: AudioRecord? = null
        var rate = sampleRate
        for (candidate in listOf(sampleRate, 48000, 44100).distinct()) {
            val minBuffer = AudioRecord.getMinBufferSize(candidate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) continue
            val r = try {
                AudioRecord(androidSource, candidate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuffer, candidate / 5 * 2))
            } catch (e: Exception) {
                null
            }
            if (r != null && r.state == AudioRecord.STATE_INITIALIZED) {
                record = r
                rate = candidate
                break
            }
            r?.release()
        }
        val rec = record ?: throw IllegalStateException("No se pudo abrir el micrófono con la fuente \"$sourceLabel\".")
        if (deviceId != 0) {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == deviceId }
                ?.let { rec.setPreferredDevice(it) }
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release()
            throw IllegalStateException("No se pudo iniciar la grabación: ${e.message}")
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            throw IllegalStateException("El micrófono está ocupado por otra aplicación.")
        }
        running = true
        thread = Thread({ loop(rec, rate, listener) }, "audio-capture").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return Started(rate, sourceLabel, unprocessed)
    }

    private fun loop(record: AudioRecord, sampleRate: Int, listener: Listener) {
        val chunk = sampleRate / 50 // 20 ms
        val shorts = ShortArray(chunk)
        val floats = FloatArray(chunk)
        try {
            while (running) {
                val n = record.read(shorts, 0, chunk)
                if (n > 0) {
                    var peak = 0f
                    for (i in 0 until n) {
                        val v = shorts[i] / 32768f
                        floats[i] = v
                        val a = abs(v)
                        if (a > peak) peak = a
                    }
                    listener.onAudio(floats, n, peak)
                } else if (n < 0) {
                    listener.onCaptureError("Error al leer el micrófono (código $n).")
                    break
                }
            }
        } finally {
            running = false
            runCatching { record.stop() }
            record.release()
        }
    }

    fun stop() {
        running = false
        val t = thread
        if (t != null && t != Thread.currentThread()) t.join(1000)
        thread = null
    }

    private fun describe(d: AudioDeviceInfo): String {
        val type = when (d.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Micrófono interno"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Auriculares con micro"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
            else -> "Entrada ${d.type}"
        }
        val product = d.productName?.toString().orEmpty()
        return if (product.isBlank()) type else "$type – $product"
    }
}
