package com.cpinto.vademo.kb

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Boxes and arrows taken by a cut or copy, to be pasted into this diagram or another. */
class DiagramClip internal constructor(internal val nodes: List<JsonObject>, internal val edges: List<JsonObject>) {
    val size: Int get() = nodes.size
}

/** The fields of a box the editor sets. A blank or null field is left out of the file. */
data class NodeFields(
    val label: String = "",
    val tag: String? = null,
    val notes: String? = null,
    val ref: Ref? = null,
    val folder: String? = null,
    val status: DiagramNode.Status? = null,
    val category: String? = null,
)

/**
 * A diagram file being edited: the file's own JSON, changed one edit at a
 * time. Every edit returns a new document, so the screen can keep the old ones
 * for undo.
 *
 * Working on the JSON rather than on [Diagram] keeps whatever an edit does not
 * touch exactly as it was, including keys this version of vademo does not
 * know, such as ones a newer tyfold writes. The edits are tyfold's canvas
 * edits (`src/lib/graph/canvas/graphEdits.ts` and `graphClip.ts` in that
 * repository); [problems] is its check (`validate` in `src-tauri/src/graph.rs`),
 * with the same sentences. Positions are never added: tyfold keeps where a box
 * was dragged off the file, and so does vademo.
 */
class DiagramDocument private constructor(val json: JsonObject) {

    val diagram: Diagram by lazy { Diagram.parse(json) }

    private val nodes: List<JsonObject> get() = json["nodes"]?.jsonArray?.map { it.jsonObject }.orEmpty()
    private val edges: List<JsonObject> get() = json["edges"]?.jsonArray?.map { it.jsonObject }.orEmpty()
    private val legend: List<JsonObject> get() = json["legend"]?.jsonArray?.map { it.jsonObject }.orEmpty()

    /** The file's text as tyfold writes it: keys in its order, two-space indent, a final newline. */
    fun toText(): String = PRETTY.encodeToString(JsonObject.serializer(), normalized()) + "\n"

    override fun equals(other: Any?) = other is DiagramDocument && other.json == json
    override fun hashCode() = json.hashCode()

    // ---- the whole diagram -------------------------------------------------

    fun withTitle(title: String) = with("title", JsonPrimitive(title))

    fun withDirection(direction: Diagram.Direction) = with("direction", JsonPrimitive(direction.name))

    // ---- boxes ------------------------------------------------------------

