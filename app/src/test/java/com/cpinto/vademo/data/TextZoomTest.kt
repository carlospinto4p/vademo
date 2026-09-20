package com.cpinto.vademo.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextZoomTest {

    @Test
    fun `a pinch multiplies the current scale`() {
        assertEquals(1.5f, TextZoom.zoomed(1f, 1.5f))
        assertEquals(1.5f, TextZoom.zoomed(0.75f, 2f))
    }

    @Test
    fun `zooming stops at the limits instead of running away`() {
        assertEquals(TextZoom.MAX, TextZoom.zoomed(TextZoom.MAX, 1.5f))
        assertEquals(TextZoom.MIN, TextZoom.zoomed(TextZoom.MIN, 0.5f))
        assertEquals(TextZoom.MAX, TextZoom.zoomed(1f, 100f))
        assertEquals(TextZoom.MIN, TextZoom.zoomed(1f, 0.001f))
    }

    @Test
    fun `a stored scale from an older version is brought inside the limits`() {
        assertEquals(TextZoom.MAX, TextZoom.clamp(9f))
        assertEquals(TextZoom.MIN, TextZoom.clamp(0.1f))
        assertEquals(TextZoom.DEFAULT, TextZoom.clamp(Float.NaN))
    }

    @Test
    fun `a nonsense pinch leaves the scale alone`() {
        assertEquals(1.2f, TextZoom.zoomed(1.2f, 0f))
        assertEquals(1.2f, TextZoom.zoomed(1.2f, Float.NaN))
    }
}
