package com.cpinto.vademo.ui.graph

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MIN_SCALE = 0.15f
private const val MAX_SCALE = 3f

/**
 * The smallest scale a drawing opens at: a diagram's smallest text (11 sp)
 * still shows at over 9 sp, its box names at 12, and the map's titles at 10.
 * Shrinking to fit a big drawing used to start it at 35 %, where those were
 * 4 to 5 sp and could not be read.
 */
internal const val READABLE_SCALE = 0.85f

private const val GLIDE_MS = 300

/**
 * Lets [content], [contentWidth] by [contentHeight], be dragged and pinched.
 * It opens whole when that leaves it readable, and otherwise at
 * [READABLE_SCALE] showing the part [start] names. Double tap zooms to full
 * size round the tap and back out to the whole drawing, and a button
 * switches between the whole drawing and a readable size. Used by the map
 * of notes and by diagrams.
 */
@Composable
internal fun PanZoom(
    contentWidth: Dp,
    contentHeight: Dp,
    start: StartAt = StartAt.Centre,
    /** Applied to the content at its own size, inside the zoom, as for recording a picture of all of it. */
    contentModifier: Modifier = Modifier,
    /** A point of the content, in its own dp, to open centred on instead of [start]. */
    focus: Pair<Dp, Dp>? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val width = with(density) { contentWidth.toPx() }
    val height = with(density) { contentHeight.toPx() }
    var framing by remember { mutableStateOf(Framing(1f, 0f, 0f)) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var glide by remember { mutableStateOf<Job?>(null) }

    val all = Viewport.all(width, height, screen.width.toFloat(), screen.height.toFloat(), MIN_SCALE)
    val focusPx = focus?.let { (fx, fy) -> with(density) { fx.toPx() to fy.toPx() } }
    val openingOn = { screenWidth: Float, screenHeight: Float ->
        if (focusPx == null) Viewport.opening(width, height, screenWidth, screenHeight, READABLE_SCALE, start)
        else Viewport.openingAt(width, height, screenWidth, screenHeight, READABLE_SCALE, focusPx.first, focusPx.second)
    }
    val opening = openingOn(screen.width.toFloat(), screen.height.toFloat())
    val glideTo = { target: Framing ->
        glide?.cancel()
        val from = framing
        glide = scope.launch {
            animate(0f, 1f, animationSpec = tween(GLIDE_MS)) { t, _ -> framing = Viewport.between(from, target, t) }
        }
    }
    // Read by the double tap, which is set up once and outlives the composition that made it.
    val currentAll by rememberUpdatedState(all)
    // Derived, so a drag recomposes nothing until the answer changes: the framing itself is read only when drawing.
    val showingAll by remember { derivedStateOf { Viewport.same(framing, currentAll) } }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size ->
                if (screen != IntSize.Zero || size.width == 0) {
                    screen = size
                    return@onSizeChanged
                }
                screen = size
                framing = openingOn(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { at -> glideTo(Viewport.doubleTap(framing, currentAll, at.x, at.y)) })
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    glide?.cancel()
                    val zoomed = Viewport.zoomAt(framing, (framing.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE), centroid.x, centroid.y)
                    framing = zoomed.copy(x = zoomed.x + pan.x, y = zoomed.y + pan.y)
                }
            },
    ) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(contentWidth, contentHeight)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = framing.x
                    translationY = framing.y
                    scaleX = framing.scale
                    scaleY = framing.scale
                }
                .then(contentModifier),
        ) { content() }
        // Only when the whole drawing is too small to read; otherwise it opens whole and there is nothing to switch.
        if (screen != IntSize.Zero && opening.scale > all.scale) {
            SmallFloatingActionButton(
                onClick = { glideTo(if (showingAll) opening else all) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(
                    if (showingAll) Icons.Filled.ZoomIn else Icons.Filled.FitScreen,
                    contentDescription = if (showingAll) "Zoom to read" else "Show all",
                )
            }
        }
    }
}

/**
 * What each colour means, as a dot and a label per entry. Tapping it folds it
 * to a "Legend" button and back, so it can be moved out of the way of what it
 * covers; [startOpen] is how it first appears.
 */
@Composable
fun Legend(entries: List<Pair<String, Color>>, modifier: Modifier, startOpen: Boolean = true) {
    var open by rememberSaveable { mutableStateOf(startOpen) }
    Surface(
        modifier.clickable(onClickLabel = if (open) "Hide legend" else "Show legend") { open = !open },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!open) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    entries.forEach { (_, colour) -> Box(Modifier.size(8.dp).background(colour, CircleShape)) }
                    Text("Legend", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 3.dp))
                }
                return@Column
            }
            entries.forEach { (label, colour) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(colour, CircleShape))
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
