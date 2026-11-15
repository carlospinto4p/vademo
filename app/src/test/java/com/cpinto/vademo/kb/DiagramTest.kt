package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DiagramTest {

    @TempDir
    lateinit var root: File

    /** The example in tyfold's `docs/GRAPH-PANE.md`, "The file". */
    private val example = """
        {
          "version": 1,
          "title": "Payments architecture",
          "direction": "LR",
          "nodes": [
            { "id": "backend", "label": "Backend", "kind": "group" },
            { "id": "api", "label": "Payments API", "tag": "service", "parent": "backend",
              "notes": "Axum, stateless.", "ref": { "path": "src/api.rs", "line": 40 } },
            { "id": "ledger", "label": "Ledger", "tag": "database", "status": "changed" }
          ],
          "edges": [
            { "id": "e1", "source": "api", "target": "ledger", "label": "SQL", "style": "dashed" }
          ]
        }
    """.trimIndent()

    @Test
    fun `reads tyfold's example file`() {
        val d = Diagram.parse(example)
        assertEquals("Payments architecture", d.title)
        assertEquals(DiagramNode.Kind.GROUP, d.nodes[0].kind)
        assertEquals(Ref("src/api.rs", 40), d.nodes[1].ref)
        assertEquals(DiagramNode.Status.CHANGED, d.nodes[2].status)
        assertEquals(DiagramEdge.Style.DASHED, d.edges.single().style)
    }

    @Test
    fun `only ids, sources and targets are needed`() {
        val d = Diagram.parse("""{ "nodes": [{ "id": "a" }, { "id": "b" }], "edges": [{ "source": "a", "target": "b" }] }""")
        assertEquals(Diagram.Direction.LR, d.direction)
        assertEquals("a", d.nodes[0].text)
        assertEquals(DiagramEdge.Style.SOLID, d.edges.single().style)
    }

    @Test
    fun `keys and words from a newer tyfold are skipped, not fatal`() {
        val d = Diagram.parse("""{ "theme": "x", "nodes": [{ "id": "a", "status": "archived", "glow": true }] }""")
        assertNull(d.nodes.single().status)
    }

    @Test
    fun `legend colours follow tyfold's order of hues`() {
        val d = Diagram.parse("""{ "legend": [{ "id": "a", "label": "A" }, { "id": "b", "label": "B" }] }""")
        assertEquals(225f, d.hueOf("a"))
        assertEquals(315f, d.hueOf("b"))
        assertNull(d.hueOf("c"))
    }

    @Test
    fun `lists a knowledge base's diagrams by title, with unreadable files named`() {
        val folder = File(root, Diagram.FOLDER).apply { mkdirs() }
        File(folder, "payments.json").writeText(example)
        File(folder, "apple.json").writeText("""{ "title": "Zebra" }""")
        File(folder, "broken.json").writeText("{ not json")
        File(folder, "notes.md").writeText("not a diagram")
        val files = diagramsIn(root)
        assertEquals(listOf("broken", "Payments architecture", "Zebra"), files.map { it.title })
        assertNotNull(files.first().error)
        assertNull(files.first().diagram)
    }

    @Test
    fun `a knowledge base without the folder has no diagrams`() {
        assertEquals(emptyList<DiagramFile>(), diagramsIn(root))
    }
}