    /** A new box or group, inside [parent] when given; returns the document and the new id. */
    fun addNode(group: Boolean, parent: String? = null): Pair<DiagramDocument, String> {
        val id = freeId(nodes.map { it.id }, if (group) "group" else "node")
        val node = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("label", JsonPrimitive(if (group) "New group" else "New node"))
            if (group) put("kind", JsonPrimitive("group"))
            if (parent != null && isGroup(parent)) put("parent", JsonPrimitive(parent))
        }
        return withNodes(nodes + node) to id
    }

    fun fieldsOf(id: String): NodeFields? = diagram.nodes.firstOrNull { it.id == id }?.let {
        NodeFields(it.label, it.tag, it.notes, it.ref, it.folder, it.status, it.category)
    }

    fun editNode(id: String, fields: NodeFields): DiagramDocument = withNodes(
        nodes.map { node ->
            if (node.id != id) return@map node
            node.edit {
                put("label", JsonPrimitive(fields.label))
                setOrRemove("tag", fields.tag)
                setOrRemove("notes", fields.notes)
                if (fields.ref == null || fields.ref.path.isBlank()) remove("ref") else put("ref", refJson(fields.ref))
                setOrRemove("folder", fields.folder)
                setOrRemove("status", fields.status?.name?.lowercase())
                setOrRemove("category", fields.category?.takeIf { c -> legend.any { it.id == c } })
            }
        },
    )

    fun setStatus(ids: Set<String>, status: DiagramNode.Status?) = withNodes(
        nodes.map { if (it.id in ids) it.edit { setOrRemove("status", status?.name?.lowercase()) } else it },
    )

    fun setCategory(ids: Set<String>, category: String?): DiagramDocument {
        val known = category?.takeIf { c -> legend.any { it.id == c } }
        return withNodes(nodes.map { if (it.id in ids) it.edit { setOrRemove("category", known) } else it })
    }

    /**
     * Removes [ids]; a group goes with everything inside it, as tyfold's
     * Delete does, and every arrow to or from what went goes too.
     */
    fun removeNodes(ids: Set<String>): DiagramDocument {
        val gone = nodes.map { it.id }.filter { id -> ids.any { within(id, it) } }.toSet()
        return withNodes(nodes.filter { it.id !in gone })
            .withEdges(edges.filter { it.string("source") !in gone && it.string("target") !in gone })
    }

    /**
     * Puts [ids] inside [group], or at the top level when null. A box cannot
     * go into itself or into a group inside it; those are left where they are.
     * A position the file held is dropped, because it was relative to the old
     * group (tyfold's rule, `docs/GRAPH-PANE.md`, "Where the boxes sit").
     */
    fun moveToGroup(ids: Set<String>, group: String?): DiagramDocument {
        if (group != null && !isGroup(group)) return this
        return withNodes(
            nodes.map { node ->
                val id = node.id
                if (id !in ids || node.string("parent") == group) return@map node
                if (group != null && within(group, id)) return@map node
                node.edit {
                    setOrRemove("parent", group)
                    remove("position")
                }
            },
        )
    }

    /** The groups [ids] may be moved into: not one of them, nor inside one of them. */
    fun groupsFor(ids: Set<String>): List<DiagramNode> =
        diagram.nodes.filter { g -> g.isGroup && ids.none { within(g.id, it) } }

    /**
     * Wraps [ids] in a new group, placed where they all were when they shared
     * a group, else at the top level. Returns the document and the group's id.
     */
    fun groupAround(ids: Set<String>): Pair<DiagramDocument, String> {
        val parents = nodes.filter { it.id in ids }.map { it.string("parent") }.distinct()
        val (added, group) = addNode(group = true, parent = parents.singleOrNull())
        return added.moveToGroup(ids, group) to group
    }

    /** Removes [group] and puts what it held into the group around it. Arrows to the group itself go. */
    fun ungroup(group: String): DiagramDocument {
        if (!isGroup(group)) return this
        val outer = nodes.first { it.id == group }.string("parent")
        val held = nodes.filter { it.string("parent") == group }.map { it.id }.toSet()
        return moveToGroup(held, outer).let { moved ->
            moved.withNodes(moved.nodes.filter { it.id != group })
                .withEdges(moved.edges.filter { it.string("source") != group && it.string("target") != group })
        }
    }

    // ---- arrows -----------------------------------------------------------

    /** A new arrow from [source] to [target]; returns the document and its id, or null when either end is missing. */
    fun addEdge(source: String, target: String): Pair<DiagramDocument, String>? {
        val ids = nodes.map { it.id }.toSet()
        if (source !in ids || target !in ids) return null
        val id = freeId(edges.map { it.id }, "e")
        val edge = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("source", JsonPrimitive(source))
            put("target", JsonPrimitive(target))
        }
        return withEdges(edges + edge) to id
    }

    fun editEdge(id: String, label: String?, style: DiagramEdge.Style): DiagramDocument = withEdges(
        edges.map { edge ->
            if (edge.id != id) edge
            else edge.edit {
                setOrRemove("label", label)
                if (style == DiagramEdge.Style.SOLID) remove("style") else put("style", JsonPrimitive("dashed"))
            }
        },
    )

    fun reverseEdge(id: String): DiagramDocument = withEdges(
        edges.map { edge ->
            if (edge.id != id) edge
            else edge.edit {
                put("source", edge["target"]!!)
                put("target", edge["source"]!!)
            }
        },
    )

    fun removeEdge(id: String): DiagramDocument = withEdges(edges.filter { it.id != id })

    // ---- the legend -------------------------------------------------------

    /**
     * Adds an entry for [label] and returns its id: the label's words joined by
     * `-`, then a number when taken (tyfold's `freeEntryId`). Null when the
     * label is blank or the legend already has [Diagram.LEGEND_HUES] entries.
     */
    fun addLegendEntry(label: String): Pair<DiagramDocument, String>? {
        val text = label.trim()
        if (text.isEmpty() || legend.size >= Diagram.LEGEND_HUES.size) return null
        val base = text.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), "-").trim('-').ifEmpty { "category" }
        val taken = legend.map { it.id }.toSet()
        val id = if (base !in taken) base else generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it !in taken }
        val entry = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("label", JsonPrimitive(text))
        }
        return with("legend", JsonArray(legend + entry)) to id
    }

    fun renameLegendEntry(id: String, label: String): DiagramDocument {
        val text = label.trim()
        if (text.isEmpty()) return this
        return with("legend", JsonArray(legend.map { if (it.id == id) it.edit { put("label", JsonPrimitive(text)) } else it }))
    }

    /** Removes an entry; the boxes that had it lose their category, and the later entries take the earlier colours. */
    fun removeLegendEntry(id: String): DiagramDocument =
        with("legend", JsonArray(legend.filter { it.id != id }))
            .withNodes(nodes.map { if (it.string("category") == id) it.edit { remove("category") } else it })

    // ---- cut, copy, paste, duplicate ---------------------------------------

    /**
     * What a copy of [ids] holds, as tyfold's Copy: the boxes, everything
     * inside a copied group, and the arrows with both ends among them. Null
     * when none of [ids] is in the diagram.
     */
    fun copy(ids: Set<String>): DiagramClip? {
        val taken = nodes.filter { node -> ids.any { within(node.id, it) } }
        if (taken.isEmpty()) return null
        val inside = taken.map { it.id }.toSet()
        val arrows = edges.filter { it.string("source") in inside && it.string("target") in inside }
        return DiagramClip(taken, arrows)
    }

    /** tyfold's Cut: what [copy] holds, and the diagram without it. */
    fun cut(ids: Set<String>): Pair<DiagramDocument, DiagramClip>? = copy(ids)?.let { removeNodes(ids) to it }

    /**
     * Adds what [clip] holds, as tyfold's Paste. A box or arrow keeps its id
     * when this diagram has none like it, so a cut and a paste move it, into
     * another diagram too; otherwise it gets a free one. A copy goes into the
     * group its original was in when this diagram has that group, else to the
     * top level. A category this legend lacks is dropped, and so are places
     * from the file, which were relative to where the original sat. Returns
     * the document and the ids of the pasted boxes not inside another pasted one.
     */
    fun paste(clip: DiagramClip): Pair<DiagramDocument, Set<String>> {
        val taken = nodes.map { it.id }.toMutableSet()
        val renamed = mutableMapOf<String, String>()
        for (node in clip.nodes) {
            val id = node.id
            val fresh = if (id in taken) freeId(taken, if (node.string("kind") == "group") "group" else "node") else id
            taken += fresh
            renamed[id] = fresh
        }
        val legendIds = legend.map { it.id }.toSet()
        val top = mutableSetOf<String>()
        val pasted = clip.nodes.map { node ->
            val parent = node.string("parent")
            val newParent = when {
                parent == null -> null
                parent in renamed -> renamed.getValue(parent)
                isGroup(parent) -> parent
                else -> null
            }
            if (parent == null || parent !in renamed) top += renamed.getValue(node.id)
            node.edit {
                put("id", JsonPrimitive(renamed.getValue(node.id)))
                setOrRemove("parent", newParent)
                if (node.string("category") !in legendIds) remove("category")
                remove("position")
                remove("size")
            }
        }
        val edgeIds = edges.map { it.id }.toMutableSet()
        val arrows = clip.edges.map { edge ->
            val id = edge.id.takeIf { it.isNotEmpty() && it !in edgeIds } ?: freeId(edgeIds, "e")
            edgeIds += id
            edge.edit {
                put("id", JsonPrimitive(id))
                put("source", JsonPrimitive(renamed.getValue(edge.string("source")!!)))
                put("target", JsonPrimitive(renamed.getValue(edge.string("target")!!)))
            }
        }
        return withNodes(nodes + pasted).withEdges(edges + arrows) to top
    }

    /** tyfold's Duplicate: a copy of [ids] pasted into the same diagram. */
    fun duplicate(ids: Set<String>): Pair<DiagramDocument, Set<String>>? = copy(ids)?.let { paste(it) }

    // ---- the check --------------------------------------------------------

    /** What is wrong with the file, in tyfold's words; empty when it is well-formed. */
    fun problems(): List<String> {
        val errors = mutableListOf<String>()
        val version = json["version"]?.jsonPrimitive?.intOrNull ?: 1
        if (version != 1) errors += "version is $version, and only 1 is supported"
        val byId = mutableMapOf<String, JsonObject>()
        for (node in nodes) {
            if (node.id.isBlank()) errors += "a node has an empty id"
            else if (byId.put(node.id, node) != null) errors += "node id \"${node.id}\" is used twice"
        }
        for (node in nodes) {
            val ref = node["ref"] as? JsonObject ?: continue
            if (ref.string("path").isNullOrBlank()) errors += "node \"${node.id}\" has a ref with an empty path"
            if (ref["line"]?.jsonPrimitive?.intOrNull == 0) {
                errors += "node \"${node.id}\" has a ref to line 0, and lines are counted from 1"
            }
        }
        for (node in nodes) {
            if (node.containsKey("folder") && node.string("folder").isNullOrBlank()) {
                errors += "node \"${node.id}\" has an empty folder"
            }
        }
        if (legend.size > Diagram.LEGEND_HUES.size) {
            errors += "the legend has ${legend.size} entries, and there are only ${Diagram.LEGEND_HUES.size} colours"
        }
        val legendIds = mutableSetOf<String>()
        for (entry in legend) {
            if (entry.id.isBlank()) errors += "a legend entry has an empty id"
            else if (!legendIds.add(entry.id)) errors += "legend id \"${entry.id}\" is used twice"
            if (entry.string("label").isNullOrBlank()) {
                errors += "legend entry \"${entry.id}\" has no label: say what its colour means"
            }
        }
        for (node in nodes) {
            val category = node.string("category") ?: continue
            if (category !in legendIds) errors += "node \"${node.id}\" has category \"$category\", which is not in the legend"
        }
        for (node in nodes) {
            val parent = node.string("parent") ?: continue
            val found = byId[parent]
            if (found == null) errors += "node \"${node.id}\" names parent \"$parent\", which is not a node"
            else if (found.string("kind") != "group") {
                errors += "node \"${node.id}\" names parent \"$parent\", which is not a group (give it \"kind\": \"group\")"
            }
        }
        for (group in nodes.filter { it.string("kind") == "group" }) {
            var at = group.string("parent")
            repeat(nodes.size) {
                if (at == null) return@repeat
                if (at == group.id) {
                    errors += "group \"${group.id}\" sits inside itself, through its parents"
                    at = null
                } else {
                    at = byId[at]?.string("parent")
                }
            }
        }
        val edgeIds = mutableSetOf<String>()
        for (edge in edges) {
            val id = edge.id
            if (id.isNotEmpty() && !edgeIds.add(id)) errors += "edge id \"$id\" is used twice"
            for (end in listOf(edge.string("source"), edge.string("target"))) {
                if (end !in byId) errors += "edge ${edge.string("source")} → ${edge.string("target")} names \"$end\", which is not a node"
            }
        }
        return errors
    }

    // ---- helpers ----------------------------------------------------------

    private fun isGroup(id: String) = nodes.any { it.id == id && it.string("kind") == "group" }

    /** Whether [id] is [ancestor] or sits somewhere inside it. Bounded against a loop of parents. */
    private fun within(id: String, ancestor: String): Boolean {
        val parentOf = nodes.associate { it.id to it.string("parent") }
        var at: String? = id
        repeat(nodes.size + 1) {
            if (at == null) return false
            if (at == ancestor) return true
            at = parentOf[at]
        }
        return false
    }

    private fun with(key: String, value: JsonElement) = DiagramDocument(JsonObject(json + (key to value)))
    private fun withNodes(list: List<JsonObject>) = with("nodes", JsonArray(list))
    private fun withEdges(list: List<JsonObject>) = with("edges", JsonArray(list))

    /**
     * Keys in the order tyfold's types declare them, unknown keys after, and
     * edges without an id given one (tyfold's `normalize`), so a file written
     * here and then by tyfold differs only where something changed.
     */
    private fun normalized(): JsonObject {
        val named = edges.filter { it.id.isNotEmpty() }.map { it.id }.toMutableSet()
        var next = 1
        val fixedEdges = edges.map { edge ->
            if (edge.id.isNotEmpty()) return@map edge
            while ("e$next" in named) next++
            named += "e$next"
            edge.edit { put("id", JsonPrimitive("e$next")) }
        }
        val out = json.edit {
            put("nodes", JsonArray(nodes.map { ordered(it, NODE_KEYS) }))
            put("edges", JsonArray(fixedEdges.map { ordered(it, EDGE_KEYS) }))
            if (legend.isEmpty()) remove("legend") else put("legend", JsonArray(legend.map { ordered(it, LEGEND_KEYS) }))
            putIfAbsent("version", JsonPrimitive(1))
            putIfAbsent("title", JsonPrimitive(""))
            putIfAbsent("direction", JsonPrimitive("LR"))
        }
        return ordered(out, GRAPH_KEYS)
    }

    companion object {
        private val GRAPH_KEYS = listOf("version", "title", "direction", "legend", "nodes", "edges")
        private val NODE_KEYS = listOf(
            "id", "label", "kind", "parent", "tag", "notes", "ref", "folder", "status", "category", "position", "size",
        )
        private val EDGE_KEYS = listOf("id", "source", "target", "label", "style")
        private val LEGEND_KEYS = listOf("id", "label")

        @OptIn(ExperimentalSerializationApi::class)
        private val PRETTY = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        fun parse(text: String) = DiagramDocument(Json.parseToJsonElement(text).jsonObject)

        /** A new, empty diagram, as tyfold's New ▸ Graph makes one. */
        fun new(title: String, direction: Diagram.Direction = Diagram.Direction.LR) = DiagramDocument(
            buildJsonObject {
                put("version", JsonPrimitive(1))
                put("title", JsonPrimitive(title))
                put("direction", JsonPrimitive(direction.name))
                put("nodes", JsonArray(emptyList()))
                put("edges", JsonArray(emptyList()))
            },
        )

        /** `<prefix>-<n>` for the smallest n not taken, as tyfold's `freeId` does. */
        fun freeId(taken: Collection<String>, prefix: String): String {
            val set = taken.toSet()
            return generateSequence(1) { it + 1 }.map { "$prefix-$it" }.first { it !in set }
        }

        private fun ordered(obj: JsonObject, keys: List<String>): JsonObject {
            val known = keys.filter { it in obj }.associateWith { obj.getValue(it) }
            return JsonObject(LinkedHashMap(known).apply { putAll(obj.filterKeys { it !in keys }) })
        }

        private val JsonObject.id: String get() = string("id").orEmpty()

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.edit(block: MutableMap<String, JsonElement>.() -> Unit) =
            JsonObject(LinkedHashMap(this).apply(block))

        private fun MutableMap<String, JsonElement>.setOrRemove(key: String, value: String?) {
            if (value.isNullOrBlank()) remove(key) else put(key, JsonPrimitive(value.trim()))
        }

        private fun refJson(ref: Ref) = buildJsonObject {
            put("path", JsonPrimitive(ref.path.trim()))
            ref.line?.let { put("line", JsonPrimitive(it)) }
        }
    }
}
