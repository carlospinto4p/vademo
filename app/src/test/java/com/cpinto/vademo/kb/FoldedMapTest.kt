package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FoldedMapTest {

    private fun note(path: String, title: String = path, type: String? = null) = GraphNote(
        name = path.substringAfterLast('/').removeSuffix(".md"),
        title = title,
        folder = if ('/' in path) path.substringBefore('/') else "",
        path = path,
        type = type,
    )

    private val notes = listOf(
        note("index.md", "Home"),
        note("ideas/ideas.md", "Ideas"),
        note("ideas/tyfold.md", "Tyfold"),
        note("ideas/scorecard.md", "Scorecard"),
        note("markets/saas.md", "SaaS"),
        note("sources/src-report.md", "A report", type = "source"),
        note("markets/src-blog.md", "A blog post", type = "source"),
    )
    private fun at(path: String) = notes.indexOfFirst { it.path == path }
    private fun link(a: String, b: String) = minOf(at(a), at(b)) to maxOf(at(a), at(b))

    private val graph = NoteGraph(
        notes,
        listOf(
            link("index.md", "ideas/ideas.md"),
            link("ideas/ideas.md", "ideas/tyfold.md"),
            link("ideas/tyfold.md", "markets/saas.md"),
            link("ideas/scorecard.md", "markets/saas.md"),
            link("ideas/tyfold.md", "sources/src-report.md"),
        ),
    )

    private fun FoldedMap.titleLinks() = links.map { (a, b) -> boxes[a].title to boxes[b].title }.toSet()

    @Test
    fun `folded, the map is the index and one box per topic, sources left out`() {
        val map = FoldedMap.of(graph, open = emptySet(), sources = false)
        assertEquals(listOf("Home", "Ideas", "markets"), map.boxes.map { it.title })
        val ideas = map.boxes[1]
        assertTrue(ideas.folded && ideas.large)
        assertEquals(3, ideas.notes)
        // A folded topic opens its hub; one with no hub opens nothing, it only unfolds.
        assertEquals("ideas/ideas.md", ideas.path)
        assertNull(map.boxes[2].path)
        assertEquals(1, map.boxes[2].notes)
        // Two links between the same topics are one line; links inside a topic are none.
        assertEquals(setOf("Home" to "Ideas", "Ideas" to "markets"), map.titleLinks())
    }

    @Test
    fun `an open topic is drawn note by note, its hub large`() {
        val map = FoldedMap.of(graph, open = setOf("ideas"), sources = false)
        assertEquals(listOf("Home", "Ideas", "Tyfold", "Scorecard", "markets"), map.boxes.map { it.title })
        assertTrue(map.boxes.none { it.folder == "ideas" && it.folded })
        assertEquals(listOf(true, true, false, false, true), map.boxes.map { it.large })
        assertEquals(
            setOf("Home" to "Ideas", "Ideas" to "Tyfold", "Tyfold" to "markets", "Scorecard" to "markets"),
            map.titleLinks(),
        )
    }

    @Test
    fun `with sources shown they count and link like any note`() {
        val map = FoldedMap.of(graph, open = emptySet(), sources = true)
        assertEquals(listOf("Home", "Ideas", "markets", "sources"), map.boxes.map { it.title })
        assertEquals(2, map.boxes[2].notes)
        assertTrue("Ideas" to "sources" in map.titleLinks())
    }

    @Test
    fun `a source is a note in sources or of type source`() {
        assertTrue(note("sources/x.md").isSource)
        assertTrue(note("markets/x.md", type = "source").isSource)
        assertFalse(note("markets/x.md", type = "concept").isSource)
    }

    /** A hub and [count] notes in `lore`, each linked to the hub, and the last two to each other too. */
    private fun lore(count: Int): NoteGraph {
        val notes = listOf(note("index.md", "Home"), note("lore/lore.md", "Lore")) +
            (0 until count).map { note("lore/n$it.md", "n$it") }
        val links = listOf(0 to 1) + (0 until count).map { 1 to it + 2 } + (count to count + 1)
        return NoteGraph(notes, links)
    }

    @Test
    fun `an open topic draws its hub and its most linked notes, the rest in one box`() {
        val map = FoldedMap.of(lore(5), open = setOf("lore"), sources = false, limits = mapOf("lore" to 2))
        // n3 and n4 are the most linked; they keep their order and the rest follow in one box.
        assertEquals(listOf("Home", "Lore", "n3", "n4", "3 more"), map.boxes.map { it.title })
        val rest = map.boxes.last()
        assertTrue(rest.rest && !rest.folded && !rest.large)
        assertEquals(3, rest.notes)
        assertNull(rest.path)
        // Links to the notes left over go to their box.
        assertEquals(setOf("Home" to "Lore", "Lore" to "n3", "Lore" to "n4", "n3" to "n4", "Lore" to "3 more"), map.titleLinks())
    }

    @Test
    fun `a topic draws up to the default limit, and all of it below that`() {
        assertEquals(FoldedMap.LIMIT + 3, FoldedMap.of(lore(FoldedMap.LIMIT + 5), setOf("lore"), sources = false).boxes.size)
        assertTrue(FoldedMap.of(lore(FoldedMap.LIMIT), setOf("lore"), sources = false).boxes.none { it.rest })
    }

    @Test
    fun `the note to keep is drawn whatever its links`() {
        val map = FoldedMap.of(lore(5), open = setOf("lore"), sources = false, limits = mapOf("lore" to 2), keep = "lore/n0.md")
        assertEquals(listOf("Home", "Lore", "n0", "n3", "3 more"), map.boxes.map { it.title })
    }

    @Test
    fun `only the selected box's links are drawn besides the shape`() {
        val map = FoldedMap.of(graph, open = setOf("ideas"), sources = false)
        fun drawn(selected: Int) = map.drawn(selected).map { (a, b) -> map.boxes[a].title to map.boxes[b].title }.toSet()
        val tyfold = map.boxes.indexOfFirst { it.title == "Tyfold" }
        // The shape: the index and topics among themselves, and each note with its own topic's hub.
        assertEquals(setOf("Home" to "Ideas", "Ideas" to "Tyfold"), drawn(-1))
        assertEquals(setOf("Home" to "Ideas", "Ideas" to "Tyfold", "Tyfold" to "markets"), drawn(tyfold))
        assertEquals(setOf("Ideas", "markets"), map.neighbours(tyfold).map { map.boxes[it].title }.toSet())
    }
}
