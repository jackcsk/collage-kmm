package com.jackcheung.collage.editor

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.jackcheung.collage.layout.LayoutNode
import com.jackcheung.collage.layout.borderWidth
import com.jackcheung.collage.layout.geometry
import com.jackcheung.collage.platform.MediaRepository
import com.jackcheung.collage.platform.PhotoRef
import com.jackcheung.collage.platform.encodeJpeg
import com.jackcheung.collage.ui.drawPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

const val EXPORT_SIZE = 2048
private const val MAX_DECODE_SIDE = 4096
private const val JPEG_QUALITY = 95

/**
 * Renders the collage at [sizePx]×[sizePx], decoding each photo at the resolution its cell needs,
 * and saves it to the photo library. Uses the same drawing code as the editor, so the saved image
 * matches the screen.
 */
suspend fun MediaRepository.saveCollage(
    layout: LayoutNode,
    cells: List<CollageCell>,
    border: Boolean,
    sizePx: Int = EXPORT_SIZE,
): PhotoRef {
    val jpeg = withContext(Dispatchers.Default) {
        val size = Size(sizePx.toFloat(), sizePx.toFloat())
        val geometry = layout.geometry(size, if (border) borderWidth(size) else 0f)
        val image = ImageBitmap(sizePx, sizePx)
        val canvas = Canvas(image)
        val scope = CanvasDrawScope()
        fun draw(block: DrawScope.() -> Unit) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size, block)

        draw { drawRect(Color.White) }
        cells.forEachIndexed { i, cell ->
            val rect = geometry.cells[i]
            // Enough pixels for the cell at the current zoom whichever way the photo is oriented.
            val photo = decode(cell.photo) { w, h ->
                min(max(rect.width, rect.height) * cell.transform.zoom / min(w, h), MAX_DECODE_SIDE.toFloat() / max(w, h))
            } ?: error("Can't read ${cell.photo}")
            draw { drawPhoto(photo, rect, cell.transform) }
        }
        image.encodeJpeg(JPEG_QUALITY)
    }
    return saveJpeg(jpeg)
}
