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

@Composable
fun RtaChart(
    model: ChartModel,
    modifier: Modifier = Modifier,
    selectedBand: Int? = null,
    onTapBand: (Int?) -> Unit = {},
    /** Vertical drag (dB to shift the window) and pinch (zoom factor); null disables the gestures. */
    onPanZoom: ((Double, Float) -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    val renderer = remember(density) { ChartRenderer(density) }
    val currentModel by rememberUpdatedState(model)
    val currentTap by rememberUpdatedState(onTapBand)
    val currentPanZoom by rememberUpdatedState(onPanZoom)
    Canvas(
        modifier.clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentTap(renderer.bandAt(currentModel, offset.x, size.width.toFloat()))
                }
            }
            .pointerInput(onPanZoom != null) {
                if (onPanZoom == null) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val plotHeight = renderer.plotRect(size.width.toFloat(), size.height.toFloat()).height()
                    if (plotHeight > 0f) {
                        val dbPerPixel = currentModel.yRangeDb / plotHeight
                        currentPanZoom?.invoke(pan.y * dbPerPixel, zoom)
                    }
                }
            },
    ) {
        drawIntoCanvas { canvas ->
            renderer.draw(canvas.nativeCanvas, size.width, size.height, model, selectedBand)
        }
    }
}
