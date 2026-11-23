package com.cpinto.vademo.ui.graph

/** What part of a drawing the screen shows: the drawing scaled by [scale], then shifted by ([x], [y]) screen pixels. */
internal data class Framing(val scale: Float, val x: Float, val y: Float)

/**
 * Where a drawing first opens when it is too big to show whole at a
 * readable size, as biases from -1 (left, top) to 1 (right, bottom). A
 * diagram drawn left to right starts at its top left, one drawn top to
 * bottom at its top, and the map of notes in its middle.
 */
internal enum class StartAt(val x: Float, val y: Float) {
    Centre(0f, 0f), TopLeft(-1f, -1f), Top(0f, -1f)
}

/** The arithmetic of [PanZoom], apart from Compose so it can be tested on the JVM. Sizes are in pixels. */
internal object Viewport {

    /** The scale at which a [width] by [height] drawing fits a [screenWidth] by [screenHeight] screen, never above 1. */
    fun fitScale(width: Float, height: Float, screenWidth: Float, screenHeight: Float): Float =
        minOf(screenWidth / width, screenHeight / height, 1f)

    /** The whole drawing, centred, however small that makes it (down to [minScale]). */
    fun all(width: Float, height: Float, screenWidth: Float, screenHeight: Float, minScale: Float): Framing {
        val scale = fitScale(width, height, screenWidth, screenHeight).coerceAtLeast(minScale)
        return Framing(scale, place(width * scale, screenWidth, 0f), place(height * scale, screenHeight, 0f))
    }

    /**
     * How a drawing opens: whole when that leaves it at [readable] scale or
     * larger, and otherwise at [readable] scale showing the part [start]
     * names. A side that fits is centred whatever [start] says.
     */
    fun opening(width: Float, height: Float, screenWidth: Float, screenHeight: Float, readable: Float, start: StartAt): Framing {
        val scale = maxOf(fitScale(width, height, screenWidth, screenHeight), readable).coerceAtMost(1f)
        return Framing(scale, place(width * scale, screenWidth, start.x), place(height * scale, screenHeight, start.y))
    }

    /**
     * As [opening], but showing the drawing's point ([pointX], [pointY]) in
     * the middle of the screen as near as the drawing's edges allow, as for a
     * map opened round one note.
     */
    fun openingAt(width: Float, height: Float, screenWidth: Float, screenHeight: Float, readable: Float, pointX: Float, pointY: Float): Framing {
        val scale = maxOf(fitScale(width, height, screenWidth, screenHeight), readable).coerceAtMost(1f)
        return Framing(scale, around(width * scale, screenWidth, pointX * scale), around(height * scale, screenHeight, pointY * scale))
    }

    /** Where a side [size] long starts so that [point] along it is mid-[screen]; centred when it fits, never past an edge. */
    private fun around(size: Float, screen: Float, point: Float): Float =
        if (size <= screen) (screen - size) / 2 else (screen / 2 - point).coerceIn(screen - size, 0f)

    /** [framing] scaled to [scale], with the drawing's point under ([focusX], [focusY]) staying there. */
    fun zoomAt(framing: Framing, scale: Float, focusX: Float, focusY: Float): Framing {
        val ratio = scale / framing.scale
        return Framing(scale, focusX - (focusX - framing.x) * ratio, focusY - (focusY - framing.y) * ratio)
    }

    /**
     * What a double tap at ([x], [y]) shows: the drawing at its full size
     * round that point when it is shown smaller, and otherwise [all].
     */
    fun doubleTap(framing: Framing, all: Framing, x: Float, y: Float): Framing =
        if (framing.scale < FULL_SIZE) zoomAt(framing, 1f, x, y) else all

    /** The framing [t] of the way from [from] to [to], for gliding between them. */
    fun between(from: Framing, to: Framing, t: Float): Framing =
        Framing(lerp(from.scale, to.scale, t), lerp(from.x, to.x, t), lerp(from.y, to.y, t))

    /** Whether [a] and [b] show the same thing, near enough that a person could not tell. */
    fun same(a: Framing, b: Framing): Boolean =
        kotlin.math.abs(a.scale - b.scale) < 0.01f && kotlin.math.abs(a.x - b.x) < 1f && kotlin.math.abs(a.y - b.y) < 1f

    /** Where a side [size] long starts on a [screen] long one: centred when it fits, else by [bias]. */
    private fun place(size: Float, screen: Float, bias: Float): Float =
        if (size <= screen) (screen - size) / 2 else (screen - size) * (bias + 1) / 2

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Just under 1, so a drawing a rounding error short of full size counts as full size. */
    private const val FULL_SIZE = 0.99f
}
