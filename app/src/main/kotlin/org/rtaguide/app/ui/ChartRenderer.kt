package org.rtaguide.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.rtaguide.dsp.Band
import org.rtaguide.targets.BandStatus
import org.rtaguide.targets.TargetCurve
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Everything needed to draw the RTA chart. Levels are in display units (dBFS or dB SPL). */
data class ChartModel(
    val bands: List<Band>,
    val measuredDb: DoubleArray? = null,
    val statuses: List<BandStatus>? = null,
    val curve: TargetCurve? = null,
    val curveOffsetDb: Double = 0.0,
    val toleranceDb: Double = 3.0,
    val showSilhouette: Boolean = true,
    val fineHz: DoubleArray? = null,
    val fineDb: DoubleArray? = null,
    val maxHoldDb: DoubleArray? = null,
    val referenceDb: DoubleArray? = null,
    val previewDb: DoubleArray? = null,
    val feedbackHz: List<Double> = emptyList(),
    val yTopDb: Double = 0.0,
    val yRangeDb: Double = 70.0,
    val unit: String = "dBFS",
)

/**
 * Draws the chart with android.graphics so the same code renders on screen (Compose
 * nativeCanvas), into PNG bitmaps and into PDF pages.
 */
class ChartRenderer(private val density: Float, private val light: Boolean = false) {
    companion object {
        const val MIN_HZ = 20.0
        const val MAX_HZ = 20000.0
        val COLOR_OK = Color.rgb(0x4C, 0xAF, 0x50)
        val COLOR_WARNING = Color.rgb(0xFF, 0xC1, 0x07)
        val COLOR_OUT = Color.rgb(0xF4, 0x43, 0x36)
        val COLOR_NEUTRAL = Color.rgb(0x60, 0x7D, 0x8B)
        val COLOR_TARGET = Color.rgb(0x4F, 0xC3, 0xF7)
        val COLOR_PREVIEW = Color.rgb(0xE0, 0x40, 0xFB)
        val COLOR_REFERENCE = Color.rgb(0xFF, 0xFF, 0xFF)
    }

