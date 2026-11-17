package org.rtaguide.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import org.rtaguide.dsp.MicCalibration
import org.rtaguide.targets.TargetCurve
import org.rtaguide.targets.session.Session
import java.io.File

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
        ?: AppSettings()

    fun save(settings: AppSettings) {
        prefs.edit().putString(KEY, json.encodeToString(AppSettings.serializer(), settings)).apply()
    }

    private companion object {
        const val KEY = "json"
    }
}

/** User curves, one JSON file each (same format as the curve JSON files in the guide). */
class CurveStore(filesDir: File) {
    private val dir = File(filesDir, "curves").apply { mkdirs() }

    fun loadAll(): List<TargetCurve> = dir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { runCatching { TargetCurve.fromJson(it.readText()) }.getOrNull() }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

    fun save(curve: TargetCurve) = File(dir, "${safe(curve.id)}.json").writeText(curve.copy(builtIn = false).toJson())

    fun delete(id: String) {
        File(dir, "${safe(id)}.json").delete()
    }
}

class SessionStore(filesDir: File) {
    private val dir = File(filesDir, "sessions").apply { mkdirs() }

    fun loadAll(): List<Session> = dir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { runCatching { Session.fromJson(it.readText()) }.getOrNull() }
        ?.sortedByDescending { it.createdAtMs }
        ?: emptyList()

    fun save(session: Session) = File(dir, "${safe(session.id)}.json").writeText(session.toJson())

    fun delete(id: String) {
        File(dir, "${safe(id)}.json").delete()
    }
}

class CalibrationStore(private val context: Context) {
    private val file = File(context.filesDir, "mic_calibration.txt")
    private val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)

    fun load(): MicCalibration? {
        if (!file.exists()) return null
        val name = prefs.getString("name", null) ?: "Calibración"
        return runCatching { MicCalibration.parse(file.readText(), name) }.getOrNull()
    }

    fun save(text: String, name: String): MicCalibration {
        val cal = MicCalibration.parse(text, name)
        file.writeText(text)
        prefs.edit().putString("name", name).apply()
        return cal
    }

    fun clear() {
        file.delete()
        prefs.edit().clear().apply()
    }
}

private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
