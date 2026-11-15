package com.cpinto.vademo.kb

/**
 * A diagram as a Mermaid `flowchart`, ported line for line from tyfold's
 * `src-tauri/src/graph/export.rs` so both apps export the same text. tyfold
 * checked that syntax by rendering it with mermaid 12 (its module comment
 * says how); this port is checked against that module's own test cases.
 *
 * Groups become subgraphs, nested as deep as the groups are; a box says its
 * label, its tag, its status in brackets and its legend entry in
 * parentheses; a dashed arrow stays dashed. Mermaid gets no colours: the
 * legend's words carry the meaning.
 */
object MermaidExport {

    /** Mermaid ids are not quoted, so every box gets a positional alias rather than its own id. */
    private fun alias(index: Int) = "n$index"

    /** Mermaid reads a quoted label as HTML: characters that would open markup or close the quote are escaped. */
    private fun escape(text: String) = text
        .replace("&", "#amp;")
        .replace("<", "#lt;")
        .replace(">", "#gt;")
        .replace("\"", "#quot;")
        .replace("\n", "<br/>")

    private fun label(diagram: Diagram, node: DiagramNode): String {
        val out = StringBuilder(escape(node.label.ifEmpty { node.id }))
        node.tag?.takeIf { it.isNotEmpty() }?.let { out.append("<br/>").append(escape(it)) }
        node.status?.let { out.append("<br/>[").append(it.name.lowercase()).append(']') }
        diagram.legend.firstOrNull { it.id == node.category }?.let { out.append("<br/>(").append(escape(it.label)).append(')') }
        return out.toString()
    }

    fun chart(diagram: Diagram): String {
        val nodes = diagram.nodes
        val index = nodes.withIndex().associate { (i, n) -> n.id to i }
        // tyfold exports only files it has checked; vademo may hold one whose parent is not a group,
        // and such a box is written at the top level rather than dropped.
        val parentOf = nodes.associate { n -> n.id to n.parent?.takeIf { p -> index[p]?.let { nodes[it].isGroup } == true } }
        val members = mutableMapOf<String, MutableList<Int>>()
        nodes.forEachIndexed { i, node -> parentOf[node.id]?.let { members.getOrPut(it) { mutableListOf() } += i } }

        val lines = mutableListOf<String>()
        if (diagram.title.isNotEmpty()) {
            val title = diagram.title.replace("\\", "\\\\").replace("\"", "\\\"")
            lines += listOf("---", "title: \"$title\"", "---")
        }
        lines += "flowchart ${diagram.direction.name}"

        // A group holding nothing is written as an ordinary box: an empty subgraph renders as a stray box.
        fun write(i: Int, depth: Int) {
            val node = nodes[i]
            val indent = "  ".repeat(depth)
            val held = members[node.id].orEmpty()
            if (!node.isGroup || held.isEmpty()) {
                lines += "$indent${alias(i)}[\"${label(diagram, node)}\"]"
                return
            }
            lines += "${indent}subgraph ${alias(i)}[\"${label(diagram, node)}\"]"
            held.forEach { write(it, depth + 1) }
            lines += "${indent}end"
        }
        nodes.forEachIndexed { i, node -> if (parentOf[node.id] == null) write(i, 1) }

        for (edge in diagram.edges) {
            val source = index[edge.source] ?: continue
            val target = index[edge.target] ?: continue
            val arrow = if (edge.style == DiagramEdge.Style.DASHED) "-.->" else "-->"
            // A `|` ends an arrow's label, so it is written as its character code.
            val text = edge.label?.takeIf { it.isNotEmpty() }?.let { "|\"${escape(it).replace("|", "#124;")}\"|" }.orEmpty()
            lines += "  ${alias(source)} $arrow$text ${alias(target)}"
        }
        return lines.joinToString("\n")
    }

    /** The chart in a fenced block, as tyfold writes a `.md` export, so it renders wherever Markdown does. */
    fun markdown(diagram: Diagram): String = "```mermaid\n${chart(diagram)}\n```\n"
}
