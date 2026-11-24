package com.cpinto.vademo.kb

/**
 * One box on a [FoldedMap]: a note, a whole folder folded into one box, or
 * the notes of an open folder left over past its limit.
 *
 * [path] is the note a tap opens: the note itself, or for a folded folder its
 * hub (`<folder>/<folder>.md`), and null for a folded folder with no hub and
 * for the notes left over. [notes] is how many notes the box stands for.
 * [large] boxes are the index, hubs and folded folders: the knowledge base's
 * shape. [rest] is the box of notes left over, which a tap shows more of.
 */
data class MapBox(
    val title: String,
    val folder: String,
    val path: String?,
    val notes: Int,
    val folded: Boolean,
    val large: Boolean,
    val rest: Boolean = false,
)

/**
 * The map of notes drawn as the knowledge base's shape: `index.md` and one
 * box per topic folder, linked where their notes link, with only the folders
 * in [FoldedMap.of]'s `open` drawn note by note. A knowledge base of a few
 * hundred notes is a hairball when every note is a box; folded, it is a
 * dozen boxes that each lead on.
 *
 * An open folder draws at most its limit of notes besides its hub, the most
 * linked first; the rest share one box, which a tap opens further.
 */
data class FoldedMap(val boxes: List<MapBox>, val links: List<Pair<Int, Int>>) {

    /** The boxes linked to box [box]. */
    fun neighbours(box: Int): Set<Int> =
        links.mapNotNullTo(mutableSetOf()) { (a, b) -> if (a == box) b else if (b == box) a else null }

    /**
     * The links to draw while box [selected] is selected (-1 for none): its
     * own, and those that show the knowledge base's shape (between two large
     * boxes, or between a box and its own folder's hub). Every link at once
     * is a hairball, so a note's other links show only once it is selected.
     */
    fun drawn(selected: Int): List<Pair<Int, Int>> =
        links.filter { (a, b) -> a == selected || b == selected || shape(boxes[a], boxes[b]) }

    private fun shape(a: MapBox, b: MapBox): Boolean =
        (a.large && b.large) || (a.folder == b.folder && (a.large || b.large))

    companion object {
        /** How many notes besides its hub an open folder draws at first, and how many more each tap on its rest adds. */
        const val LIMIT = 12

        /**
         * [graph] with every folder folded but those in [open]. Source notes
         * are left out unless [sources]; a link to a left-out note is too.
         * An open folder draws [limits]'s number of notes besides its hub
         * ([LIMIT] when it has none), the note at [keep] always among them.
         */
        fun of(
            graph: NoteGraph,
            open: Set<String>,
            sources: Boolean,
            limits: Map<String, Int> = emptyMap(),
            keep: String? = null,
        ): FoldedMap {
            val boxes = mutableListOf<MapBox>()
            // Note index in graph → box index, for the notes shown.
            val boxOf = arrayOfNulls<Int>(graph.notes.size)
            val shown = graph.notes.withIndex().filter { (_, note) -> sources || !note.isSource }
            val degree = IntArray(graph.notes.size)
            for ((a, b) in graph.links) {
                if (sources || (!graph.notes[a].isSource && !graph.notes[b].isSource)) {
                    degree[a]++
                    degree[b]++
                }
            }
            for ((folder, members) in shown.groupBy { it.value.folder }) {
                if (folder.isEmpty() || folder in open) {
                    val limit = limits[folder] ?: LIMIT
                    // The hub and the note to keep first, then the most linked; ties stay in file order.
                    val ranked = members.sortedWith(
                        compareBy({ !isHub(it.value) }, { it.value.path != keep }, { -degree[it.index] }),
                    )
                    val hubs = ranked.count { isHub(it.value) }
                    val drawn = ranked.take(hubs + limit).sortedBy { it.index }
                    for ((i, note) in drawn) {
                        boxOf[i] = boxes.size
                        boxes += MapBox(note.title, folder, note.path, 1, folded = false, large = isHub(note))
                    }
                    val left = ranked.drop(hubs + limit)
                    if (left.isNotEmpty()) {
                        left.forEach { boxOf[it.index] = boxes.size }
                        boxes += MapBox("${left.size} more", folder, null, left.size, folded = false, large = false, rest = true)
                    }
                    continue
                }
                val hub = members.firstOrNull { isHub(it.value) }?.value
                members.forEach { boxOf[it.index] = boxes.size }
                boxes += MapBox(hub?.title ?: folder, folder, hub?.path, members.size, folded = true, large = true)
            }
            val links = sortedSetOf(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
            for ((a, b) in graph.links) {
                val p = boxOf[a] ?: continue
                val q = boxOf[b] ?: continue
                if (p != q) links += minOf(p, q) to maxOf(p, q)
            }
            return FoldedMap(boxes, links.toList())
        }

        /** The index, or a topic's hub: the note named like its folder, directly in it. */
        private fun isHub(note: GraphNote): Boolean =
            note.path == "index.md" || note.path == "${note.folder}/${note.folder}.md"
    }
}
