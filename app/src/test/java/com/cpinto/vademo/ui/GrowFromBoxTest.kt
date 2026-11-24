package com.cpinto.vademo.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrowFromBoxTest {

    private val screen = Bounds(0f, 0f, 1000f, 2000f)

    /** A map box 140 by 52, drawn at half size, somewhere down the screen. */
    private val box = Bounds(300f, 900f, 70f, 26f)

    @Test
    fun `at the start the screen is drawn over the box`() {
        val frame = GrowFrom.frame(box, screen, 0f, boxCorner = 10f)
        assertEquals(0.07f, frame.scale, 1e-6f)
        assertEquals(300f, frame.x)
        assertEquals(900f, frame.y)
        // Cut to the box's height, in the screen's own unscaled pixels: 26 / 0.07.
        assertEquals(26f / 0.07f, frame.shownHeight, 1e-3f)
        assertEquals(26f, frame.shownHeight * frame.scale, 1e-3f)
        // Corners as round as the box's once drawn shrunk.
        assertEquals(10f, frame.corner * frame.scale, 1e-4f)
        assertEquals(0f, frame.alpha)
    }

    @Test
    fun `at the end it is the whole screen`() {
        val frame = GrowFrom.frame(box, screen, 1f, boxCorner = 10f)
        assertEquals(1f, frame.scale)
        assertEquals(0f, frame.x)
        assertEquals(0f, frame.y)
        assertEquals(2000f, frame.shownHeight)
        assertEquals(0f, frame.corner)
        assertEquals(1f, frame.alpha)
    }

    @Test
    fun `half way it is between the two and already opaque`() {
        val frame = GrowFrom.frame(box, screen, 0.5f, boxCorner = 10f)
        assertEquals(535f / 1000f, frame.scale, 1e-6f)
        assertEquals(150f, frame.x)
        assertEquals(450f, frame.y)
        assertEquals(1013f, frame.shownHeight * frame.scale, 1e-2f)
        assertEquals(1f, frame.alpha)
    }

    @Test
    fun `the note fades in over the first part of the way`() {
        assertEquals(0.5f, GrowFrom.frame(box, screen, GrowFrom.FADE / 2, 10f).alpha, 1e-6f)
        assertEquals(1f, GrowFrom.frame(box, screen, GrowFrom.FADE, 10f).alpha, 1e-6f)
    }

    @Test
    fun `positions are taken relative to a screen not at the window's corner`() {
        val below = Bounds(0f, 100f, 1000f, 1900f)
        val frame = GrowFrom.frame(box, below, 0f, 10f)
        assertEquals(300f, frame.x)
        assertEquals(800f, frame.y)
    }

    @Test
    fun `a box taller than the screen never shows more than it`() {
        val wide = Bounds(0f, 0f, 1000f, 3000f)
        assertEquals(2000f, GrowFrom.frame(wide, screen, 0f, 10f).shownHeight)
    }

    @Test
    fun `a box almost out of sight is not grown from`() {
        assertTrue(GrowFrom.usable(box))
        assertFalse(GrowFrom.usable(Bounds(0f, 0f, 70f, 1f)))
        assertFalse(GrowFrom.usable(Bounds(0f, 0f, 0f, 0f)))
    }

    @Test
    fun `a box tapped binds to the note that opens next`() {
        val openings = NoteOpenings()
        openings.from(box)
        assertEquals(box, openings.bind("note", isNote = true))
        // Asked again, as the host does for the screen going out, it is still that note's.
        assertEquals(box, openings.bind("note", isNote = true))
        assertEquals(box, openings.of("note"))
    }

    @Test
    fun `a box tapped is forgotten when no note opens`() {
        val openings = NoteOpenings()
        openings.from(box)
        assertNull(openings.bind("folder", isNote = false))
        assertNull(openings.bind("note", isNote = true))
        assertNull(openings.of("folder"))
    }

    @Test
    fun `a note opened without a box slides as before`() {
        val openings = NoteOpenings()
        assertNull(openings.bind("note", isNote = true))
        assertNull(openings.of("note"))
    }

    @Test
    fun `a note gone for good forgets its box`() {
        val openings = NoteOpenings()
        openings.from(box)
        openings.bind("note", isNote = true)
        openings.forget("note")
        assertNull(openings.of("note"))
    }
}
