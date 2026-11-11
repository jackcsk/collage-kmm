package com.jackcheung.collage.ui.adaptive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * One control in the app's bars. Each has an icon and a title so it works in every form: an icon
 * on the side rail (the title becomes its accessibility label), an action in the top bar, or a
 * labelled tool.
 *
 * Screens rebuild their items on every recomposition (the editor does so on every gesture frame),
 * so this is an `@Immutable data class`: equal items let the bar composables skip.
 */
@Immutable
data class BarItem(
    val icon: Painter,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    /** Non-null for toggles (tools) and tabs: whether it is on or the current tab. */
    val selected: Boolean? = null,
    val iconRotation: Float = 0f,
)

/** Width of a side rail (Material navigation rail), shared so every screen's rail lines up. */
val RailWidth = 80.dp
private val PrimaryActionEndPadding = 8.dp

/** Height of an extended floating action button (56dp) plus its 16dp margin. */
private val FabClearance = 72.dp

/**
 * A screen with bars that follow [WindowLayout.barEdge]. In tall windows the bars are horizontal:
 * a top app bar ([navigation], [actions], [primaryAction]), a bottom toolbar ([tools]) and a
 * floating action button ([floatingAction]); the screen shows [tabs] above the content they switch,
 * with [AdaptiveTabRow]. When the bars move to the side they gather into one rail, laid out as on
 * iPhone Duo: top-bar items at the top with navigation first; bottom-bar items at the bottom in
 * groups (tools, then tabs when the system owns the strip), with the floating action last.
 *
 * [content] gets the padding that keeps it clear of the bars and the platform's safe areas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveScaffold(
    title: String,
    modifier: Modifier = Modifier,
    centerTitle: Boolean = false,
    navigation: BarItem? = null,
    primaryAction: BarItem? = null,
    floatingAction: BarItem? = null,
    actions: List<BarItem> = emptyList(),
    tools: List<BarItem> = emptyList(),
    tabs: List<BarItem> = emptyList(),
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val windowLayout = LocalWindowLayout.current
    val edge = windowLayout.barEdge
    // iPhone Duo: the rail goes in the system's strip, beside the content's safe area rather than taking from it.
    val systemBar = windowLayout.systemBar
    val safe = WindowInsets.safeDrawing
    // Lazy padding: the insets are read at layout, so inset changes don't recompose the scaffold.
    val contentPadding = when {
        edge == null -> safe.only(
            if (tools.isEmpty()) WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom else WindowInsetsSides.Horizontal,
        ).asPaddingValues()
        systemBar != null -> safe.asPaddingValues()
        else -> safe.only(
            WindowInsetsSides.Vertical + if (edge == BarEdge.Start) WindowInsetsSides.End else WindowInsetsSides.Start,
        ).asPaddingValues()
    }

    // Like Material's Scaffold: paint the theme background and set its content colour, so nothing
    // shows the window's own background (light, even in dark theme) through gaps in the content.
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScaffoldLayout(
            edge = edge,
            systemBar = systemBar,
            topBar = {
                if (edge == null) {
                    val barTitle: @Composable () -> Unit = { Text(title) }
                    val navigationIcon: @Composable () -> Unit = { navigation?.let { BarIconButton(it) } }
                    val barActions: @Composable RowScope.() -> Unit = {
                        actions.forEach { BarIconButton(it) }
                        primaryAction?.let { action ->
                            Button(
                                onClick = action.onClick,
                                enabled = action.enabled,
                                modifier = Modifier.padding(end = PrimaryActionEndPadding),
                            ) { Text(action.label) }
                        }
                    }
                    val barInsets = safe.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    FadeInOnEnter(Modifier.animateWindowBounds()) {
                        if (centerTitle) {
                            CenterAlignedTopAppBar(barTitle, navigationIcon = navigationIcon, actions = barActions, windowInsets = barInsets)
                        } else {
                            TopAppBar(barTitle, navigationIcon = navigationIcon, actions = barActions, windowInsets = barInsets)
                        }
                    }
                }
            },
            rail = {
                if (edge != null) {
                    FadeInOnEnter(Modifier.animateWindowBounds()) {
                        // Tabs join the rail only in the system's strip (iPhone Duo); elsewhere they stay in the content.
                        SideRail(edge, systemBar, navigation, primaryAction, floatingAction, actions, tools, if (systemBar != null) tabs else emptyList())
                    }
                }
            },
            bottomBar = {
                if (edge == null && tools.isNotEmpty()) {
                    FadeInOnEnter(Modifier.animateWindowBounds()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                                .padding(bottom = 12.dp),
                        ) { tools.forEach { ToolItem(it) } }
                    }
                }
            },
            body = {
                Box(Modifier.animateWindowBounds()) {
                    content(contentPadding)
                    if (edge == null && floatingAction != null) {
                        ExtendedFloatingActionButton(
                            onClick = floatingAction.onClick,
                            icon = { Icon(floatingAction.icon, contentDescription = null) },
                            text = { Text(floatingAction.label) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(contentPadding).padding(16.dp),
                        )
                    }
                    // As Material's Scaffold does, keep snackbars above the floating button rather than over it.
                    val fabClearance = if (edge == null && floatingAction != null) FabClearance else 0.dp
                    Box(Modifier.align(Alignment.BottomCenter).padding(contentPadding).padding(bottom = fabClearance)) { snackbarHost() }
                }
            },
        )
    }
}

/**
 * Places the scaffold's parts: [body] below [topBar] and above [bottomBar], beside [rail] when the
 * bars are on the side, or with [rail] in [systemBar]'s strip and [body] filling the window.
 */
