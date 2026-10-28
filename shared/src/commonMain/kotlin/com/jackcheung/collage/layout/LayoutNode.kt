package com.jackcheung.collage.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.min

/** Direction a [LayoutNode.Split] lays out its children. */
enum class Axis {
    /** Children side by side, left to right. */
    Row,

    /** Children stacked, top to bottom. */
    Column,
}

/**
 * A collage layout as a tree of splits. Leaves are photo slots numbered `0 until cellCount` in
 * depth-first order; the slot number indexes the photo list.
 */
sealed interface LayoutNode {
    data class Cell(val index: Int) : LayoutNode

    /** [weights] are fractions of this split's extent along [axis] and sum to 1. */
    data class Split(
        val axis: Axis,
        val children: List<LayoutNode>,
        val weights: List<Float>,
    ) : LayoutNode {
        init {
            require(children.size >= 2) { "A split needs at least two children" }
            require(weights.size == children.size) { "One weight per child" }
        }
    }
}

val LayoutNode.cellCount: Int
    get() = when (this) {
        is LayoutNode.Cell -> 1
        is LayoutNode.Split -> children.sumOf { it.cellCount }
    }

/**
 * A draggable boundary between children [index] and `index + 1` of the split at [path].
 * [position] is the boundary coordinate along [axis]; the boundary spans [start]..[end] on the
 * other axis. [splitLength] is the split's extent along [axis], used to turn pixels into weight.
 */
data class Divider(
    val path: List<Int>,
    val index: Int,
    val axis: Axis,
    val position: Float,
    val start: Float,
    val end: Float,
    val splitLength: Float,
) {
    /** Distance from [point] to this divider, or null when [point] is beyond its ends. */
    fun distanceTo(point: Offset): Float? {
        val along = if (axis == Axis.Row) point.x else point.y
        val across = if (axis == Axis.Row) point.y else point.x
        return if (across in start..end) abs(along - position) else null
    }
}

/**
 * Resolved geometry of a layout on a canvas. [frames] tile the canvas without gaps (used for hit
 * testing); [cells] are the frames shrunk by the border, where photos are drawn. Both are indexed
 * by cell number.
 */
class CollageGeometry(
    val frames: List<Rect>,
    val cells: List<Rect>,
    val dividers: List<Divider>,
) {
    fun cellAt(point: Offset): Int? = frames.indexOfFirst { it.contains(point) }.takeIf { it >= 0 }

    fun dividerAt(point: Offset, touchRadius: Float): Divider? = dividers
        .mapNotNull { divider -> divider.distanceTo(point)?.let { divider to it } }
        .filter { (_, distance) -> distance <= touchRadius }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

/** Border thickness, proportional to the canvas so the saved image matches the screen. */
fun borderWidth(canvasSize: Size): Float = min(canvasSize.width, canvasSize.height) * 0.02f

/** Lays the tree out on a canvas of [size] with a uniform [gap] between and around the cells. */
fun LayoutNode.geometry(size: Size, gap: Float): CollageGeometry {
    val frames = arrayOfNulls<Rect>(cellCount)
    val dividers = mutableListOf<Divider>()
    val half = gap / 2f

    fun visit(node: LayoutNode, rect: Rect, path: List<Int>) {
        when (node) {
            is LayoutNode.Cell -> frames[node.index] = rect
            is LayoutNode.Split -> {
                val row = node.axis == Axis.Row
                val length = if (row) rect.width else rect.height
                var cursor = if (row) rect.left else rect.top
                node.children.forEachIndexed { i, child ->
                    val extent = length * node.weights[i]
                    val childRect = if (row) {
                        Rect(cursor, rect.top, cursor + extent, rect.bottom)
                    } else {
                        Rect(rect.left, cursor, rect.right, cursor + extent)
                    }
                    visit(child, childRect, path + i)
                    cursor += extent
                    if (i < node.children.lastIndex) {
                        dividers += Divider(
                            path = path,
                            index = i,
                            axis = node.axis,
                            position = cursor,
                            start = if (row) rect.top else rect.left,
                            end = if (row) rect.bottom else rect.right,
                            splitLength = length,
                        )
                    }
                }
            }
        }
    }

    visit(this, Rect(half, half, size.width - half, size.height - half), emptyList())
    val resolved = frames.map { requireNotNull(it) { "Cell numbers must be 0 until cellCount" } }
    return CollageGeometry(
        frames = resolved,
        cells = resolved.map { it.deflate(half) },
        dividers = dividers,
    )
}

/** Smallest fraction a child of a split with [childCount] children may shrink to. */
fun minWeight(childCount: Int): Float = min(0.15f, 0.6f / childCount)

/**
 * Moves divider [index] of the split at [path] by [delta], a fraction of that split's extent.
 * Only the two neighbouring children change, and neither shrinks below [minWeight].
 */
fun LayoutNode.moveDivider(path: List<Int>, index: Int, delta: Float): LayoutNode = updateSplit(path) { split ->
    val weights = split.weights.toMutableList()
    val pair = weights[index] + weights[index + 1]
    val floor = minWeight(weights.size)
    if (pair - floor < floor) return@updateSplit split
    val first = (weights[index] + delta).coerceIn(floor, pair - floor)
    weights[index] = first
    weights[index + 1] = pair - first
    split.copy(weights = weights)
}

private fun LayoutNode.updateSplit(path: List<Int>, transform: (LayoutNode.Split) -> LayoutNode.Split): LayoutNode {
    val split = this as? LayoutNode.Split ?: error("No split at path")
    if (path.isEmpty()) return transform(split)
    val head = path.first()
    return split.copy(
        children = split.children.mapIndexed { i, child ->
            if (i == head) child.updateSplit(path.drop(1), transform) else child
        },
    )
}
