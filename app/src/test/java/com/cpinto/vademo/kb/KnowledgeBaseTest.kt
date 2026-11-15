package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

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
    fun `noteAt takes a path from the root or a bare note name`() {
        note("consensus/raft.md", "Raft")
        val kb = KnowledgeBase(root)
        val raft = File(root, "consensus/raft.md")
        assertEquals(raft, kb.noteAt("consensus/raft.md"))
        assertEquals(raft, kb.noteAt("./consensus/raft.md"))
        assertEquals(raft, kb.noteAt("raft.md"))
        assertEquals(raft, kb.noteAt("raft"))
    }

    @Test
    fun `noteAt looks a bare name up in the notes it is given, and only a bare name`() {
        note("consensus/raft.md", "Raft")
        val kb = KnowledgeBase(root)
        val raft = File(root, "consensus/raft.md")
        var walks = 0
        val notes = { walks++; mapOf("paxos" to raft) }
        assertEquals(raft, kb.noteAt("paxos", notes))
        assertEquals(raft, kb.noteAt("paxos.md", notes))
        assertEquals(raft, kb.noteAt("consensus/raft.md", notes))
        assertNull(kb.noteAt("other/paxos.md", notes))
        assertEquals(2, walks)
    }

    @Test
    fun `noteAt names nothing for other files, missing notes or paths outside`() {
        note("consensus/raft.md", "Raft")
        File(root, "scripts").mkdirs()
        File(root, "scripts/run.py").writeText("")
        val kb = KnowledgeBase(root)
        assertNull(kb.noteAt("scripts/run.py"))
        assertNull(kb.noteAt("other/raft.md"))
        assertNull(kb.noteAt("../outside.md"))
    }

    @Test
    fun `diagramFile is a json file in the diagrams folder`() {
        assertEquals(File(root, ".tyfold-graphs/price-to-model.json"), KnowledgeBase(root).diagramFile("price-to-model"))
    }

    @Test
    fun `diagramFile refuses names that would leave the diagrams folder`() {
        val kb = KnowledgeBase(root)
        for (name in listOf("../secret", "../../files/settings", "a/b", "..\\x", "", " ")) {
            assertNull(kb.diagramFile(name), name)
        }
    }

    /** A folder outside the knowledge base, standing in for the app's private files. */
    @TempDir
    lateinit var elsewhere: File

    private fun outside(): File = elsewhere.apply {
        File(this, "secret.md").writeText("---\ntitle: Secret\n---\ntoken")
        File(this, "secret.json").writeText("""{"nodes":[]}""")
    }

    private fun link(path: String, target: File) {
        File(root, path).parentFile!!.mkdirs()
        Files.createSymbolicLink(File(root, path).toPath(), target.toPath())
    }

    @Test
    fun `a note linked to a file outside is never listed or found`() {
        note("ideas/ideas.md", "Ideas")
        link("ideas/secret.md", File(outside(), "secret.md"))
        val kb = KnowledgeBase(root)
        assertEquals(listOf("ideas"), kb.notesIn(File(root, "ideas")).map { it.name })
        assertEquals(1, kb.foldersIn(root).single().noteCount)
        assertNull(kb.resolve("secret"))
        assertNull(kb.noteAt("ideas/secret.md"))
        assertThrows<IllegalArgumentException> { kb.fileAt("ideas/secret.md") }
    }

    @Test
    fun `a folder linked to one outside is never listed or walked`() {
        note("ideas/ideas.md", "Ideas")
        link("elsewhere", outside())
        val kb = KnowledgeBase(root)
        assertEquals(listOf("ideas"), kb.foldersIn(root).map { it.name })
        assertNull(kb.resolve("secret"))
    }

    @Test
    fun `an index linked to a file outside is not the index`() {
        link("index.md", File(outside(), "secret.md"))
        assertNull(KnowledgeBase(root).index)
    }

    @Test
    fun `a diagram linked to a file outside is not opened`() {
        link(".tyfold-graphs/secret.json", File(outside(), "secret.json"))
        assertNull(KnowledgeBase(root).diagramFile("secret"))
        assertEquals(emptyList<DiagramFile>(), diagramsIn(root))
    }

    @Test
    fun `fileAt refuses a path out of the knowledge base`() {
        assertThrows<IllegalArgumentException> { KnowledgeBase(root).fileAt("../outside.md") }
    }

    @Test
    fun `a link that stays inside the knowledge base still works`() {
        note("ideas/ideas.md", "Ideas")
        link("ideas/alias.md", File(root, "ideas/ideas.md"))
        assertEquals(listOf("ideas", "alias"), KnowledgeBase(root).notesIn(File(root, "ideas")).map { it.name })
    }

    @Test
    fun `relative paths use forward slashes`() {
        note("ideas/tyfold.md", "Tyfold")
        val kb = KnowledgeBase(root)
        assertEquals("ideas/tyfold.md", kb.relativePath(File(root, "ideas/tyfold.md")))
    }
}
