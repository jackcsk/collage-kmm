package com.jackcheung.collage.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutCatalogTest {
    @Test
    fun everyPhotoCountHasTemplatesThatUseEachPhotoExactlyOnce() {
        for (count in 1..LayoutCatalog.MAX_PHOTOS) {
            val templates = LayoutCatalog.templatesFor(count)
            assertTrue(templates.isNotEmpty(), "no templates for $count")
            templates.forEach { template ->
                assertEquals((0 until count).toList(), template.root.cellIndices(), template.id)
                template.root.splits().forEach { split ->
                    assertEquals(1f, split.weights.sum(), 1e-4f, template.id)
                    split.weights.forEach { assertTrue(it >= minWeight(split.weights.size), template.id) }
                }
            }
        }
    }

    @Test
    fun templateIdsAreUnique() {
        val ids = (1..LayoutCatalog.MAX_PHOTOS).flatMap { LayoutCatalog.templatesFor(it) }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun countsOutsideTheSupportedRangeHaveNoTemplates() {
        assertTrue(LayoutCatalog.templatesFor(0).isEmpty())
        assertTrue(LayoutCatalog.templatesFor(LayoutCatalog.MAX_PHOTOS + 1).isEmpty())
    }

    private fun LayoutNode.cellIndices(): List<Int> = when (this) {
        is LayoutNode.Cell -> listOf(index)
        is LayoutNode.Split -> children.flatMap { it.cellIndices() }
    }

    private fun LayoutNode.splits(): List<LayoutNode.Split> = when (this) {
        is LayoutNode.Cell -> emptyList()
        is LayoutNode.Split -> listOf(this) + children.flatMap { it.splits() }
    }
}