@Composable
private fun ScaffoldLayout(
    edge: BarEdge?,
    systemBar: SystemVerticalBar?,
    topBar: @Composable () -> Unit,
    rail: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxSize(),
        contents = listOf(topBar, rail, bottomBar, body),
    ) { (top, side, bottom, main), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val railAtStart = edge == BarEdge.Start
        if (systemBar != null) {
            // The strip lies within the safe-area inset that the content already avoids.
            val stripTop = systemBar.top.roundToPx()
            val stripHeight = (height - stripTop - systemBar.bottom.roundToPx()).coerceAtLeast(0)
            val stripWidth = systemBar.width.roundToPx()
            val sideRail = side.firstOrNull()?.measure(Constraints.fixed(stripWidth, stripHeight))
            val content = main.first().measure(Constraints.fixed(width, height))
            val inset = systemBar.edgeInset.roundToPx()
            return@Layout layout(width, height) {
                content.placeRelative(0, 0)
                sideRail?.placeRelative(if (railAtStart) inset else width - inset - stripWidth, stripTop)
            }
        }
        val topBar = top.firstOrNull()?.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
        val bottomBar = bottom.firstOrNull()?.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
        val topHeight = topBar?.height ?: 0
        val bottomHeight = bottomBar?.height ?: 0
        val sideRail = side.firstOrNull()?.measure(Constraints(maxWidth = width, minHeight = height, maxHeight = height))
        val railWidth = sideRail?.width ?: 0
        val bodyWidth = (width - railWidth).coerceAtLeast(0)
        val bodyHeight = (height - topHeight - bottomHeight).coerceAtLeast(0)
        val content = main.first().measure(Constraints.fixed(bodyWidth, bodyHeight))
        layout(width, height) {
            content.placeRelative(if (railAtStart) railWidth else 0, topHeight)
            topBar?.placeRelative(0, 0)
            bottomBar?.placeRelative(0, height - bottomHeight)
            sideRail?.placeRelative(if (railAtStart) 0 else width - railWidth, 0)
        }
    }
}

/**
 * [tabs] as a tab row above the content they switch, unless they are in iPhone Duo's vertical bar.
 */
