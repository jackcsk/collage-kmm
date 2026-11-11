package com.jackcheung.collage.ui.adaptive

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Dp
import com.jackcheung.collage.platform.systemVerticalBar

/** Which side of the window holds the bars when they run along the vertical axis. */
enum class BarEdge { Start, End }

/**
 * Where the system wants app controls on the vertical axis (iPhone Duo): a strip of [width] along
 * [edge], [edgeInset] in from that edge and between [top] and [bottom] from the window's top and
 * bottom. It shares the side with the system's vertical status bar, which is part of the safe area,
 * so the app's content still stays inside the safe area and the strip sits within it.
 */
@Immutable
data class SystemVerticalBar(val edge: BarEdge, val top: Dp, val bottom: Dp, val edgeInset: Dp, val width: Dp)

/**
 * How screens arrange themselves in the current window. [AdaptiveRoot] decides this once, from the
 * window's size and the platform's bar placement, so every screen follows the same rules.
 */
@Immutable
data class WindowLayout(
    /** The window is wider than it is tall: panes go side by side instead of stacking. */
    val wide: Boolean,
    /**
     * Where navigation, actions and tools go when they run vertically along a side, or null
     * for the usual horizontal bars at the top and bottom. Wide windows put them on the side to
     * keep vertical space for content, as iPhone Duo does; [systemBar] can also ask for it.
     */
    val barEdge: BarEdge?,
    /** The system's own vertical bar strip, when the platform has one (iPhone Duo). */
    val systemBar: SystemVerticalBar? = null,
)

val LocalWindowLayout = compositionLocalOf { WindowLayout(wide = false, barEdge = null) }

private val LocalWindowLookahead = compositionLocalOf<LookaheadScope?> { null }

/** Hosts the app: measures the window, decides [WindowLayout], and anchors [animateWindowBounds]. */
@Composable
fun AdaptiveRoot(content: @Composable () -> Unit) {
    val systemBar = systemVerticalBar()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val layout = WindowLayout(wide = wide, barEdge = systemBar?.edge ?: if (wide) BarEdge.End else null, systemBar = systemBar)
        LookaheadScope {
            CompositionLocalProvider(LocalWindowLayout provides layout, LocalWindowLookahead provides this) {
                content()
            }
        }
    }
}

private val WindowBoundsTransform = BoundsTransform { _: Rect, _: Rect ->
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Glides this element to its new position and size when the window changes shape (fold, unfold,
 * rotation) or the arrangement switches, instead of jumping there.
 */
@Composable
fun Modifier.animateWindowBounds(): Modifier {
    val lookahead = LocalWindowLookahead.current ?: return this
    return animateBounds(lookahead, boundsTransform = WindowBoundsTransform)
}
