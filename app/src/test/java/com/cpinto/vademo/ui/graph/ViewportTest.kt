package com.cpinto.vademo.ui.graph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewportTest {

    private val readable = 0.85f

    @Test
    fun `a drawing smaller than the screen opens at full size, centred`() {
        val f = Viewport.opening(400f, 200f, 1000f, 2000f, readable, StartAt.TopLeft)
        assertEquals(Framing(1f, 300f, 900f), f)
    }

    @Test
    fun `a drawing that fits at a readable size opens whole`() {
        // Fits at 0.9, which is still readable.
        val f = Viewport.opening(1000f, 500f, 900f, 2000f, readable, StartAt.TopLeft)
        assertEquals(0.9f, f.scale, 1e-4f)
        assertEquals(0f, f.x, 1e-3f)
    }

    @Test
    fun `a drawing too big to read whole opens at the readable scale, not shrunk to fit`() {
        // Fitting it would take 0.25: its 11 sp labels would show at under 3 sp.
        val f = Viewport.opening(4000f, 8000f, 1000f, 2000f, readable, StartAt.Centre)
        assertEquals(readable, f.scale)
    }

    @Test
    fun `a left-to-right diagram opens at its top left`() {
        val f = Viewport.opening(4000f, 8000f, 1000f, 2000f, readable, StartAt.TopLeft)
        assertEquals(0f, f.x)
        assertEquals(0f, f.y)
    }

    @Test
    fun `a top-to-bottom diagram opens at its top, centred across`() {
        val f = Viewport.opening(4000f, 8000f, 1000f, 2000f, readable, StartAt.Top)
        assertEquals((1000f - 4000f * readable) / 2, f.x, 1e-3f)
        assertEquals(0f, f.y)
    }

    @Test
    fun `a side that fits is centred whatever the start says`() {
        // Wide and short: too wide at the readable scale, but its height fits.
        val f = Viewport.opening(4000f, 400f, 1000f, 2000f, readable, StartAt.TopLeft)
        assertEquals(0f, f.x)
        assertEquals((2000f - 400f * readable) / 2, f.y, 1e-3f)
    }

    @Test
    fun `show all fits the whole drawing, down to the smallest scale`() {
        assertEquals(Framing(0.25f, 0f, 0f), Viewport.all(4000f, 8000f, 1000f, 2000f, minScale = 0.15f))
        assertEquals(0.15f, Viewport.all(40000f, 80000f, 1000f, 2000f, minScale = 0.15f).scale)
    }

    @Test
    fun `zooming keeps the point under the fingers still`() {
        val before = Framing(0.5f, 100f, 200f)
        val after = Viewport.zoomAt(before, 2f, 300f, 400f)
        // The drawing's point under (300, 400) before and after.
        val pointBefore = (300f - before.x) / before.scale to (400f - before.y) / before.scale
        val pointAfter = (300f - after.x) / after.scale to (400f - after.y) / after.scale
        assertEquals(pointBefore.first, pointAfter.first, 1e-3f)
        assertEquals(pointBefore.second, pointAfter.second, 1e-3f)
    }

    @Test
    fun `double tap zooms a shrunk drawing to full size round the tap, and back out from there`() {
        val all = Framing(0.25f, 0f, 0f)
        val zoomed = Viewport.doubleTap(all, all, 500f, 500f)
        assertEquals(1f, zoomed.scale)
        assertEquals(Viewport.zoomAt(all, 1f, 500f, 500f), zoomed)
        assertEquals(all, Viewport.doubleTap(zoomed, all, 10f, 10f))
    }

    @Test
    fun `gliding starts at one framing and ends at the other`() {
        val a = Framing(0.25f, 0f, 0f)
        val b = Framing(1f, -300f, -600f)
        assertEquals(a, Viewport.between(a, b, 0f))
        assertEquals(b, Viewport.between(a, b, 1f))
        assertEquals(Framing(0.625f, -150f, -300f), Viewport.between(a, b, 0.5f))
    }

    @Test
    fun `framings a pixel apart count as the same`() {
        assertTrue(Viewport.same(Framing(0.5f, 10f, 10f), Framing(0.5f, 10.4f, 9.7f)))
        assertFalse(Viewport.same(Framing(0.5f, 10f, 10f), Framing(0.6f, 10f, 10f)))
    }
}
