package com.cpinto.vademo.ui.note

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Reports two-finger pinches as zoom factors, leaving every other gesture
 * alone.
 *
 * `detectTransformGestures` would be shorter but claims one-finger drags as
 * pans, which is how the note is scrolled — so this waits for a second
 * finger and only then consumes anything. Put it *after* `verticalScroll` in
 * the chain: the inner modifier sees each event first, so a pinch is taken
 * before the scroll can read it as a drag.
 *
 * [onZoomEnd] fires once when the fingers lift, and only if the gesture
 * actually zoomed — that is the moment to persist the new scale, rather than
 * writing every intermediate value of the pinch.
 */
fun Modifier.pinchTextZoom(
    onZoom: (factor: Float) -> Unit,
    onZoomEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoomed = false
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val factor = event.calculateZoom()
                if (factor != 1f) {
                    onZoom(factor)
                    zoomed = true
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
        if (zoomed) onZoomEnd()
    }
}
