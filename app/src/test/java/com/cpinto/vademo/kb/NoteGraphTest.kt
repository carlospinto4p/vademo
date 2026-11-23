package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NoteGraphTest {

    @TempDir
    lateinit var root: File

    private fun note(path: String, body: String, title: String? = null) {
        val file = File(root, path)
        file.parentFile!!.mkdirs()
        val front = title?.let { "---\ntitle: $it\n---\n" }.orEmpty()
        file.writeText(front + body)
    }

    private fun graph() = NoteGraph.of(NoteIndex.of(KnowledgeBase(root)))

    private fun NoteGraph.linkNames() = links.map { (a, b) -> notes[a].name to notes[b].name }.toSet()

    @Test
    fun `notes are the index and every note in a folder, not root repository files`() {
        note("index.md", "[[ideas]]", title = "Home")
        note("README.md", "[[ideas]]")
        note("backlog.md", "")
        note("ideas/ideas.md", "")
        note("ideas/deep/nested.md", "")
        note("templates/idea.md", "[[ideas]]")
        val notes = graph().notes
        assertEquals(listOf("ideas/deep/nested.md", "ideas/ideas.md", "index.md"), notes.map { it.path })
        assertEquals(listOf("ideas", "ideas", ""), notes.map { it.folder })
        assertEquals("Home", notes.single { it.name == "index" }.title)
    }

    @Test
    fun `a link both ways is one line`() {
        note("ideas/a.md", "[[b]]")
        note("ideas/b.md", "[[a]] and again [[a|A]]")
        assertEquals(setOf("a" to "b"), graph().linkNames())
    }

    @Test
    fun `links to missing notes and to the note itself are left out`() {
        note("ideas/a.md", "[[a]] [[nowhere]] [[b.md]]")
        note("ideas/b.md", "")
        assertEquals(setOf("a" to "b"), graph().linkNames())
    }

    @Test
    fun `links in frontmatter and in code do not count`() {
        note("ideas/a.md", "---\nrelated: [[b]]\n---\nUse `[[b]]` to link.")
        note("ideas/b.md", "")
        assertEquals(emptySet<Pair<String, String>>(), graph().linkNames())
    }

    @Test
    fun `around keeps the notes up to two links away and the links among them`() {
        // a - b - c - d, and e on its own.
        note("ideas/a.md", "[[b]]")
        note("ideas/b.md", "[[c]]")
        note("ideas/c.md", "[[d]]")
        note("ideas/d.md", "")
        note("ideas/e.md", "")
        val around = graph().around("ideas/a.md")
        assertEquals(listOf("a", "b", "c"), around.notes.map { it.name })
        assertEquals(setOf("a" to "b", "b" to "c"), around.linkNames())
        assertEquals(listOf("a", "b"), graph().around("ideas/a.md", steps = 1).notes.map { it.name })
        assertEquals(listOf("e"), graph().around("ideas/e.md").notes.map { it.name })
        assertEquals(emptyList<GraphNote>(), graph().around("ideas/none.md").notes)
    }
}
