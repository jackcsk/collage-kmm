package com.jackcheung.collage.layout

import androidx.compose.ui.geometry.Offset
import kotlin.math.max

/**
 * How a photo sits in its cell, independent of the photo's pixel resolution and the cell's size,
 * so the same values render identically on screen and in the saved image.
 *
 * The photo always covers the cell. [zoom] multiplies the cover scale; ([focusX], [focusY]) is the
 * point of the photo, as fractions of its width and height, shown at the cell centre.
 */
data class PhotoTransform(
    val zoom: Float = 1f,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val mirrored: Boolean = false,
    val flipped: Boolean = false,
) {
    /**
     * Resolves this transform for an image of [imageWidth]×[imageHeight] px in a cell of
     * [cellWidth]×[cellHeight] px. The focus is pulled in so the image never leaves a gap.
     */
    fun placement(imageWidth: Float, imageHeight: Float, cellWidth: Float, cellHeight: Float): Placement {
        val scale = max(cellWidth / imageWidth, cellHeight / imageHeight) * zoom
        return Placement(
            scale = scale,
            centerX = clampCenter(focusX * imageWidth, imageWidth, cellWidth / (2f * scale)),
            centerY = clampCenter(focusY * imageHeight, imageHeight, cellHeight / (2f * scale)),
        )
    }

    /**
     * Applies one step of a pan/pinch gesture. [pan] is the centroid movement and [centroid] the
     * current centroid relative to the cell centre, both in screen pixels. The photo point that was
     * under the previous centroid ends up under the current one.
     */
    fun transformed(
        pan: Offset,
        zoomChange: Float,
        centroid: Offset,
        imageWidth: Float,
        imageHeight: Float,
        cellWidth: Float,
        cellHeight: Float,
    ): PhotoTransform {
        val before = placement(imageWidth, imageHeight, cellWidth, cellHeight)
        // Screen directions are reversed inside a mirrored or flipped photo.
        val sx = if (mirrored) -1f else 1f
        val sy = if (flipped) -1f else 1f
        val previous = Offset((centroid.x - pan.x) * sx, (centroid.y - pan.y) * sy)
        val current = Offset(centroid.x * sx, centroid.y * sy)

        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val newScale = before.scale / zoom * newZoom
        val anchorX = before.centerX + previous.x / before.scale
        val anchorY = before.centerY + previous.y / before.scale
        val moved = copy(
            zoom = newZoom,
            focusX = (anchorX - current.x / newScale) / imageWidth,
            focusY = (anchorY - current.y / newScale) / imageHeight,
        )
        // Store the clamped focus so dragging past an edge doesn't build up hidden slack.
        val clamped = moved.placement(imageWidth, imageHeight, cellWidth, cellHeight)
        return moved.copy(focusX = clamped.centerX / imageWidth, focusY = clamped.centerY / imageHeight)
    }

    companion object {
        const val MAX_ZOOM = 5f
    }
}

/** [scale] maps image px to canvas px; ([centerX], [centerY]) is the image px drawn at the cell centre. */
data class Placement(val scale: Float, val centerX: Float, val centerY: Float)

private fun clampCenter(center: Float, imageLength: Float, halfVisible: Float): Float =
    if (halfVisible * 2f >= imageLength) imageLength / 2f else center.coerceIn(halfVisible, imageLength - halfVisible)
