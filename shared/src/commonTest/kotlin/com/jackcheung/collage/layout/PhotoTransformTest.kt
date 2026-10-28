package com.jackcheung.collage.layout

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoTransformTest {
    // A 200×100 landscape photo in a 100×100 cell: cover scale 1, 100 px of horizontal slack.
    private val imageW = 200f
    private val imageH = 100f
    private val cell = 100f

    @Test
    fun photoAlwaysCoversTheCell() {
        val placement = PhotoTransform(focusX = 0f, focusY = 0f).placement(imageW, imageH, cell, cell)

        assertEquals(1f, placement.scale)
        assertEquals(50f, placement.centerX)
        assertEquals(50f, placement.centerY)
    }

    @Test
    fun panningMovesThePhotoWithTheFingerAndStopsAtItsEdge() {
        val start = PhotoTransform()

        assertEquals(130f, start.pan(Offset(-30f, 0f)).focusX * imageW, 1e-3f)

        val pastEdge = start.pan(Offset(-500f, 0f))
        assertEquals(150f, pastEdge.focusX * imageW, 1e-3f)
        // No hidden slack: coming back moves the photo straight away.
        assertEquals(140f, pastEdge.pan(Offset(10f, 0f)).focusX * imageW, 1e-3f)
    }

    @Test
    fun panningAMirroredPhotoStillFollowsTheFinger() {
        val mirrored = PhotoTransform(mirrored = true).pan(Offset(-30f, 0f))

        assertEquals(70f, mirrored.focusX * imageW, 1e-3f)
    }

    @Test
    fun pinchKeepsThePointUnderTheFingersInPlace() {
        val centroid = Offset(20f, 10f)
        val start = PhotoTransform()
        val before = start.placement(imageW, imageH, cell, cell)
        val pointX = before.centerX + centroid.x / before.scale
        val pointY = before.centerY + centroid.y / before.scale

        val zoomed = start.transformed(Offset.Zero, 2f, centroid, imageW, imageH, cell, cell)
        val after = zoomed.placement(imageW, imageH, cell, cell)

        assertEquals(2f, zoomed.zoom)
        assertEquals(pointX, after.centerX + centroid.x / after.scale, 1e-3f)
        assertEquals(pointY, after.centerY + centroid.y / after.scale, 1e-3f)
    }

    @Test
    fun zoomStaysBetweenCoverAndTheMaximum() {
        val start = PhotoTransform()

        assertEquals(1f, start.transformed(Offset.Zero, 0.2f, Offset.Zero, imageW, imageH, cell, cell).zoom)
        assertEquals(
            PhotoTransform.MAX_ZOOM,
            start.transformed(Offset.Zero, 50f, Offset.Zero, imageW, imageH, cell, cell).zoom,
        )
    }

    private fun PhotoTransform.pan(by: Offset) = transformed(by, 1f, by, imageW, imageH, cell, cell)
}
