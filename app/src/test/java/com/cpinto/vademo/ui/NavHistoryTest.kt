package com.cpinto.vademo.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavHistoryTest {

    private val history = NavHistory()
    private var nextId = 0

    /** Opens [route] as a new screen, as the navigation library would, and returns its id. */
    private fun open(route: String, place: Boolean = true): String =
        "e${nextId++}".also { history.onTop(it, route, place) }

    @Test
    fun `nothing to go forward to at first`() {
        open("home", place = false)
        open("note/a")
        assertFalse(history.canGoForward)
        assertNull(history.takeForward())
    }

    @Test
    fun `going back offers the screen just left`() {
        open("home", place = false)
        val a = open("note/a")
        open("note/b")
        history.onTop(a, "note/a", true)
        assertTrue(history.canGoForward)
        assertEquals("note/b", history.takeForward())
    }

    @Test
    fun `forward steps through several screens in the order they were left`() {
        val home = open("home", place = false)
        val a = open("diagram/d")
        open("note/b")
        open("note/c")
        history.onTop(a, "diagram/d", true) // back twice at once
        assertEquals("note/b", history.takeForward().also { open(it!!) })
        assertEquals("note/c", history.takeForward().also { open(it!!) })
        assertFalse(history.canGoForward)
        history.onTop(home, "home", false)
        assertTrue(history.canGoForward)
    }

    @Test
    fun `going forward keeps the rest of the way forward`() {
        val a = open("note/a")
        val b = open("note/b")
        open("note/c")
        history.onTop(b, "note/b", true)
        history.onTop(a, "note/a", true)
        open(history.takeForward()!!)
        assertEquals("note/c", history.takeForward())
    }

    @Test
    fun `opening something new forgets the way forward`() {
        val a = open("note/a")
        open("note/b")
        history.onTop(a, "note/a", true)
        open("note/x")
        assertFalse(history.canGoForward)
    }

    @Test
    fun `going back from the editor does not offer to reopen it`() {
        val a = open("note/a")
        open("edit/a", place = false)
        history.onTop(a, "note/a", true)
        assertFalse(history.canGoForward)
    }

    @Test
    fun `goForward opens the next route`() {
        val opened = mutableListOf<String>()
        history.open = { opened += it }
        val a = open("note/a")
        open("note/b")
        history.onTop(a, "note/a", true)
        history.goForward()
        assertEquals(listOf("note/b"), opened)
        history.goForward()
        assertEquals(listOf("note/b"), opened)
    }
}
