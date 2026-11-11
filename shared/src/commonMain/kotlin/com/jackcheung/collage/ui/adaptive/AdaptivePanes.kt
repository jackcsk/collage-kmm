package com.jackcheung.collage.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val PRIMARY_FRACTION = 0.4f
private val PrimaryMinWidth = 240.dp
private val PrimaryMaxWidth = 420.dp

/**
 * Two panes, like an iPhone Duo split arrangement: in a wide window they sit side by side with
 * [primary] on the leading side; otherwise [primary] takes the height it needs on top and
 * [secondary] fills the rest. Both keep their state and glide between the arrangements.
 */
@Composable
fun AdaptivePanes(primary: @Composable () -> Unit, secondary: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val wide = LocalWindowLayout.current.wide
    Layout(
        modifier = modifier,
        contents = listOf(
            { Box(Modifier.animateWindowBounds(), propagateMinConstraints = true) { primary() } },
            { if (wide) VerticalDivider(Modifier.animateWindowBounds()) else HorizontalDivider(Modifier.animateWindowBounds()) },
            { Box(Modifier.animateWindowBounds(), propagateMinConstraints = true) { secondary() } },
        ),
    ) { (first, divider, second), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (wide) {
            val primaryWidth = (width * PRIMARY_FRACTION).roundToInt()
                .coerceIn(PrimaryMinWidth.roundToPx(), PrimaryMaxWidth.roundToPx())
                .coerceAtMost(width / 2)
            val start = first.first().measure(Constraints.fixed(primaryWidth, height))
            val line = divider.first().measure(Constraints(maxWidth = width, minHeight = height, maxHeight = height))
            val end = second.first().measure(Constraints.fixed((width - primaryWidth - line.width).coerceAtLeast(0), height))
            layout(width, height) {
                start.placeRelative(0, 0)
                line.placeRelative(primaryWidth, 0)
                end.placeRelative(primaryWidth + line.width, 0)
            }
        } else {
            val top = first.first().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
            val line = divider.first().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
            val bottom = second.first().measure(Constraints.fixed(width, (height - top.height - line.height).coerceAtLeast(0)))
            layout(width, height) {
                top.placeRelative(0, 0)
                line.placeRelative(0, top.height)
                bottom.placeRelative(0, top.height + line.height)
            }
        }
    }
}
