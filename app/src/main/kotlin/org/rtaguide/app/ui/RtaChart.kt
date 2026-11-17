package org.rtaguide.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

/**
 * RTA chart. Gestures (when the callbacks are given):
 *  - tap: places the frequency cursor at the exact frequency; double tap removes it
 *  - horizontal drag: moves the cursor
 *  - vertical drag: moves the dB window; pinch: zooms it
 */
@Composable
fun RtaChart(
    model: ChartModel,
    modifier: Modifier = Modifier,
    onCursor: ((Double?) -> Unit)? = null,
    onPanZoom: ((Double, Float) -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    val renderer = remember(density) { ChartRenderer(density) }
    val currentModel by rememberUpdatedState(model)
    val currentCursor by rememberUpdatedState(onCursor)
    val currentPanZoom by rememberUpdatedState(onPanZoom)
    Canvas(
        modifier.clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { currentCursor?.invoke(null) },
                    onTap = { offset -> currentCursor?.invoke(renderer.hzAt(offset.x, size.width.toFloat())) },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val width = size.width.toFloat()
                    val cursorCallback = currentCursor
                    if (zoom == 1f && abs(pan.x) > abs(pan.y) && cursorCallback != null) {
                        renderer.hzAt(centroid.x, width)?.let { cursorCallback(it) }
                    } else {
                        val plotHeight = renderer.plotRect(width, size.height.toFloat()).height()
                        if (plotHeight > 0f) currentPanZoom?.invoke(pan.y * currentModel.yRangeDb / plotHeight, zoom)
                    }
                }
            },
    ) {
        drawIntoCanvas { canvas ->
            renderer.draw(canvas.nativeCanvas, size.width, size.height, model)
        }
    }
}

/** Frequency as printed on the cursor: 31.6 Hz, 125 Hz, 1.25 kHz, 12.6 kHz. */
fun formatCursorHz(hz: Double): String = when {
    hz < 100 -> "%.1f Hz".format(hz)
    hz < 1000 -> "%.0f Hz".format(hz)
    hz < 10000 -> "%.2f kHz".format(hz / 1000)
    else -> "%.1f kHz".format(hz / 1000)
}