    private val fg = if (light) Color.rgb(0x22, 0x22, 0x22) else Color.rgb(0xEE, 0xEE, 0xEE)
    private val background = if (light) Color.WHITE else Color.rgb(0x0F, 0x14, 0x19)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(fg, 0x30); strokeWidth = density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(fg, 0xB0); textSize = 10f * density }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val silhouettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = withAlpha(COLOR_TARGET, 0x40) }
    private val tolerancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = withAlpha(COLOR_TARGET, 0x28) }
    private val targetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = COLOR_TARGET; strokeWidth = 2.5f * density
    }
    private val finePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = withAlpha(fg, 0x90); strokeWidth = 1.2f * density
    }
    private val holdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = COLOR_WARNING; strokeWidth = 1.5f * density
    }
    private val referencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = if (light) Color.DKGRAY else COLOR_REFERENCE; strokeWidth = 1.8f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = COLOR_PREVIEW; strokeWidth = 2f * density
    }
    private val feedbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = COLOR_OUT }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = fg; strokeWidth = 1.5f * density
    }

    private val left get() = 34f * density
    private val right get() = 6f * density
    private val top get() = 6f * density
    private val bottom get() = 16f * density

    fun plotRect(width: Float, height: Float) = RectF(left, top, width - right, height - bottom)

    fun x(hz: Double, r: RectF): Float =
        r.left + (log10(hz / MIN_HZ) / log10(MAX_HZ / MIN_HZ)).toFloat() * r.width()

    fun y(db: Double, r: RectF, m: ChartModel): Float =
        r.top + ((m.yTopDb - db) / m.yRangeDb).toFloat().coerceIn(0f, 1f) * r.height()

    /** Band under the horizontal position [px], or null. */
    fun bandAt(m: ChartModel, px: Float, width: Float): Int? {
        val r = plotRect(width, 100f)
        if (px < r.left || px > r.right) return null
        val hz = MIN_HZ * (MAX_HZ / MIN_HZ).pow(((px - r.left) / r.width()).toDouble())
        return m.bands.indexOfFirst { hz >= it.lowHz && hz < it.highHz }.takeIf { it >= 0 }
    }

    fun draw(canvas: Canvas, width: Float, height: Float, m: ChartModel, selected: Int? = null, drawBackground: Boolean = true) {
        if (drawBackground) canvas.drawColor(background)
        val r = plotRect(width, height)
        drawGrid(canvas, r, m)
        canvas.save()
        canvas.clipRect(r)
        val curve = m.curve
        if (curve != null && m.showSilhouette) drawSilhouette(canvas, r, m, curve)
        drawBars(canvas, r, m)
        m.fineDb?.let { db -> m.fineHz?.let { hz -> drawLine(canvas, r, m, hz, db, finePaint) } }
        if (curve != null && m.showSilhouette) drawTargetLine(canvas, r, m, curve)
        m.maxHoldDb?.let { drawBandLine(canvas, r, m, it, holdPaint) }
        m.referenceDb?.let { drawBandLine(canvas, r, m, it, referencePaint) }
        m.previewDb?.let { drawBandLine(canvas, r, m, it, previewPaint) }
        for (hz in m.feedbackHz) drawFeedbackMarker(canvas, r, hz)
        if (selected != null && selected in m.bands.indices) {
            val b = m.bands[selected]
            canvas.drawRect(x(b.lowHz, r), r.top, x(b.highHz, r), r.bottom, selectionPaint)
        }
        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas, r: RectF, m: ChartModel) {
        val step = when {
            m.yRangeDb <= 24 -> 3.0
            m.yRangeDb <= 48 -> 6.0
            else -> 10.0
        }
        val lowest = m.yTopDb - m.yRangeDb
        var db = ceil(lowest / step) * step
        textPaint.textAlign = Paint.Align.RIGHT
        while (db <= m.yTopDb + 1e-9) {
            val yy = y(db, r, m)
            canvas.drawLine(r.left, yy, r.right, yy, gridPaint)
            canvas.drawText(formatDb(db), r.left - 3f * density, yy + 3.5f * density, textPaint)
            db += step
        }
        textPaint.textAlign = Paint.Align.CENTER
        val labeled = listOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0)
        for (decade in listOf(10.0, 100.0, 1000.0, 10000.0)) for (k in 1..9) {
            val hz = decade * k
            if (hz < MIN_HZ || hz > MAX_HZ) continue
            val xx = x(hz, r)
            canvas.drawLine(xx, r.top, xx, r.bottom, gridPaint)
        }
        for (hz in labeled) {
            val xx = x(hz, r).coerceIn(r.left + 8 * density, r.right - 8 * density)
            canvas.drawText(if (hz >= 1000) "${(hz / 1000).toInt()}k" else hz.toInt().toString(), xx, r.bottom + 12f * density, textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(m.unit, 2f * density, r.top + 9f * density, textPaint)
    }

    private fun curvePoints(r: RectF, m: ChartModel, curve: TargetCurve, delta: Double): List<Pair<Float, Float>> {
        val n = 160
        return (0..n).map { i ->
            val hz = MIN_HZ * (MAX_HZ / MIN_HZ).pow(i.toDouble() / n)
            x(hz, r) to y(curve.relativeGainAt(hz) + m.curveOffsetDb + delta, r, m)
        }
    }

    private fun drawSilhouette(canvas: Canvas, r: RectF, m: ChartModel, curve: TargetCurve) {
        // Silhouette: the area under the target curve.
        val main = curvePoints(r, m, curve, 0.0)
        val fill = Path().apply {
            moveTo(main.first().first, r.bottom)
            main.forEach { lineTo(it.first, it.second) }
            lineTo(main.last().first, r.bottom)
            close()
        }
        canvas.drawPath(fill, silhouettePaint)
        // Tolerance band: target ± tolerance.
        val upper = curvePoints(r, m, curve, m.toleranceDb)
        val lower = curvePoints(r, m, curve, -m.toleranceDb)
        val band = Path().apply {
            moveTo(upper.first().first, upper.first().second)
            upper.forEach { lineTo(it.first, it.second) }
            lower.asReversed().forEach { lineTo(it.first, it.second) }
            close()
        }
        canvas.drawPath(band, tolerancePaint)
    }

    private fun drawTargetLine(canvas: Canvas, r: RectF, m: ChartModel, curve: TargetCurve) {
        val pts = curvePoints(r, m, curve, 0.0)
        val path = Path().apply {
            moveTo(pts.first().first, pts.first().second)
            pts.drop(1).forEach { lineTo(it.first, it.second) }
        }
        canvas.drawPath(path, targetLinePaint)
    }

    private fun drawBars(canvas: Canvas, r: RectF, m: ChartModel) {
        val levels = m.measuredDb ?: return
        val gap = 1f * density
        for ((i, band) in m.bands.withIndex()) {
            if (i >= levels.size) break
            val x0 = x(band.lowHz, r) + gap
            val x1 = x(band.highHz, r) - gap
            if (x1 <= x0) continue
            barPaint.color = withAlpha(
                when (m.statuses?.getOrNull(i)) {
                    BandStatus.OK -> COLOR_OK
                    BandStatus.WARNING -> COLOR_WARNING
                    BandStatus.OUT -> COLOR_OUT
                    else -> COLOR_NEUTRAL
                },
                0xD0,
            )
            canvas.drawRect(x0, y(levels[i], r, m), x1, r.bottom, barPaint)
        }
    }

    private fun drawBandLine(canvas: Canvas, r: RectF, m: ChartModel, levels: DoubleArray, paint: Paint) {
        val path = Path()
        var started = false
        for ((i, band) in m.bands.withIndex()) {
            if (i >= levels.size) break
            val xx = x(band.centerHz, r)
            val yy = y(levels[i], r, m)
            if (!started) { path.moveTo(xx, yy); started = true } else path.lineTo(xx, yy)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawLine(canvas: Canvas, r: RectF, m: ChartModel, hz: DoubleArray, db: DoubleArray, paint: Paint) {
        if (hz.isEmpty()) return
        val path = Path()
        path.moveTo(x(hz[0], r), y(db[0], r, m))
        for (i in 1 until hz.size) path.lineTo(x(hz[i], r), y(db[i], r, m))
        canvas.drawPath(path, paint)
    }

    private fun drawFeedbackMarker(canvas: Canvas, r: RectF, hz: Double) {
        val xx = x(hz, r)
        val s = 6f * density
        val path = Path().apply {
            moveTo(xx, r.top + 2 * s)
            lineTo(xx - s, r.top)
            lineTo(xx + s, r.top)
            close()
        }
        canvas.drawPath(path, feedbackPaint)
    }

    private fun formatDb(v: Double): String = if (v == floor(v)) v.toInt().toString() else "%.1f".format(v)

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
