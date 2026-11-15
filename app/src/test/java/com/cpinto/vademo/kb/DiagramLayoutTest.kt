package com.cpinto.vademo.kb

import com.cpinto.vademo.kb.DiagramLayout.Box
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagramLayoutTest {

    private fun diagram(json: String) = Diagram.parse(json)

    private fun place(d: Diagram) =
        DiagramLayout.place(d, d.nodes.filterNot { it.isGroup }.associate { it.id to Box(200.0, 64.0) })

    private fun Placed.contains(o: Placed) =
        o.x >= x && o.y >= y && o.x + o.width <= x + width && o.y + o.height <= y + height

    private fun Placed.overlaps(o: Placed) =
        o.x < x + width && x < o.x + o.width && o.y < y + height && y < o.y + o.height

    @Test
    fun `a chain runs along the diagram's direction`() {
        val nodes = """[{ "id": "a" }, { "id": "b" }, { "id": "c" }]"""
        val edges = """[{ "source": "a", "target": "b" }, { "source": "b", "target": "c" }]"""
        val lr = place(diagram("""{ "direction": "LR", "nodes": $nodes, "edges": $edges }""")).boxes
        assertTrue(lr.getValue("a").x < lr.getValue("b").x && lr.getValue("b").x < lr.getValue("c").x)
        val tb = place(diagram("""{ "direction": "TB", "nodes": $nodes, "edges": $edges }""")).boxes
        assertTrue(tb.getValue("a").y < tb.getValue("b").y && tb.getValue("b").y < tb.getValue("c").y)
    }

    @Test
    fun `a cycle is laid out`() {
        val d = diagram(
            """{ "nodes": [{ "id": "a" }, { "id": "b" }, { "id": "c" }],
                 "edges": [{ "source": "a", "target": "b" }, { "source": "b", "target": "c" }, { "source": "c", "target": "a" }] }""",
        )
        val boxes = place(d).boxes.values.toList()
        assertEquals(3, boxes.size)
        for (i in boxes.indices) for (j in i + 1 until boxes.size) assertTrue(!boxes[i].overlaps(boxes[j]))
    }

    /** Shaped like a real agent's diagram: groups inside groups, edges across them, a node outside. */
    private val nested = diagram(
        """{ "direction": "TB",
             "nodes": [
               { "id": "repo", "kind": "group" },
               { "id": "src", "kind": "group", "parent": "repo" },
               { "id": "tests", "kind": "group", "parent": "repo" },
               { "id": "api", "parent": "src" }, { "id": "db", "parent": "src" }, { "id": "jobs", "parent": "src" },
               { "id": "unit", "parent": "tests" },
               { "id": "empty", "kind": "group" },
               { "id": "user" }
             ],
             "edges": [
               { "source": "user", "target": "api" }, { "source": "api", "target": "db" },
               { "source": "jobs", "target": "db" }, { "source": "unit", "target": "api" }
             ] }""",
    )

    @Test
    fun `every group holds its members, and no two siblings overlap`() {
        val boxes = place(nested).boxes
        val parentOf = nested.nodes.associate { it.id to it.parent }
        for (node in nested.nodes) {
            node.parent?.let { assertTrue(boxes.getValue(it).contains(boxes.getValue(node.id)), "${node.id} outside $it") }
        }
        for (a in nested.nodes) for (b in nested.nodes) {
            if (a.id < b.id && parentOf[a.id] == parentOf[b.id]) {
                assertTrue(!boxes.getValue(a.id).overlaps(boxes.getValue(b.id)), "${a.id} overlaps ${b.id}")
            }
        }
    }

    @Test
    fun `everything sits inside the diagram's bounds, from 0, 0`() {
        val places = place(nested)
        assertEquals(0.0, places.boxes.values.minOf { it.x })
        assertEquals(0.0, places.boxes.values.minOf { it.y })
        assertTrue(places.boxes.values.all { it.x + it.width <= places.width && it.y + it.height <= places.height })
    }

    @Test
    fun `an empty group keeps tyfold's default size`() {
        val empty = place(nested).boxes.getValue("empty")
        assertEquals(320.0, empty.width)
        assertEquals(200.0, empty.height)
    }

    @Test
    fun `a file where every node has a position keeps those places, relative to each group`() {
        val d = diagram(
            """{ "nodes": [
                 { "id": "g", "kind": "group", "position": { "x": -50, "y": 10 }, "size": { "width": 400, "height": 300 } },
                 { "id": "a", "parent": "g", "position": { "x": 20, "y": 48 } },
                 { "id": "b", "position": { "x": 500, "y": 10 } }
               ] }""",
        )
        val boxes = place(d).boxes
        assertEquals(Placed(0.0, 0.0, 400.0, 300.0), boxes["g"])
        assertEquals(Placed(20.0, 48.0, 200.0, 64.0), boxes["a"])
        assertEquals(Placed(550.0, 0.0, 200.0, 64.0), boxes["b"])
    }

    @Test
    fun `a parent that is missing, not a group, or circular is ignored`() {
        val d = diagram(
            """{ "nodes": [
                 { "id": "a", "parent": "nowhere" }, { "id": "b", "parent": "a" },
                 { "id": "g1", "kind": "group", "parent": "g2" }, { "id": "g2", "kind": "group", "parent": "g1" }
               ] }""",
        )
        assertEquals(setOf("a", "b", "g1", "g2"), place(d).boxes.keys)
    }

    @Test
    fun `the same file always gives the same picture`() {
        assertEquals(place(nested), place(nested))
    }
}
