package com.cpinto.vademo.kb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

/**
 * A diagram in tyfold's graph format: `.tyfold-graphs/<name>.json` at the root
 * of a knowledge base, written by agents with tyfold's `tyfold_graph_write`
 * tool. The format and its rules are tyfold's (`docs/GRAPH-PANE.md` in that
 * repository, "The file"); vademo only reads it.
 *
 * Unknown keys are ignored rather than refused: tyfold already refuses them
 * when the file is written, and a newer tyfold adding a key should not stop
 * an older vademo from showing the rest.
 */
@Serializable
data class Diagram(
    val version: Int = 1,
    val title: String = "",
    val direction: Direction = Direction.LR,
    val legend: List<LegendEntry> = emptyList(),
    val nodes: List<DiagramNode> = emptyList(),
    val edges: List<DiagramEdge> = emptyList(),
) {
    @Serializable
    enum class Direction { LR, TB }

    /** The legend's colour for [category]: its place in the legend, as in tyfold. */
    fun hueOf(category: String?): Float? {
        val i = legend.indexOfFirst { it.id == category }
        return if (i < 0) null else LEGEND_HUES[i % LEGEND_HUES.size]
    }

    companion object {
        const val FOLDER = ".tyfold-graphs"

        /** tyfold's `LEGEND_HUES` (`src/lib/graph/legend.ts`), so a diagram has the same colours in both. */
        val LEGEND_HUES = floatArrayOf(225f, 315f, 180f, 270f, 75f, 30f, 135f, 0f)

        /** An unknown status or kind falls back to its default instead of failing the whole file. */
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun parse(text: String): Diagram = json.decodeFromString(serializer(), text)

        fun parse(element: JsonElement): Diagram = json.decodeFromJsonElement(serializer(), element)
    }
}

@Serializable
data class LegendEntry(val id: String, val label: String = "")

@Serializable
data class DiagramNode(
    val id: String,
    val label: String = "",
    val kind: Kind = Kind.NODE,
    val parent: String? = null,
    val tag: String? = null,
    val notes: String? = null,
    val ref: Ref? = null,
    /** Where the box's work is done, such as a worktree; tyfold links it to a session there. */
    val folder: String? = null,
    val status: Status? = null,
    val category: String? = null,
    /** Relative to the parent group's top-left corner, as tyfold draws it. */
    val position: Position? = null,
    val size: Size? = null,
) {
    @Serializable
    enum class Kind {
        @SerialName("node") NODE,
        @SerialName("group") GROUP,
    }

    @Serializable
    enum class Status {
        @SerialName("added") ADDED,
        @SerialName("changed") CHANGED,
        @SerialName("removed") REMOVED,
        @SerialName("unreachable") UNREACHABLE,
    }

    val isGroup: Boolean get() = kind == Kind.GROUP

    /** What the box says: its label, or its id when it has none, as in tyfold. */
    val text: String get() = label.ifBlank { id }
}

@Serializable
data class Ref(val path: String, val line: Int? = null)

@Serializable
data class Position(val x: Double, val y: Double)

@Serializable
data class Size(val width: Double, val height: Double)

@Serializable
data class DiagramEdge(
    val id: String = "",
    val source: String,
    val target: String,
    val label: String? = null,
    val style: Style = Style.SOLID,
) {
    @Serializable
    enum class Style {
        @SerialName("solid") SOLID,
        @SerialName("dashed") DASHED,
    }
}

/** One file in a knowledge base's [Diagram.FOLDER]. [diagram] is null when the file could not be read. */
data class DiagramFile(val name: String, val diagram: Diagram?, val error: String?) {
    val title: String get() = diagram?.title?.ifBlank { null } ?: name
}

/** The diagrams of the knowledge base at [root], by title. */
fun diagramsIn(root: File): List<DiagramFile> =
    File(root, Diagram.FOLDER).listFiles().orEmpty()
        .filter { it.isFile && it.extension == "json" }
        .map(::readDiagram)
        .sortedBy { it.title.lowercase() }

fun readDiagram(file: File): DiagramFile = try {
    DiagramFile(file.nameWithoutExtension, Diagram.parse(file.readText()), null)
} catch (e: Exception) {
    DiagramFile(file.nameWithoutExtension, null, e.message ?: e.javaClass.simpleName)
}
