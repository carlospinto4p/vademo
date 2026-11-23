package com.cpinto.vademo.kb

/**
 * One box on a [FoldedMap]: a note, or a whole folder folded into one box.
 *
 * [path] is the note a tap opens: the note itself, or for a folded folder its
 * hub (`<folder>/<folder>.md`), and null for a folded folder with no hub.
 * [notes] is how many notes the box stands for. [large] boxes are the index,
 * hubs and folded folders: the knowledge base's shape.
 */
data class MapBox(
    val title: String,
    val folder: String,
    val path: String?,
    val notes: Int,
    val folded: Boolean,
    val large: Boolean,
)

/**
 * The map of notes drawn as the knowledge base's shape: `index.md` and one
 * box per topic folder, linked where their notes link, with only the folders
 * in [FoldedMap.of]'s `open` drawn note by note. A knowledge base of a few
 * hundred notes is a hairball when every note is a box; folded, it is a
 * dozen boxes that each lead on.
 */
data class FoldedMap(val boxes: List<MapBox>, val links: List<Pair<Int, Int>>) {

    companion object {
        /**
         * [graph] with every folder folded but those in [open]. Source notes
         * are left out unless [sources]; a link to a left-out note is too.
         */
        fun of(graph: NoteGraph, open: Set<String>, sources: Boolean): FoldedMap {
            val boxes = mutableListOf<MapBox>()
            // Note index in graph → box index, for the notes shown.
            val boxOf = arrayOfNulls<Int>(graph.notes.size)
            val shown = graph.notes.withIndex().filter { (_, note) -> sources || !note.isSource }
            val folderBox = mutableMapOf<String, Int>()
            for ((folder, members) in shown.groupBy { it.value.folder }) {
                if (folder.isEmpty() || folder in open) {
                    for ((i, note) in members) {
                        boxOf[i] = boxes.size
                        boxes += MapBox(note.title, folder, note.path, 1, folded = false, large = isHub(note))
                    }
                    continue
                }
                val hub = members.firstOrNull { isHub(it.value) }?.value
                folderBox[folder] = boxes.size
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
            note.folder.isEmpty() || note.path == "${note.folder}/${note.folder}.md"
    }
}
