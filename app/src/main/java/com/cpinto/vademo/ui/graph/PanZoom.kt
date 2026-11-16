package com.cpinto.vademo.ui.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val MIN_START_SCALE = 0.35f
private const val MIN_SCALE = 0.15f
private const val MAX_SCALE = 3f

/**
 * Lets [content], [contentWidth] by [contentHeight], be dragged and pinched.
 * It starts scaled to fit the screen, but never smaller than a scale where
 * titles can no longer be read. Used by the map of notes and by diagrams.
 */
@Composable
fun PanZoom(
    contentWidth: Dp,
    contentHeight: Dp,
    /** Applied to the content at its own size, inside the zoom, as for recording a picture of all of it. */
    contentModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size ->
                if (fitted || size.width == 0) return@onSizeChanged
                val w = with(density) { contentWidth.toPx() }
                val h = with(density) { contentHeight.toPx() }
                scale = minOf(size.width / w, size.height / h, 1f).coerceAtLeast(MIN_START_SCALE)
                offset = Offset(((size.width - w * scale) / 2).coerceAtLeast(0f), ((size.height - h * scale) / 2).coerceAtLeast(0f))
                fitted = true
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    // Keep the point under the fingers still while zooming.
                    offset = centroid - (centroid - offset) * (newScale / scale) + pan
                    scale = newScale
                }
            },
    ) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(contentWidth, contentHeight)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = scale
                    scaleY = scale
                }
                .then(contentModifier),
        ) { content() }
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
