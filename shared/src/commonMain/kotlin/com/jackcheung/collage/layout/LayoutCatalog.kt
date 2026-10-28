package com.jackcheung.collage.layout

/** A ready-made arrangement offered for a given number of photos. */
data class LayoutTemplate(val id: String, val root: LayoutNode) {
    val cellCount: Int = root.cellCount
}

object LayoutCatalog {
    const val MAX_PHOTOS = 9

    private val templates: Map<Int, List<LayoutTemplate>> = mapOf(
        1 to listOf(C),
        2 to listOf(
            row(C, C),
            col(C, C),
            row(2f to C, 1f to C),
            col(2f to C, 1f to C),
        ),
        3 to listOf(
            row(C, col(C, C)),
            col(C, row(C, C)),
            row(C, C, C),
            col(C, C, C),
            row(col(C, C), C),
            col(row(C, C), C),
        ),
        4 to listOf(
            col(row(C, C), row(C, C)),
            row(2f to C, 1f to col(C, C, C)),
            col(2f to C, 1f to row(C, C, C)),
            row(C, C, C, C),
            col(C, C, C, C),
            row(1f to col(C, C, C), 2f to C),
            col(1f to row(C, C, C), 2f to C),
        ),
        5 to listOf(
            col(row(C, C), row(C, C, C)),
            row(col(C, C), col(C, C, C)),
            col(2f to C, 1f to row(C, C, C, C)),
            row(2f to C, 1f to col(C, C, C, C)),
            row(col(C, C), C, col(C, C)),
            col(row(C, C), C, row(C, C)),
            col(row(C, C, C), row(C, C)),
        ),
        6 to listOf(
            col(row(C, C, C), row(C, C, C)),
            row(col(C, C, C), col(C, C, C)),
            col(2f to row(2f to C, 1f to col(C, C)), 1f to row(C, C, C)),
            col(row(C, C), row(C, C), row(C, C)),
            col(C, row(C, C), row(C, C, C)),
            row(2f to C, 1f to col(C, C, C, C, C)),
        ),
        7 to listOf(
            col(row(C, C), row(C, C, C), row(C, C)),
            col(row(C, C, C), C, row(C, C, C)),
            row(col(C, C, C), C, col(C, C, C)),
            col(2f to C, 1f to row(C, C, C), 1f to row(C, C, C)),
            col(row(C, C, C, C), row(C, C, C)),
        ),
        8 to listOf(
            col(row(C, C, C, C), row(C, C, C, C)),
            row(col(C, C, C, C), col(C, C, C, C)),
            col(row(C, C, C), row(C, C), row(C, C, C)),
            col(2f to C, 1f to row(C, C, C), 1f to row(C, C, C, C)),
        ),
        9 to listOf(
            col(row(C, C, C), row(C, C, C), row(C, C, C)),
            col(row(C, C), row(C, C, C), row(C, C, C, C)),
            col(row(C, C, C, C), C, row(C, C, C, C)),
            row(col(C, C, C, C), C, col(C, C, C, C)),
        ),
    ).mapValues { (count, specs) ->
        specs.mapIndexed { i, spec -> LayoutTemplate(id = "$count-$i", root = spec.build()) }
    }

    /** Templates for [count] photos, best first; empty when [count] is outside 1..[MAX_PHOTOS]. */
    fun templatesFor(count: Int): List<LayoutTemplate> = templates[count].orEmpty()
}

/** Compact description of a template; cells are numbered when built. */
private sealed interface Spec

private data object C : Spec

private class SplitSpec(val axis: Axis, val parts: List<Pair<Float, Spec>>) : Spec

private fun row(vararg parts: Spec) = SplitSpec(Axis.Row, parts.map { 1f to it })

private fun row(vararg parts: Pair<Float, Spec>) = SplitSpec(Axis.Row, parts.toList())

private fun col(vararg parts: Spec) = SplitSpec(Axis.Column, parts.map { 1f to it })

private fun col(vararg parts: Pair<Float, Spec>) = SplitSpec(Axis.Column, parts.toList())

private fun Spec.build(): LayoutNode {
    var next = 0
    fun Spec.toNode(): LayoutNode = when (this) {
        C -> LayoutNode.Cell(next++)
        is SplitSpec -> {
            val total = parts.sumOf { it.first.toDouble() }.toFloat()
            LayoutNode.Split(
                axis = axis,
                children = parts.map { it.second.toNode() },
                weights = parts.map { it.first / total },
            )
        }
    }
    return toNode()
}
