package com.jackcheung.collage.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LayoutGeometryTest {
    /** Left half, and the right half split into top and bottom. */
    private val layout = LayoutNode.Split(
        axis = Axis.Row,
        children = listOf(
            LayoutNode.Cell(0),
            LayoutNode.Split(Axis.Column, listOf(LayoutNode.Cell(1), LayoutNode.Cell(2)), listOf(0.5f, 0.5f)),
        ),
        weights = listOf(0.5f, 0.5f),
    )

    @Test
    fun gapIsUniformBetweenCellsAndAroundTheEdge() {
        val geometry = layout.geometry(Size(100f, 100f), gap = 10f)

        assertEquals(Rect(10f, 10f, 45f, 90f), geometry.cells[0])
        assertEquals(Rect(55f, 10f, 90f, 45f), geometry.cells[1])
        assertEquals(Rect(55f, 55f, 90f, 90f), geometry.cells[2])
    }

    @Test
    fun touchesInTheGapBelongToTheNearestCell() {
        val geometry = layout.geometry(Size(100f, 100f), gap = 10f)

        assertEquals(0, geometry.cellAt(Offset(48f, 20f)))
        assertEquals(2, geometry.cellAt(Offset(70f, 52f)))
        assertNull(geometry.cellAt(Offset(120f, 20f)))
    }

    @Test
    fun dividerHitTestingPrefersTheClosestBoundaryWithinItsSpan() {
        val geometry = layout.geometry(Size(100f, 100f), gap = 0f)

        val vertical = geometry.dividerAt(Offset(52f, 20f), touchRadius = 8f)
        assertEquals(emptyList(), vertical?.path)
        assertEquals(0, vertical?.index)

        val horizontal = geometry.dividerAt(Offset(75f, 47f), touchRadius = 8f)
        assertEquals(listOf(1), horizontal?.path)

        // The right column's boundary doesn't extend into the left cell.
        assertNull(geometry.dividerAt(Offset(20f, 50f), touchRadius = 8f))
    }

    @Test
    fun movingADividerOnlyResizesItsNeighboursAndStopsAtTheMinimum() {
        val three = LayoutNode.Split(Axis.Row, List(3) { LayoutNode.Cell(it) }, List(3) { 1f / 3 })

        val moved = three.moveDivider(emptyList(), 0, 0.1f) as LayoutNode.Split
        assertEquals(1f / 3 + 0.1f, moved.weights[0], 1e-5f)
        assertEquals(1f / 3 - 0.1f, moved.weights[1], 1e-5f)
        assertEquals(1f / 3, moved.weights[2], 1e-5f)

        val clamped = three.moveDivider(emptyList(), 0, 0.9f) as LayoutNode.Split
        assertEquals(minWeight(3), clamped.weights[1], 1e-5f)
        assertEquals(1f, clamped.weights.sum(), 1e-5f)
    }

    @Test
    fun movingANestedDividerLeavesTheRestOfTheTreeAlone() {
        val moved = layout.moveDivider(listOf(1), 0, -0.2f) as LayoutNode.Split

        assertEquals(layout.weights, moved.weights)
        val column = moved.children[1] as LayoutNode.Split
        assertEquals(0.3f, column.weights[0], 1e-5f)
        assertEquals(0.7f, column.weights[1], 1e-5f)
    }
}
