package com.jackcheung.collage.editor

import com.jackcheung.collage.layout.LayoutNode
import com.jackcheung.collage.layout.PhotoTransform
import com.jackcheung.collage.platform.PhotoRef

/** A photo placed in one slot of the layout. */
data class CollageCell(val photo: PhotoRef, val transform: PhotoTransform = PhotoTransform())

/**
 * The collage being edited. [cells] is indexed by the layout's cell numbers. [edited] records
 * whether leaving would lose work.
 */
data class EditorState(
    val layout: LayoutNode,
    val cells: List<CollageCell>,
    val border: Boolean = false,
    val selected: Int? = null,
    val edited: Boolean = false,
)

sealed interface SaveState {
    data object Idle : SaveState

    data object Saving : SaveState

    data class Saved(val photo: PhotoRef) : SaveState

    data object Failed : SaveState
}
