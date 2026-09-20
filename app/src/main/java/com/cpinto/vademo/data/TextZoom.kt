package com.cpinto.vademo.data

/**
 * How much bigger or smaller a note's text is drawn than the phone's own
 * font size, set by pinching a note and remembered across notes.
 *
 * It multiplies the font scale rather than the density, so the text reflows
 * to the width of the screen instead of running off it: zooming in makes a
 * line hold fewer words, never makes the reader pan sideways to finish one.
 */
object TextZoom {

    const val DEFAULT = 1f

    /**
     * Below this the frontmatter chips stop being legible; above it a
     * heading no longer fits a phone's width.
     */
    const val MIN = 0.7f
    const val MAX = 2.5f

    fun clamp(scale: Float): Float = when {
        scale.isNaN() -> DEFAULT
        else -> scale.coerceIn(MIN, MAX)
    }

    /** The scale after a pinch of [factor] (>1 fingers apart, <1 together). */
    fun zoomed(scale: Float, factor: Float): Float =
        if (factor.isNaN() || factor <= 0f) clamp(scale) else clamp(scale * factor)
}
