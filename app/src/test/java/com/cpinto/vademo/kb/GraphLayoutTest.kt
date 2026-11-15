package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot

class GraphLayoutTest {

    private val boxWidth = 0.85
    private val boxHeight = 0.35

    /**
     * Shaped like a real knowledge base: an index linking to folder hubs,
     * each hub linking to its notes, some notes linking across folders and a
     * few notes nothing links to.
     */
    private fun knowledgeBase(): Triple<Int, List<Pair<Int, Int>>, List<String>> {
        val groups = mutableListOf("")
        val links = mutableListOf<Pair<Int, Int>>()
        val folders = listOf("concepts", "papers", "systems", "questions", "sources")
        for ((f, folder) in folders.withIndex()) {
            val hub = groups.size
            groups += folder
            links += 0 to hub
            repeat(8 + f * 3) { n ->
                val note = groups.size
                groups += folder
                if (n % 5 != 4) links += hub to note // every fifth note is an orphan
                if (n % 3 == 0 && note > 7) links += (note - 7) to note
            }
        }
        return Triple(groups.size, links.distinct(), groups)
    }

    private fun layout(count: Int, links: List<Pair<Int, Int>>, groups: List<String>) =
        GraphLayout.layout(count, links, groups, boxWidth, boxHeight)

    @Test
    fun `no two boxes overlap and all sit inside the bounds`() {
        val (count, links, groups) = knowledgeBase()
        val result = layout(count, links, groups)
        val c = result.centres
        for (i in c.indices) {
            assertTrue(c[i].x - boxWidth / 2 >= -1e-9 && c[i].x + boxWidth / 2 <= result.width + 1e-9)
            assertTrue(c[i].y - boxHeight / 2 >= -1e-9 && c[i].y + boxHeight / 2 <= result.height + 1e-9)
            for (j in i + 1 until c.size) {
                val apart = abs(c[i].x - c[j].x) >= boxWidth - 1e-9 || abs(c[i].y - c[j].y) >= boxHeight - 1e-9
                assertTrue(apart, "boxes $i and $j overlap")
            }
        }
    }

    @Test
    fun `linked notes end up closer than unlinked ones`() {
        val (count, links, groups) = knowledgeBase()
        val c = layout(count, links, groups).centres
        fun distance(a: Int, b: Int) = hypot(c[a].x - c[b].x, c[a].y - c[b].y)
        val linked = links.map { (a, b) -> distance(a, b) }.average()
        val linkSet = links.toSet()
        val unlinked = (0 until count).flatMap { a -> (a + 1 until count).map { a to it } }
            .filter { it !in linkSet }
            .map { (a, b) -> distance(a, b) }
            .average()
        assertTrue(linked < unlinked / 2, "linked $linked, unlinked $unlinked")
    }

    @Test
    fun `a tall screen gives a taller picture`() {
        val (count, links, groups) = knowledgeBase()
        val square = layout(count, links, groups)
        val tall = GraphLayout.layout(count, links, groups, boxWidth, boxHeight, aspect = 2.0)
        assertTrue(tall.height / tall.width > 1.3 * square.height / square.width)
    }

    @Test
    fun `the same graph always gives the same picture`() {
        val (count, links, groups) = knowledgeBase()
        assertEquals(layout(count, links, groups), layout(count, links, groups))
    }

    @Test
    fun `an empty graph and a single note are handled`() {
        assertEquals(Layout(emptyList(), 0.0, 0.0), layout(0, emptyList(), emptyList()))
        val one = layout(1, emptyList(), listOf(""))
        assertEquals(Point(boxWidth / 2, boxHeight / 2), one.centres.single())
    }
}
