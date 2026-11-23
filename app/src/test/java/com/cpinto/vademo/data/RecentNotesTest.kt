package com.cpinto.vademo.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecentNotesTest {

    @Test
    fun `the note just opened comes first, once`() {
        assertEquals(listOf("b.md", "a.md", "c.md"), RecentNotes.opened(listOf("a.md", "b.md", "c.md"), "b.md"))
        assertEquals(listOf("d.md", "a.md"), RecentNotes.opened(listOf("a.md"), "d.md"))
    }

    @Test
    fun `only the newest are kept`() {
        val full = (1..RecentNotes.KEEP).map { "n$it.md" }
        val after = RecentNotes.opened(full, "new.md")
        assertEquals(RecentNotes.KEEP, after.size)
        assertEquals("new.md", after.first())
        assertEquals(full.dropLast(1), after.drop(1))
    }

    @Test
    fun `stored as one path per line, nothing stored read as none`() {
        val paths = listOf("ideas/a.md", "index.md")
        assertEquals(paths, RecentNotes.decode(RecentNotes.encode(paths)))
        assertEquals(emptyList<String>(), RecentNotes.decode(null))
        assertEquals(emptyList<String>(), RecentNotes.decode(""))
    }
}
