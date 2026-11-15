package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NoteIndexTest {

    @TempDir
    lateinit var root: File

    private fun write(path: String, text: String) {
        File(root, path).apply { parentFile!!.mkdirs() }.writeText(text)
    }

    private fun index() = NoteIndex.of(KnowledgeBase(root))

    @Test
    fun `reads each note's frontmatter, headings, links and unverified markers`() {
        write(
            "consensus/raft.md",
            """
            ---
            title: Raft
            type: algorithm
            status: draft
            tags: [consensus, "leader-election"]
            created: 2026-11-06
            updated: 2026-11-08
            ---

            # Raft

            Elects a leader [[quorum]] (unverified). See [[paxos|Paxos]] and [[quorum.md]].

            ## Log replication ##

            ```
            # not a heading [[nope]] (unverified)
            ```
            """.trimIndent(),
        )
        val note = index().notes.single()
        assertEquals("consensus/raft.md", note.path)
        assertEquals("raft", note.name)
        assertEquals("Raft", note.title)
        assertEquals("algorithm", note.type)
        assertEquals("draft", note.status)
        assertEquals(listOf("consensus", "leader-election"), note.tags)
        assertEquals("2026-11-06", note.created)
        assertEquals("2026-11-08", note.updated)
        assertEquals(listOf("Raft", "Log replication"), note.headings)
        assertEquals(listOf("quorum", "paxos", "quorum"), note.links)
        // The marker inside the code block is counted too: it is text in the note.
        assertEquals(2, note.unverified)
    }

    @Test
    fun `a note without frontmatter is titled by its file name`() {
        write("ideas/loose-thought.md", "Just text.")
        val note = index().notes.single()
        assertEquals("loose-thought", note.title)
        assertEquals(null, note.type)
        assertEquals(emptyList<String>(), note.tags)
    }

    @Test
    fun `notes are the index and folder notes, not root files or templates`() {
        write("index.md", "")
        write("README.md", "")
        write("CLAUDE.md", "")
        write("ideas/a.md", "")
        write("templates/concept.md", "")
        write(".claude/rules/x.md", "")
        assertEquals(listOf("ideas/a.md", "index.md"), index().notes.map { it.path })
    }

    @Test
    fun `backlinks list each linking note once and skip links to itself`() {
        write("ideas/a.md", "[[b]] and [[b|again]] and [[a]]")
        write("ideas/b.md", "[[a]]")
        write("ideas/c.md", "[[b]]")
        val backlinks = index().backlinks
        assertEquals(listOf("a", "c"), backlinks["b"])
        assertEquals(listOf("b"), backlinks["a"])
        assertEquals(null, backlinks["c"])
    }

    @Test
    fun `linkedFrom gives the notes linking to a note, by its path`() {
        write("ideas/a.md", "---\ntitle: Note A\n---\n[[b]]")
        write("ideas/b.md", "")
        write("sources/c.md", "---\ntitle: Note C\n---\n[[b]]")
        assertEquals(listOf("Note A", "Note C"), index().linkedFrom("ideas/b.md").map { it.title })
        assertEquals(emptyList<IndexedNote>(), index().linkedFrom("ideas/a.md"))
    }

    @Test
    fun `links that name no note are unresolved, once per note`() {
        write("ideas/a.md", "[[gone]] [[gone]] [[b]]")
        write("ideas/b.md", "[[also-gone]]")
        assertEquals(
            listOf(UnresolvedLink("a", "gone"), UnresolvedLink("b", "also-gone")),
            index().unresolved,
        )
    }

    @Test
    fun `orphans are notes nothing links to, except the index and hubs`() {
        write("index.md", "")
        write("ideas/ideas.md", "[[linked]]")
        write("ideas/linked.md", "")
        write("ideas/alone.md", "[[linked]]")
        write("ideas/self.md", "[[self]]")
        write("sources/overview.md", "---\ntype: hub\n---\n")
        assertEquals(listOf("alone", "self"), index().orphans.map { it.name })
    }

    private fun front(title: String, type: String? = null, updated: String? = null, tags: String = "[]") =
        "---\ntitle: $title\n" + (type?.let { "type: $it\n" } ?: "") +
            (updated?.let { "updated: $it\n" } ?: "") + "tags: $tags\n---\n"

    @Test
    fun `recent puts the newest updated first and undated notes last`() {
        write("ideas/a.md", front("Alpha", updated = "2026-09-01"))
        write("ideas/b.md", front("Beta", updated = "2026-11-08"))
        write("ideas/c.md", front("Gamma"))
        write("ideas/d.md", front("Delta", updated = "2026-10-15"))
        assertEquals(listOf("Beta", "Delta", "Alpha", "Gamma"), index().recent().map { it.title })
    }

    @Test
    fun `byType groups by type, largest first, untyped last`() {
        write("sources/s1.md", front("Source one", "source"))
        write("sources/s2.md", front("Source two", "source"))
        write("ideas/q.md", front("Why", "question"))
        write("ideas/c.md", front("Quorum", "concept"))
        write("ideas/loose.md", "No frontmatter.")
        assertEquals(
            listOf(
                "source" to listOf("Source one", "Source two"),
                "concept" to listOf("Quorum"),
                "question" to listOf("Why"),
                null to listOf("loose"),
            ),
            index().byType().map { (type, notes) -> type to notes.map { it.title } },
        )
    }

    @Test
    fun `tagCounts counts each note once per tag, most used first`() {
        write("ideas/a.md", front("A", tags = "[raft, consensus, raft]"))
        write("ideas/b.md", front("B", tags = "[consensus]"))
        write("ideas/c.md", front("C", tags = "[paxos]"))
        assertEquals(
            listOf("consensus" to 2, "paxos" to 1, "raft" to 1),
            index().tagCounts(),
        )
    }

    @Test
    fun `finds a note by name, with or without md`() {
        write("ideas/a.md", "---\ntitle: A note\n---\n")
        assertEquals("A note", index().note("a")?.title)
        assertEquals("A note", index().note("a.md")?.title)
        assertEquals(null, index().note("b"))
    }
}
