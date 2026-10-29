package com.jackcheung.collage.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.jackcheung.collage.editor.EditorState
import com.jackcheung.collage.layout.Axis
import com.jackcheung.collage.layout.CollageGeometry
import com.jackcheung.collage.layout.borderWidth
import com.jackcheung.collage.layout.geometry
import com.jackcheung.collage.platform.PhotoRef
import com.jackcheung.collage.ui.drawCollage
import com.jackcheung.collage.ui.drawPhoto
import kotlin.math.abs

/** A photo lifted by a long press, following the finger until dropped on another cell. */
private data class SwapDrag(val from: Int, val position: Offset)

private enum class CellGesture { Tap, Transform, Swap }

/**
 * The collage with Layout-style editing: tap selects a cell, drag/pinch moves and zooms its
 * photo, dragging a boundary resizes neighbouring cells, and hold-then-drag swaps two photos.
 */
@Composable
fun CollageEditorCanvas(
    state: EditorState,
    image: (PhotoRef) -> ImageBitmap?,
    onSelect: (Int?) -> Unit,
    onTransform: (index: Int, pan: Offset, zoomChange: Float, centroid: Offset, cellSize: Size) -> Unit,
    onMoveDivider: (path: List<Int>, index: Int, delta: Float) -> Unit,
    onSwap: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentState by rememberUpdatedState(state)
    val select by rememberUpdatedState(onSelect)
    val transform by rememberUpdatedState(onTransform)
    val moveDivider by rememberUpdatedState(onMoveDivider)
    val swapCells by rememberUpdatedState(onSwap)
    var swap by remember { mutableStateOf<SwapDrag?>(null) }
    val haptics = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    val placeholder = MaterialTheme.colorScheme.surfaceVariant

    val gestures = Modifier.pointerInput(Unit) {
        val touchRadius = 20.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown()
            val canvasSize = size.toSize()
            fun geometry() = currentState.collageGeometry(canvasSize)

            val divider = geometry().dividerAt(down.position, touchRadius)
            if (divider != null) {
                down.consume()
                // Track the finger's absolute position so the boundary stays under it after
                // hitting a size limit and coming back.
                val origin = divider.position
                drag(down.id) { change ->
                    val travel = change.position - down.position
                    val wanted = origin + if (divider.axis == Axis.Row) travel.x else travel.y
                    val current = geometry().dividers.firstOrNull { it.path == divider.path && it.index == divider.index }
                    if (current != null) {
                        moveDivider(divider.path, divider.index, (wanted - current.position) / current.splitLength)
                    }
                    change.consume()
                }
                return@awaitEachGesture
            }

            val cell = geometry().cellAt(down.position) ?: return@awaitEachGesture
            val gesture = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                classify(down, viewConfiguration.touchSlop)
            } ?: CellGesture.Swap

            when (gesture) {
                CellGesture.Tap -> select(if (currentState.selected == cell) null else cell)
                CellGesture.Transform -> {
                    select(cell)
                    // Classification already read the movement that crossed touch slop.
                    var event = currentEvent
                    do {
                        if (event.changes.any { it.isConsumed }) break
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if ((zoomChange != 1f || pan != Offset.Zero) && centroid.isSpecified) {
                            val rect = geometry().cells[cell]
                            transform(cell, pan, zoomChange, centroid - rect.center, rect.size)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                        event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                }
                CellGesture.Swap -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    select(null)
                    var position = down.position
                    swap = SwapDrag(cell, position)
                    drag(down.id) { change ->
                        position = change.position
                        swap = SwapDrag(cell, position)
                        change.consume()
                    }
                    swap = null
                    val target = geometry().cellAt(position)
                    if (target != null && target != cell) swapCells(cell, target)
                }
            }
        }
    }

    Canvas(modifier.then(gestures)) {
        val geometry = state.collageGeometry(size)
        drawCollage(geometry, state.cells, image, background = Color.White, placeholder = placeholder)
        val stroke = 3.dp.toPx()

        state.selected?.let { index ->
            outline(geometry.cells[index], accent, stroke)
            drawDividerHandles(geometry, index, accent)
        }

        swap?.let { drag ->
            drawRect(Color.White.copy(alpha = 0.6f), geometry.cells[drag.from].topLeft, geometry.cells[drag.from].size)
            val target = geometry.cellAt(drag.position)
            if (target != null && target != drag.from) outline(geometry.cells[target], accent, stroke)
            val cell = state.cells[drag.from]
            image(cell.photo)?.let { bitmap ->
                val lifted = Rect(center = drag.position, radius = 44.dp.toPx())
                drawPhoto(bitmap, lifted, cell.transform, alpha = 0.85f)
                outline(lifted, Color.White, 2.dp.toPx())
            }
        }
    }
}

private fun EditorState.collageGeometry(size: Size): CollageGeometry =
    layout.geometry(size, if (border) borderWidth(size) else 0f)

/** Waits until the first touch is a tap (lifted), or a transform (moved or second finger). */
private suspend fun AwaitPointerEventScope.classify(down: PointerInputChange, touchSlop: Float): CellGesture {
    while (true) {
        val pressed = awaitPointerEvent().changes.filter { it.pressed }
        if (pressed.isEmpty()) return CellGesture.Tap
        if (pressed.size > 1) return CellGesture.Transform
        if ((pressed.first().position - down.position).getDistance() > touchSlop) return CellGesture.Transform
    }
}

private fun DrawScope.outline(rect: Rect, color: Color, stroke: Float) {
    drawRect(
        color = color,
        topLeft = rect.topLeft + Offset(stroke / 2, stroke / 2),
        size = Size(rect.width - stroke, rect.height - stroke),
        style = Stroke(stroke),
    )
}

/** Grab handles on each edge of the selected cell that can be dragged to resize it. */
private fun DrawScope.drawDividerHandles(geometry: CollageGeometry, index: Int, color: Color) {
    val frame = geometry.frames[index]
    val cell = geometry.cells[index]
    val long = 28.dp.toPx()
    val short = 6.dp.toPx()
    val epsilon = 0.5f
    geometry.dividers.forEach { divider ->
        val center = when (divider.axis) {
            Axis.Row -> {
                if (divider.start > frame.top + epsilon || divider.end < frame.bottom - epsilon) return@forEach
                when {
                    abs(divider.position - frame.left) < epsilon -> Offset(cell.left, cell.center.y)
                    abs(divider.position - frame.right) < epsilon -> Offset(cell.right, cell.center.y)
                    else -> return@forEach
                }
            }
            Axis.Column -> {
                if (divider.start > frame.left + epsilon || divider.end < frame.right - epsilon) return@forEach
                when {
                    abs(divider.position - frame.top) < epsilon -> Offset(cell.center.x, cell.top)
                    abs(divider.position - frame.bottom) < epsilon -> Offset(cell.center.x, cell.bottom)
                    else -> return@forEach
                }
            }
        }
        val size = if (divider.axis == Axis.Row) Size(short, long) else Size(long, short)
        val topLeft = center - Offset(size.width / 2, size.height / 2)
        val radius = CornerRadius(short / 2)
        drawRoundRect(Color.White, topLeft - Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width + 2.dp.toPx(), size.height + 2.dp.toPx()), CornerRadius(short / 2 + 1.dp.toPx()))
        drawRoundRect(color, topLeft, size, radius)
    }
}