@Composable
fun AdaptiveTabRow(tabs: List<BarItem>) {
    if (LocalWindowLayout.current.systemBar != null) return
    PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.selected == true }.coerceAtLeast(0)) {
        tabs.forEach { tab ->
            Tab(selected = tab.selected == true, onClick = tab.onClick, text = { Text(tab.label) })
        }
    }
}

@Composable
private fun SideRail(
    edge: BarEdge,
    systemBar: SystemVerticalBar?,
    navigation: BarItem?,
    primaryAction: BarItem?,
    floatingAction: BarItem?,
    actions: List<BarItem>,
    tools: List<BarItem>,
    tabs: List<BarItem>,
) {
    // In the system's strip the parent already sized and placed the rail; otherwise it pads itself.
    val placement = if (systemBar != null) {
        Modifier.fillMaxSize()
    } else {
        val side = if (edge == BarEdge.End) WindowInsetsSides.End else WindowInsetsSides.Start
        Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + side)).width(RailWidth)
    }
    // verticalScroll keeps the minimum height, so the weighted spacer still splits top and bottom.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // Scrolls only if a very short window can't fit every control.
        modifier = placement.verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
    ) {
        // Top, as on iPhone Duo: what the top bar holds, with navigation first.
        navigation?.let { RailGroup(listOf(it)) }
        primaryAction?.let { ProminentRailButton(it) }
        if (actions.isNotEmpty()) RailGroup(actions)
        Spacer(Modifier.weight(1f))
        // Bottom: what the bottom bars hold, each set grouped, with the floating action last.
        if (tools.isNotEmpty()) RailGroup(tools)
        if (tabs.isNotEmpty()) RailGroup(tabs, highlightSelected = true)
        floatingAction?.let { ProminentRailButton(it) }
    }
}

/** Related rail controls in one capsule, like iPhone Duo's grouped bar items. */
@Composable
private fun RailGroup(items: List<BarItem>, highlightSelected: Boolean = false) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            items.forEach { item ->
                if (highlightSelected) {
                    IconToggleButton(
                        checked = item.selected == true,
                        onCheckedChange = { item.onClick() },
                        enabled = item.enabled,
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            checkedContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Icon(item.icon, contentDescription = item.label) }
                } else {
                    BarIconButton(item)
                }
            }
        }
    }
}

/** An icon-only control, as on iPhone Duo's vertical bars; the title is its accessibility label. */
@Composable
private fun BarIconButton(item: BarItem) {
    val icon: @Composable () -> Unit = {
        Icon(item.icon, contentDescription = item.label, modifier = Modifier.rotate(item.iconRotation))
    }
    when (val selected = item.selected) {
        null -> IconButton(onClick = item.onClick, enabled = item.enabled, content = icon)
        else -> IconToggleButton(checked = selected, onCheckedChange = { item.onClick() }, enabled = item.enabled, content = icon)
    }
}

@Composable
private fun ProminentRailButton(item: BarItem) {
    FilledIconButton(onClick = item.onClick, enabled = item.enabled, modifier = Modifier.size(48.dp)) {
        Icon(item.icon, contentDescription = item.label, modifier = Modifier.rotate(item.iconRotation))
    }
}

/** An icon over its title, for the bottom toolbar. */
@Composable
private fun ToolItem(item: BarItem) {
    val color = when {
        !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        item.selected == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val interaction = when (val selected = item.selected) {
        null -> Modifier.clickable(enabled = item.enabled, role = Role.Button, onClick = item.onClick)
        else -> Modifier.toggleable(value = selected, enabled = item.enabled, role = Role.Switch) { item.onClick() }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = interaction.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.rotate(item.iconRotation))
        Spacer(Modifier.height(4.dp))
        Text(item.label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Fades [content] in when it first appears, so bars don't pop in as the layout changes. */
@Composable
private fun FadeInOnEnter(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = state, enter = fadeIn(), modifier = modifier) { content() }
}
