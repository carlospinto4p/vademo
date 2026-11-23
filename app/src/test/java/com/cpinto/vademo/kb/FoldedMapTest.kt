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
}
