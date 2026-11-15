package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate

class NoteTemplateTest {

    private val today = LocalDate.of(2026, 11, 15)

    private val template = """
        ---
        title: <The question, as a question>
        type: question
        status: draft
        tags: []
        created: YYYY-MM-DD
        updated: YYYY-MM-DD
        review-by: YYYY-MM-DD
        ---

        # <The question>

        Hub: [[<topic-hub>]]

        ## Why it matters

        <What decision this answer feeds.>

        ## Answer

        _Not answered yet._
    """.trimIndent()

    @Test
    fun `fills the title, dates, heading and hub`() {
        val note = NoteTemplate.fill(template, "Is Raft simpler than Paxos?", hub = "consensus", date = today)
        val front = FrontmatterParser.parse(note)
        assertEquals("Is Raft simpler than Paxos?", front.frontmatter?.title)
        assertEquals("2026-11-15", front.frontmatter?.created)
        assertEquals("2026-11-15", front.frontmatter?.updated)
        assertEquals("question", front.frontmatter?.type)
        assertTrue(note.contains("\n# Is Raft simpler than Paxos?\n"), note)
        assertTrue(note.contains("Hub: [[consensus]]"), note)
    }

    @Test
    fun `leaves every other placeholder for the writer`() {
        val note = NoteTemplate.fill(template, "A question", hub = "consensus", date = today)
        assertTrue(note.contains("review-by: YYYY-MM-DD"), note)
        assertTrue(note.contains("<What decision this answer feeds.>"), note)
        assertTrue(note.contains("_Not answered yet._"), note)
    }

    @Test
    fun `keeps the hub placeholder when the folder has no hub note`() {
        val note = NoteTemplate.fill(template, "A question", hub = null, date = today)
        assertTrue(note.contains("Hub: [[<topic-hub>]]"), note)
    }

    @Test
    fun `a hub named in the template stays as it is`() {
        val fixed = template.replace("[[<topic-hub>]]", "[[backtests]]")
        assertTrue(NoteTemplate.fill(fixed, "A run", hub = "ideas", date = today).contains("Hub: [[backtests]]"))
    }

    @Test
    fun `a heading inside the body is not taken for the note's heading`() {
        val note = NoteTemplate.fill(template, "A question", hub = null, date = today)
        assertTrue(note.contains("## Why it matters"), note)
        assertTrue(note.contains("## Answer"), note)
    }

    @Test
    fun `titles YAML would misread are quoted and read back whole`() {
        for (title in listOf("Raft: the basics", "#1 cause of outages", "Exactly once, or not")) {
            val note = NoteTemplate.fill(template, title, hub = null, date = today)
            assertEquals(title, FrontmatterParser.parse(note).frontmatter?.title, note)
        }
        val quoted = NoteTemplate.fill(template, "Raft: the basics", hub = null, date = today)
        assertTrue(quoted.contains("title: \"Raft: the basics\""), quoted)
    }

    @Test
    fun `a template with no frontmatter only gets its heading`() {
        assertEquals("# New\n\nText", NoteTemplate.fill("# <Title>\n\nText", "New", hub = null, date = today))
    }

    @Test
    fun `file names are kebab-case without accents`() {
        assertEquals("is-raft-simpler-than-paxos", NoteTemplate.fileName("Is Raft simpler than Paxos?"))
        assertEquals("cafe-con-leche-2026", NoteTemplate.fileName("  Café con leche, 2026! "))
        assertEquals("", NoteTemplate.fileName("¿?"))
    }

    @Test
    fun `only kebab-case names are accepted`() {
        assertTrue(NoteTemplate.isValidName("src-raft-paper"))
        assertFalse(NoteTemplate.isValidName("Raft"))
        assertFalse(NoteTemplate.isValidName("raft--paper"))
        assertFalse(NoteTemplate.isValidName("-raft"))
        assertFalse(NoteTemplate.isValidName(""))
    }

    @Test
    fun `lists templates and finds a folder's hub`(@TempDir root: File) {
        File(root, "templates").mkdirs()
        File(root, "templates/source.md").writeText("x")
        File(root, "templates/note.md").writeText("x")
        File(root, "consensus").mkdirs()
        File(root, "consensus/consensus.md").writeText("x")
        File(root, "inbox").mkdirs()
        val kb = KnowledgeBase(root)
        assertEquals(listOf("note.md", "source.md"), kb.templates().map { it.name })
        assertEquals("consensus", kb.hubOf(File(root, "consensus")))
        assertEquals(null, kb.hubOf(File(root, "inbox")))
    }

    @Test
    fun `a knowledge base without templates lists none`(@TempDir root: File) {
        assertEquals(emptyList<File>(), KnowledgeBase(root).templates())
    }
}
