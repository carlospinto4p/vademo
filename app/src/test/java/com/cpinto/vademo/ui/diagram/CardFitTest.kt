package com.cpinto.vademo.ui.diagram

import com.cpinto.vademo.ui.graph.StartAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardFitTest {

    // A phone's note column, and the card's height cap, in dp.
    private val card = 360f
    private val max = 360f

    @Test
    fun `a small diagram is drawn at full size, centred across, as tall as itself`() {
        val fit = CardFit.of(200f, 100f, card, max, StartAt.TopLeft)
        assertEquals(1f, fit.framing.scale)
        assertEquals(80f, fit.framing.x)
        assertEquals(0f, fit.framing.y, 1e-3f)
        assertEquals(100f, fit.height)
        assertFalse(fit.cut)
    }

    @Test
    fun `a diagram that shrinks to a readable size is shown whole`() {
        // 480 wide fits at 0.75, above the card's readable scale.
        val fit = CardFit.of(480f, 200f, card, max, StartAt.TopLeft)
        assertEquals(0.75f, fit.framing.scale, 1e-4f)
        assertEquals(150f, fit.height, 1e-3f)
        assertFalse(fit.cut)
    }

    @Test
    fun `a wide diagram stops shrinking at the readable scale and is cut on the right`() {
        // Fitting 1600 into 360 would take 0.225.
        val fit = CardFit.of(1600f, 300f, card, max, StartAt.TopLeft)
        assertEquals(CARD_READABLE_SCALE, fit.framing.scale)
        assertEquals(0f, fit.framing.x, 1e-3f)
        assertEquals(0f, fit.framing.y, 1e-3f)
        assertEquals(300f * CARD_READABLE_SCALE, fit.height, 1e-3f)
        assertTrue(fit.cutRight)
        assertFalse(fit.cutLeft)
        assertFalse(fit.cutBottom)
        assertTrue(fit.cut)
    }

    @Test
    fun `a tall top-to-bottom diagram shows its top, capped, cut at the bottom`() {
        val fit = CardFit.of(300f, 2000f, card, max, StartAt.Top)
        assertEquals(CARD_READABLE_SCALE, fit.framing.scale)
        assertEquals(max, fit.height)
        assertEquals(0f, fit.framing.y, 1e-3f)
        assertTrue(fit.cutBottom)
        assertFalse(fit.cutLeft || fit.cutRight)
    }

    @Test
    fun `a wide top-to-bottom diagram shows its middle, cut on both sides`() {
        val fit = CardFit.of(1600f, 300f, card, max, StartAt.Top)
        assertTrue(fit.cutLeft)
        assertTrue(fit.cutRight)
    }
}
