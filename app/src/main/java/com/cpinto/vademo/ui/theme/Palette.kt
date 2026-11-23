package com.cpinto.vademo.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The colours vademo picks itself rather than taking from the theme: a
 * diagram's legend tints and stripes, its status borders, and the map's
 * folder colours. Kept as ARGB and apart from Compose, so their contrast is
 * tested on the JVM (`PaletteTest`).
 *
 * The theme's own colours change with the wallpaper (Material You), but by
 * tone, not by hue: a light surface is always about tone 90 to 98, a dark
 * one 6 to 22, and text tone 10 to 30 or 80 to 90. So each colour here is
 * checked against those tones, and darkened (light theme) or lightened
 * (dark theme) a step at a time until it stands out: 3:1, the WCAG figure
 * for lines and borders. A yellow stripe at the same lightness as a blue
 * one faded into its box; now each hue gets the lightness it needs.
 */
object Palette {

    /** WCAG's contrast for borders, stripes and other parts of a picture that are not text. */
    const val GRAPHIC = 3.0

    /** WCAG's contrast for normal-sized text. */
    const val TEXT = 4.5

    /** Material 3 surface tones a picture's colours sit on: surface, surfaceContainerHigh, surfaceContainerHighest. */
    val LIGHT_SURFACES = listOf(98.0, 92.0, 90.0)
    val DARK_SURFACES = listOf(6.0, 17.0, 22.0)

    /** The tone of the theme's dimmest text, onSurfaceVariant: 30 on light, 80 on dark. */
    fun inkTone(dark: Boolean) = if (dark) 80.0 else 30.0

    /** A legend entry's box fill, a wash of its hue. */
    fun tint(hue: Float, dark: Boolean): Int = hsl(hue, if (dark) 0.3f else 0.6f, if (dark) 0.2f else 0.94f)

    /** A legend entry's stripe down the side of its box, standing out from that box's [tint]. */
    fun stripe(hue: Float, dark: Boolean): Int =
        readable(hue, 0.55f, if (dark) 0.6f else 0.45f, listOf(luminance(tint(hue, dark))), dark)

    /**
     * A status border (added, changed), around a box of any legend tint or
     * none, on the diagram's surface.
     */
    fun status(hue: Float, saturation: Float, lightness: Float, dark: Boolean, legendHues: FloatArray): Int {
        val behind = legendHues.map { luminance(tint(it, dark)) } + surfaces(dark)
        return readable(hue, saturation, lightness, behind, dark)
    }

    /** A folder's colour on the map: the border of its notes' boxes, on any of the theme's surfaces. */
    fun folder(hue: Float, dark: Boolean): Int = readable(hue, 0.55f, if (dark) 0.6f else 0.45f, surfaces(dark), dark)

    fun surfaces(dark: Boolean): List<Double> = (if (dark) DARK_SURFACES else LIGHT_SURFACES).map(::toneLuminance)

    /**
     * hsl([hue], [saturation], [lightness]), darkened on a light theme or
     * lightened on a dark one in steps of 0.01 until it has [GRAPHIC]
     * contrast with every luminance in [behind].
     */
    fun readable(hue: Float, saturation: Float, lightness: Float, behind: List<Double>, dark: Boolean): Int {
        var l = lightness
        while (true) {
            val colour = hsl(hue, saturation, l)
            val lum = luminance(colour)
            val worst = behind.minOf { contrast(lum, it) }
            if (worst >= GRAPHIC || l <= 0f || l >= 1f) return colour
            l = if (dark) min(1f, l + 0.01f) else max(0f, l - 0.01f)
        }
    }

    /** Opaque ARGB for hue 0–360, saturation and lightness 0–1. */
    fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1 - kotlin.math.abs(2 * lightness - 1)) * saturation
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val x = c * (1 - kotlin.math.abs(h % 2 - 1))
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = lightness - c / 2
        fun channel(v: Float) = ((v + m).coerceIn(0f, 1f) * 255 + 0.5f).toInt()
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** WCAG relative luminance of an ARGB colour. */
    fun luminance(argb: Int): Double {
        fun linear(channel: Int): Double {
            val v = channel / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) + 0.0722 * linear(argb and 0xFF)
    }

    /** The relative luminance of a Material tone, which is CIE L*. */
    fun toneLuminance(tone: Double): Double {
        val fy = (tone + 16) / 116
        return if (fy > 6.0 / 29) fy * fy * fy else (fy - 16.0 / 116) * 3 * (6.0 / 29) * (6.0 / 29)
    }

    /** WCAG contrast ratio between two luminances, 1 to 21. */
    fun contrast(a: Double, b: Double): Double = (max(a, b) + 0.05) / (min(a, b) + 0.05)
}
