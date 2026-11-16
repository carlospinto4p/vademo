package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagramDocumentTest {

    /** As tyfold writes a file: its key order, two-space indent, a final newline. */
    private val tyfoldText = """
        {
          "version": 1,
          "title": "Payments",
          "direction": "LR",
          "legend": [
            {
              "id": "team-a",
              "label": "Team A"
            }
          ],
          "nodes": [
            {
              "id": "backend",
              "label": "Backend",
              "kind": "group",
              "position": {
                "x": 40.0,
                "y": 10.0
              },
              "size": {
                "width": 320.0,
                "height": 200.0
              }
            },
            {
              "id": "api",
              "label": "API",
              "parent": "backend",
              "tag": "service",
              "ref": {
                "path": "src/api.rs",
                "line": 40
              },
              "folder": "worktrees/api",
              "category": "team-a",
              "position": {
                "x": 20.0,
                "y": 48.0
              }
            },
            {
              "id": "ledger",
              "label": "Ledger"
            }
          ],
          "edges": [
            {
              "id": "e1",
              "source": "api",
              "target": "ledger",
              "label": "SQL",
              "style": "dashed"
            }
          ]
        }
    """.trimIndent() + "\n"

    private val doc = DiagramDocument.parse(tyfoldText)

    private fun DiagramDocument.node(id: String) = diagram.nodes.single { it.id == id }

    @Test
    fun `a file tyfold wrote is written back byte for byte`() {
        assertEquals(tyfoldText, doc.toText())
    }

    @Test
    fun `a file of the wrong shape fails when parsed, not when first drawn`() {
        // Valid JSON, but not a diagram: the screen must get an error, not crash later.
        for (text in listOf("""{ "nodes": 5 }""", """{ "edges": [{ "source": 1 }] }""", "[]", "{ not json")) {
            assertThrows(IllegalArgumentException::class.java, { DiagramDocument.parse(text) }, text)
        }
    }

    @Test
    fun `keys this version does not know survive an edit`() {
        val text = """{ "title": "T", "theme": "dark", "nodes": [{ "id": "a", "glow": true }], "edges": [] }"""
        val edited = DiagramDocument.parse(text).editNode("a", NodeFields(label = "A")).toText()
        assertTrue("\"glow\": true" in edited, edited)
        assertTrue("\"theme\": \"dark\"" in edited, edited)
    }

    @Test
    fun `an edit changes only the lines it is about`() {
        val edited = doc.editNode("ledger", NodeFields(label = "Ledger DB")).toText()
        val changed = tyfoldText.lines().zip(edited.lines()).filter { (a, b) -> a != b }
        assertEquals(listOf("      \"label\": \"Ledger\"" to "      \"label\": \"Ledger DB\""), changed)
    }

    @Test
    fun `new boxes, groups and arrows take tyfold's free ids and words`() {
        val (one, a) = DiagramDocument.new("New").addNode(group = false)
        val (two, b) = one.addNode(group = false)
        val (three, g) = two.addNode(group = true)
        assertEquals(listOf("node-1", "node-2", "group-1"), listOf(a, b, g))
        assertEquals("New node", three.node(a).label)
        assertEquals("New group", three.node(g).label)
        assertEquals("e-1", three.addEdge(a, b)!!.second)
        assertNull(three.addEdge(a, "nowhere"))
    }

    @Test
    fun `editing a box sets its fields and leaves blank ones out`() {
        val fields = NodeFields(
            label = "API v2",
            tag = " ",
            notes = "Now async.",
            ref = Ref("", null),
            folder = null,
            status = DiagramNode.Status.CHANGED,
            category = "no-such-entry",
        )
        val api = doc.editNode("api", fields).node("api")
        assertEquals("API v2", api.label)
        assertNull(api.tag)
        assertEquals("Now async.", api.notes)
        assertNull(api.ref)
        assertNull(api.folder)
        assertEquals(DiagramNode.Status.CHANGED, api.status)
        assertNull(api.category)
        assertEquals("backend", api.parent)
    }

    @Test
    fun `deleting a group takes what is inside it, and the arrows to them`() {
        val after = doc.removeNodes(setOf("backend"))
        assertEquals(listOf("ledger"), after.diagram.nodes.map { it.id })
        assertTrue(after.diagram.edges.isEmpty())
    }

    @Test
    fun `moving a box to another group drops its position, and never into itself`() {
        val moved = doc.moveToGroup(setOf("api"), null)
        assertNull(moved.node("api").parent)
        assertNull(moved.node("api").position)
        val (withInner, inner) = doc.addNode(group = true, parent = "backend")
        val loop = withInner.moveToGroup(setOf("backend"), inner)
        assertNull(loop.node("backend").parent)
        assertEquals(listOf("backend", inner), withInner.groupsFor(setOf("api")).map { it.id })
        assertEquals(emptyList<String>(), withInner.groupsFor(setOf("backend")).map { it.id })
    }

    @Test
    fun `grouping puts the new group where the boxes were, and ungrouping undoes it`() {
        val (grouped, g) = doc.groupAround(setOf("api"))
        assertEquals("backend", grouped.node(g).parent)
        assertEquals(g, grouped.node("api").parent)
        val back = grouped.ungroup(g)
        assertEquals("backend", back.node("api").parent)
        assertFalse(back.diagram.nodes.any { it.id == g })
    }

    @Test
    fun `arrows can be relabelled, made solid, reversed and removed`() {
        val solid = doc.editEdge("e1", "reads", DiagramEdge.Style.SOLID)
        assertEquals("reads", solid.diagram.edges.single().label)
        assertFalse("\"style\"" in solid.toText())
        val reversed = solid.reverseEdge("e1").diagram.edges.single()
        assertEquals("ledger" to "api", reversed.source to reversed.target)
        assertTrue(doc.removeEdge("e1").diagram.edges.isEmpty())
    }

    @Test
    fun `legend entries get ids from their words, stop at eight, and take their colour with them`() {
        val (two, id) = doc.addLegendEntry("Team A!")!!
        assertEquals("team-a-2", id)
        var full = two
        repeat(6) { full = full.addLegendEntry("Layer $it")!!.first }
        assertNull(full.addLegendEntry("Ninth"))
        assertNull(doc.addLegendEntry("  "))
        val removed = doc.removeLegendEntry("team-a")
        assertTrue(removed.diagram.legend.isEmpty())
        assertNull(removed.node("api").category)
        assertEquals("Team Alpha", doc.renameLegendEntry("team-a", " Team Alpha ").diagram.legend.single().label)
    }

    @Test
    fun `status and category can be set on several boxes at once`() {
        val marked = doc.setStatus(setOf("api", "ledger"), DiagramNode.Status.ADDED).setCategory(setOf("ledger"), "team-a")
        assertEquals(DiagramNode.Status.ADDED, marked.node("ledger").status)
        assertEquals(DiagramNode.Status.ADDED, marked.node("api").status)
        assertEquals("team-a", marked.node("ledger").category)
        assertNull(marked.setStatus(setOf("api"), null).node("api").status)
    }

    @Test
    fun `copying a group takes what is inside it and the arrows among them`() {
        val clip = doc.copy(setOf("backend"))!!
        assertEquals(2, clip.size)
        assertNull(doc.copy(setOf("nowhere")))
        // Pasted into a diagram that has none of these ids, everything keeps its id.
        val (empty, _) = DiagramDocument.new("Other").addNode(group = false)
        val (pasted, top) = empty.paste(clip)
        assertEquals(setOf("backend"), top)
        assertEquals("backend", pasted.node("api").parent)
        assertNull(pasted.node("api").category) // the other diagram's legend has no such entry
        assertNull(pasted.node("backend").position)
    }

    @Test
    fun `a cut and a paste move a box, arrows included, into another diagram`() {
        val (left, clip) = doc.cut(setOf("api", "ledger"))!!
        assertEquals(listOf("backend"), left.diagram.nodes.map { it.id })
        val (other, _) = DiagramDocument.parse(tyfoldText).removeNodes(setOf("api", "ledger")).paste(clip)
        assertEquals(setOf("backend", "api", "ledger"), other.diagram.nodes.map { it.id }.toSet())
        assertEquals("e1", other.diagram.edges.single().id)
        assertEquals("backend", other.node("api").parent) // its group is there, so it goes back in
    }

    @Test
    fun `duplicating gives fresh ids, keeps the group, and copies the arrows between the copies`() {
        val (twice, copies) = doc.duplicate(setOf("api", "ledger"))!!
        assertEquals(setOf("node-1", "node-2"), copies)
        val apiCopy = twice.node("node-1")
        assertEquals("API", apiCopy.label)
        assertEquals("backend", apiCopy.parent)
        val newArrow = twice.diagram.edges.single { it.id != "e1" }
        assertEquals("e-1", newArrow.id)
        assertEquals("node-1" to "node-2", newArrow.source to newArrow.target)
        assertEquals(emptyList<String>(), twice.problems())
    }

    @Test
    fun `an arrow without an id gets e and a free number when written`() {
        val text = """{ "nodes": [{ "id": "a" }, { "id": "b" }], "edges": [{ "id": "e1", "source": "a", "target": "b" }, { "source": "b", "target": "a" }] }"""
        assertTrue("\"id\": \"e2\"" in DiagramDocument.parse(text).toText())
    }

    @Test
    fun `the check finds what tyfold refuses, in its words`() {
        val broken = DiagramDocument.parse(
            """{ "version": 2,
                 "legend": [{ "id": "x", "label": "" }],
                 "nodes": [
                   { "id": "a", "category": "y", "ref": { "path": " " } },
                   { "id": "a" },
                   { "id": "g", "kind": "group", "parent": "h" }, { "id": "h", "kind": "group", "parent": "g" },
                   { "id": "b", "parent": "a" }
                 ],
                 "edges": [{ "id": "e", "source": "a", "target": "z" }] }""",
        )
        val problems = broken.problems()
        listOf(
            "version is 2, and only 1 is supported",
            "node id \"a\" is used twice",
            "node \"a\" has a ref with an empty path",
            "legend entry \"x\" has no label: say what its colour means",
            "node \"a\" has category \"y\", which is not in the legend",
            "node \"b\" names parent \"a\", which is not a group (give it \"kind\": \"group\")",
            "group \"g\" sits inside itself, through its parents",
            "edge a → z names \"z\", which is not a node",
        ).forEach { assertTrue(it in problems, "missing: $it in $problems") }
        assertEquals(emptyList<String>(), doc.problems())
    }

    @Test
    fun `the check finds empty ids, line 0, empty folders, a long legend and repeated ids`() {
        val legend = (1..9).joinToString(", ") { """{ "id": "c$it", "label": "C$it" }""" }
        val broken = DiagramDocument.parse(
            """{ "legend": [$legend, { "id": "", "label": "Blank" }, { "id": "c1", "label": "Again" }],
                 "nodes": [
                   { "id": "" },
                   { "id": "a", "ref": { "path": "src/a.kt", "line": 0 }, "folder": " " },
                   { "id": "b", "parent": "gone" }
                 ],
                 "edges": [{ "id": "e", "source": "a", "target": "b" }, { "id": "e", "source": "b", "target": "a" }] }""",
        )
        val problems = broken.problems()
        listOf(
            "a node has an empty id",
            "node \"a\" has a ref to line 0, and lines are counted from 1",
            "node \"a\" has an empty folder",
            "the legend has 11 entries, and there are only 8 colours",
            "a legend entry has an empty id",
            "legend id \"c1\" is used twice",
            "node \"b\" names parent \"gone\", which is not a node",
            "edge id \"e\" is used twice",
        ).forEach { assertTrue(it in problems, "missing: $it in $problems") }
    }

    @Test
    fun `ungrouping something that is not a group changes nothing`() {
        assertEquals(doc, doc.ungroup("nowhere"))
        assertEquals(doc, doc.ungroup("api"))
    }
}
