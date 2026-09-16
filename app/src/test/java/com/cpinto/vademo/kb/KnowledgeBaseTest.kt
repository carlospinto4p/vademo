package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KnowledgeBaseTest {

    @TempDir
    lateinit var root: File

    private fun note(path: String, title: String? = null) {
        val file = File(root, path)
        file.parentFile!!.mkdirs()
        file.writeText(if (title == null) "# no frontmatter" else "---\ntitle: $title\ntype: question\nstatus: draft\n---\nbody")
    }

    @Test
    fun `folders skip hidden directories and templates`() {
        note("ideas/ideas.md", "Ideas")
        note("markets/markets.md", "Markets")
        note("templates/idea.md", "Template")
        note(".claude/rules/x.md", "Rule")
        val kb = KnowledgeBase(root)
        assertEquals(listOf("ideas", "markets"), kb.folders().map { it.name })
    }

    @Test
    fun `notes list the hub first then by title`() {
        note("ideas/ideas.md", "Ideas")
        note("ideas/zebra.md", "Another idea")
        note("ideas/apple.md", "Zoo idea")
        val names = KnowledgeBase(root).notesIn(File(root, "ideas")).map { it.name }
        assertEquals(listOf("ideas", "zebra", "apple"), names)
    }

    @Test
    fun `a note without a title falls back to its file name`() {
        note("ideas/untitled-thing.md")
        assertEquals("untitled-thing", KnowledgeBase(root).notesIn(File(root, "ideas")).single().title)
    }

    @Test
    fun `resolve finds a note in any folder`() {
        note("sources/src-report.md", "Report")
        assertEquals(File(root, "sources/src-report.md"), KnowledgeBase(root).resolve("src-report"))
    }

    @Test
    fun `resolve ignores templates`() {
        note("templates/idea.md", "Template")
        assertNull(KnowledgeBase(root).resolve("idea"))
    }

    @Test
    fun `relative paths use forward slashes`() {
        note("ideas/tyfold.md", "Tyfold")
        val kb = KnowledgeBase(root)
        assertEquals("ideas/tyfold.md", kb.relativePath(File(root, "ideas/tyfold.md")))
    }
}
