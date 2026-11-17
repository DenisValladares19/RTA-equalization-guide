package org.rtaguide.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import org.rtaguide.app.ui.ChartModel
import org.rtaguide.app.ui.ChartRenderer
import org.rtaguide.app.ui.formatGain
import org.rtaguide.dsp.Band
import org.rtaguide.dsp.BandSet
import org.rtaguide.dsp.formatFrequency
import org.rtaguide.targets.BandStatus
import org.rtaguide.targets.TargetCurve
import org.rtaguide.targets.session.CsvExport
import org.rtaguide.targets.session.Session
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

/** Exports saved sessions as CSV, PNG and PDF and opens the system share sheet. */
object Exporter {
    fun shareCsv(context: Context, session: Session) = runSafely(context) {
        val file = exportFile(context, session, "csv")
        file.writeText(CsvExport.session(session))
        share(context, file, "text/csv")
    }

    fun sharePng(context: Context, session: Session, curves: List<TargetCurve>) = runSafely(context) {
        val bitmap = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        ChartRenderer(2.5f).draw(canvas, 1600f, 900f, chartModel(session, curves))
        val file = exportFile(context, session, "png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        share(context, file, "image/png")
    }

    fun sharePdf(context: Context, session: Session, curves: List<TargetCurve>) = runSafely(context) {
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        drawReport(page.canvas, session, curves)
        pdf.finishPage(page)
        val file = exportFile(context, session, "pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        share(context, file, "application/pdf")
    }

    private fun drawReport(c: Canvas, s: Session, curves: List<TargetCurve>) {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.DKGRAY }
        val bold = Paint(text).apply { isFakeBoldText = true; color = Color.BLACK }
        var y = 48f
        c.drawText(s.name, 40f, y, title)
        y += 16f
        val date = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(s.createdAtMs))
        c.drawText("$date · Curva: ${s.curveName ?: "—"} · ${s.unit} · ${s.positions} posición(es) · 1/${s.bandsPerOctave} oct", 40f, y, text)
        if (s.notes.isNotBlank()) {
            y += 13f
            c.drawText(s.notes.replace('\n', ' ').take(120), 40f, y, text)
        }
        y += 12f
        c.save()
        c.translate(40f, y)
        ChartRenderer(0.9f, light = true).draw(c, 515f, 260f, chartModel(s, curves))
        c.restore()
        y += 280f

        c.drawText("EQ gráfico sugerido", 40f, y, bold)
        c.drawText("EQ paramétrico sugerido", 330f, y, bold)
        y += 14f
        val startY = y
        val half = (s.graphicEq.size + 1) / 2
        s.graphicEq.forEachIndexed { i, b ->
            val col = if (i < half) 40f else 180f
            val row = if (i < half) i else i - half
            c.drawText("${b.label} Hz", col, startY + row * 12f, text)
            c.drawText(formatGain(b.gainDb), col + 60f, startY + row * 12f, if (b.gainDb != 0.0) bold else text)
        }
        s.parametric.forEachIndexed { i, f ->
            c.drawText(
                "${i + 1}.  ${formatFrequency(f.frequencyHz)} Hz   ${formatGain(f.gainDb)}   Q %.1f".format(f.q),
                330f, startY + i * 12f, text,
            )
        }
        val notesY = startY + maxOf(half, s.parametric.size) * 12f + 16f
        val flagged = s.bands.filter { it.note.isNotBlank() && !it.note.startsWith("Fuera") && !it.note.startsWith("Diferencia") }
        if (flagged.isNotEmpty()) {
            c.drawText("Avisos", 40f, notesY, bold)
            flagged.take(10).forEachIndexed { i, b -> c.drawText("${b.label} Hz: ${b.note}", 40f, notesY + 13f + i * 12f, text) }
        }
        c.drawText("Generado con RTA Guía EQ. Aplica los cambios, vuelve a medir y verifica escuchando.", 40f, 815f, text)
    }

    private fun chartModel(s: Session, curves: List<TargetCurve>): ChartModel {
        val bands = bandsOf(s)
        val measured = s.measuredDb
        val curve = curves.firstOrNull { it.id == s.curveId }
        val targets = s.bands.map { it.targetDb }
        val offset = if (curve != null) {
            val pairs = s.bands.indices.filter { targets[it] != null }
            if (pairs.isEmpty()) 0.0 else pairs.map { targets[it]!! - curve.relativeGainAt(s.bands[it].centerHz) }.average()
        } else 0.0
        val tolerance = s.toleranceDb ?: curve?.toleranceDb ?: 3.0
        val statuses = s.bands.map { b ->
            val t = b.targetDb ?: return@map BandStatus.IGNORED
            val d = abs(b.measuredDb - t)
            when {
                b.note.startsWith("Fuera") -> BandStatus.IGNORED
                d <= tolerance -> BandStatus.OK
                d <= 2 * tolerance -> BandStatus.WARNING
                else -> BandStatus.OUT
            }
        }
        val max = measured.maxOrNull() ?: 0.0
        val top = ceil((max + 6) / 10) * 10
        return ChartModel(
            bands = bands, measuredDb = measured, statuses = statuses, curve = curve, curveOffsetDb = offset,
            toleranceDb = tolerance, yTopDb = top, yRangeDb = 60.0, unit = s.unit,
        )
    }

    private fun bandsOf(s: Session): List<Band> {
        val edge = 2.0.pow(1.0 / (2.0 * s.bandsPerOctave))
        return s.bands.mapIndexed { i, b -> Band(i, b.centerHz, BandSet.nominalFor(b.centerHz), b.centerHz / edge, b.centerHz * edge) }
    }

    private fun exportFile(context: Context, s: Session, ext: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = s.name.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ _-]"), "_").trim().ifBlank { "medicion" }
        return File(dir, "$safe.$ext")
    }

    private fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir ${file.name}"))
    }

    private fun runSafely(context: Context, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
