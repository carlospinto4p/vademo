package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FrontmatterTest {

    private val note = """
        ---
        title: Tyfold — a paid desktop app
        type: idea
        status: draft         # draft | reviewed | outdated
        tags: [ideas, software, "developer-tools"]
        created: 2026-09-15
        updated: 2026-09-15
        ---

        # Tyfold

        Body text.
    """.trimIndent()

    @Test
    fun `reads the fields the knowledge bases use`() {
        val front = FrontmatterParser.parse(note).frontmatter!!
        assertEquals("Tyfold — a paid desktop app", front.title)
        assertEquals("idea", front.type)
        assertEquals("draft", front.status)
        assertEquals(listOf("ideas", "software", "developer-tools"), front.tags)
        assertEquals("2026-09-15", front.updated)
    }

    @Test
    fun `body starts after the closing fence`() {
        assertEquals("# Tyfold\n\nBody text.", FrontmatterParser.parse(note).body)
    }

    @Test
    fun `a note without frontmatter is all body`() {
        val parsed = FrontmatterParser.parse("# Just a heading\n")
        assertNull(parsed.frontmatter)
        assertEquals("# Just a heading\n", parsed.body)
    }

    @Test
    fun `an unclosed fence is not treated as frontmatter`() {
        assertNull(FrontmatterParser.parse("---\ntitle: x\n\nbody").frontmatter)
    }

    @Test
    fun `a hash without leading space stays in the value`() {
        val front = FrontmatterParser.parse("---\ntitle: C#-first\n---\n").frontmatter!!
        assertEquals("C#-first", front.title)
    }

    @Test
    fun `withUpdated replaces only the updated date`() {
        val result = FrontmatterParser.withUpdated(note, LocalDate.of(2026, 9, 16))
        assertEquals(note.replace("updated: 2026-09-15", "updated: 2026-09-16"), result)
    }

    @Test
    fun `withUpdated leaves a note without the field unchanged`() {
        val text = "---\ntitle: x\n---\nbody"
        assertEquals(text, FrontmatterParser.withUpdated(text, LocalDate.of(2026, 9, 16)))
    }

    @Test
    fun `withUpdated ignores an updated line in the body`() {
        val text = "---\ntitle: x\n---\nupdated: yesterday"
        assertEquals(text, FrontmatterParser.withUpdated(text, LocalDate.of(2026, 9, 16)))
    }
}
