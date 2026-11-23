package com.cpinto.vademo.ui.theme

import com.cpinto.vademo.kb.Diagram
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contrast of the colours vademo picks itself, light and dark, against the
 * Material tones the theme puts behind and on them whatever the wallpaper.
 */
class PaletteTest {

    private val themes = listOf(false, true)
    /** Every hue, not only the legend's eight: the map spreads its folders round the whole wheel. */
    private val hues = (0 until 360 step 5).map { it.toFloat() }

    private fun ratio(colour: Int, behind: Double) = Palette.contrast(Palette.luminance(colour), behind)

    private fun name(dark: Boolean, hue: Float) = "${if (dark) "dark" else "light"} theme, hue $hue"

    @Test
    fun `text on a legend tint is readable`() {
        for (dark in themes) for (hue in hues) {
            val tint = Palette.tint(hue, dark)
            // The dimmest text drawn on a box, its headline in onSurfaceVariant.
            val ink = Palette.toneLuminance(Palette.inkTone(dark))
            val r = Palette.contrast(Palette.luminance(tint), ink)
            assertTrue(r >= Palette.TEXT, "${name(dark, hue)}: $r")
        }
    }

    @Test
    fun `a stripe stands out from its box`() {
        for (dark in themes) for (hue in hues) {
            val r = ratio(Palette.stripe(hue, dark), Palette.luminance(Palette.tint(hue, dark)))
            assertTrue(r >= Palette.GRAPHIC, "${name(dark, hue)}: $r")
        }
    }

    @Test
    fun `status borders stand out from every tint and surface`() {
        for (dark in themes) {
            val behind = Diagram.LEGEND_HUES.map { Palette.luminance(Palette.tint(it, dark)) } + Palette.surfaces(dark)
            for ((hue, s, l) in listOf(Triple(135f, 0.6f, 0.35f), Triple(40f, 0.9f, 0.45f))) {
                val colour = Palette.status(hue, s, l, dark, Diagram.LEGEND_HUES)
                for (b in behind) assertTrue(ratio(colour, b) >= Palette.GRAPHIC, "${name(dark, hue)}: ${ratio(colour, b)}")
            }
        }
    }

    @Test
    fun `a folder's colour stands out from every surface a map box can have`() {
        for (dark in themes) for (hue in hues) {
            val colour = Palette.folder(hue, dark)
            for (b in Palette.surfaces(dark)) assertTrue(ratio(colour, b) >= Palette.GRAPHIC, "${name(dark, hue)}: ${ratio(colour, b)}")
        }
    }

    @Test
    fun `a colour that already stands out keeps its lightness`() {
        // A deep blue on white needs no darkening.
        assertEquals(Palette.hsl(225f, 0.55f, 0.45f), Palette.readable(225f, 0.55f, 0.45f, listOf(1.0), dark = false))
    }

    @Test
    fun `hsl and luminance agree with known colours`() {
        assertEquals(0xFFFF0000.toInt(), Palette.hsl(0f, 1f, 0.5f))
        assertEquals(0xFF000000.toInt(), Palette.hsl(200f, 0.5f, 0f))
        assertEquals(0xFFFFFFFF.toInt(), Palette.hsl(200f, 0.5f, 1f))
        assertEquals(1.0, Palette.luminance(0xFFFFFFFF.toInt()), 1e-9)
        assertEquals(21.0, Palette.contrast(1.0, 0.0), 1e-9)
        // Tone 50 is mid-grey: L* 50 is a luminance of about 0.18.
        assertEquals(0.184, Palette.toneLuminance(50.0), 1e-3)
    }
}
