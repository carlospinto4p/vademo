package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** tyfold's own cases for its Mermaid export (`src-tauri/src/graph/export.rs`), with its expected output. */
class MermaidExportTest {

    @Test
    fun `groups become subgraphs and arrows keep their style and label`() {
        val d = Diagram.parse(
            """{"title":"Payments","nodes":[
                {"id":"web","label":"Web app","tag":"frontend","status":"changed"},
                {"id":"be","label":"Backend","kind":"group"},
                {"id":"api","label":"API","parent":"be","status":"added"},
                {"id":"empty","label":"Later","kind":"group"}],
              "edges":[
                {"source":"web","target":"api","label":"HTTPS"},
                {"source":"api","target":"web","style":"dashed"}]}""",
        )
        assertEquals(
            listOf(
                "---",
                "title: \"Payments\"",
                "---",
                "flowchart LR",
                "  n0[\"Web app<br/>frontend<br/>[changed]\"]",
                "  subgraph n1[\"Backend\"]",
                "    n2[\"API<br/>[added]\"]",
                "  end",
                "  n3[\"Later\"]",
                "  n0 -->|\"HTTPS\"| n2",
                "  n2 -.-> n0",
            ).joinToString("\n"),
            MermaidExport.chart(d),
        )
    }

    @Test
    fun `a box says what its colour means in the legend`() {
        val d = Diagram.parse(
            """{"legend":[{"id":"ext","label":"Third <party>"}],
              "nodes":[{"id":"pay","label":"Stripe","status":"added","category":"ext"},{"id":"db"}]}""",
        )
        val chart = MermaidExport.chart(d)
        assertTrue("  n0[\"Stripe<br/>[added]<br/>(Third #lt;party#gt;)\"]" in chart, chart)
        assertTrue("  n1[\"db\"]" in chart, chart)
    }

    @Test
    fun `a group in a group is a subgraph in a subgraph`() {
        val d = Diagram.parse(
            """{"nodes":[
                {"id":"out","label":"Cloud","kind":"group"},
                {"id":"in","label":"Cluster","kind":"group","parent":"out"},
                {"id":"api","label":"API","parent":"in"},
                {"id":"lb","label":"LB","parent":"out"}]}""",
        )
        assertEquals(
            listOf(
                "flowchart LR",
                "  subgraph n0[\"Cloud\"]",
                "    subgraph n1[\"Cluster\"]",
                "      n2[\"API\"]",
                "    end",
                "    n3[\"LB\"]",
                "  end",
            ).joinToString("\n"),
            MermaidExport.chart(d),
        )
    }

    @Test
    fun `text that would break the chart is escaped`() {
        val d = Diagram.parse(
            """{"direction":"TB","nodes":[{"id":"a","label":"say \"hi\" <b>"},{"id":"b"}],
              "edges":[{"source":"a","target":"b","label":"x|y"}]}""",
        )
        val chart = MermaidExport.chart(d)
        assertTrue(chart.startsWith("flowchart TB\n"), chart)
        assertTrue("n0[\"say #quot;hi#quot; #lt;b#gt;\"]" in chart, chart)
        assertTrue("n1[\"b\"]" in chart, chart)
        assertTrue("-->|\"x#124;y\"|" in chart, chart)
    }

    @Test
    fun `a box whose parent is not a group is written at the top level, not dropped`() {
        val chart = MermaidExport.chart(Diagram.parse("""{"nodes":[{"id":"a","parent":"nowhere"},{"id":"b","parent":"a"}]}"""))
        assertTrue("  n0[\"a\"]" in chart && "  n1[\"b\"]" in chart, chart)
    }

    @Test
    fun `markdown wraps the chart in a fenced block`() {
        val md = MermaidExport.markdown(Diagram.parse("""{"nodes":[{"id":"a"}]}"""))
        assertTrue(md.startsWith("```mermaid\nflowchart LR\n") && md.endsWith("\n```\n"), md)
    }
}
