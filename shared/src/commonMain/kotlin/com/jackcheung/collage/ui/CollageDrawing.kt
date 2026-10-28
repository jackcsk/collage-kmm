package com.jackcheung.collage.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.jackcheung.collage.editor.CollageCell
import com.jackcheung.collage.layout.CollageGeometry
import com.jackcheung.collage.layout.PhotoTransform
import com.jackcheung.collage.platform.PhotoRef

/** Draws [image] into [rect] as described by [transform], clipped to the cell. */
fun DrawScope.drawPhoto(image: ImageBitmap, rect: Rect, transform: PhotoTransform, alpha: Float = 1f) {
    val placement = transform.placement(image.width.toFloat(), image.height.toFloat(), rect.width, rect.height)
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        withTransform({
            translate(rect.center.x, rect.center.y)
            scale(
                scaleX = if (transform.mirrored) -placement.scale else placement.scale,
                scaleY = if (transform.flipped) -placement.scale else placement.scale,
                pivot = Offset.Zero,
            )
            translate(-placement.centerX, -placement.centerY)
        }) {
            drawImage(image, alpha = alpha)
        }
    }
}

/** Background, then every cell: its photo when loaded, otherwise [placeholder]. */
fun DrawScope.drawCollage(
    geometry: CollageGeometry,
    cells: List<CollageCell>,
    image: (PhotoRef) -> ImageBitmap?,
    background: Color,
    placeholder: Color,
) {
    drawRect(background)
    geometry.cells.forEachIndexed { i, rect ->
        val cell = cells.getOrNull(i)
        val bitmap = cell?.let { image(it.photo) }
        if (cell != null && bitmap != null) {
            drawPhoto(bitmap, rect, cell.transform)
        } else {
            drawRect(placeholder, rect.topLeft, rect.size)
        }
    }
}
