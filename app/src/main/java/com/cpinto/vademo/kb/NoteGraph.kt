package com.cpinto.vademo.kb

/** One note on the map. [folder] is its top-level folder, or `""` for the root `index.md`. */
data class GraphNote(val name: String, val title: String, val folder: String, val path: String)

/**
 * The notes of a knowledge base and the `[[links]]` between them, for drawing
 * as a map. A link is undirected here: `a → b` and `b → a` are one line.
 * Links to notes that do not exist, and links from a note to itself, are left
 * out. Repository files at the root (README, CLAUDE.md, the backlog) are not
 * notes, as on the browse screen; `index.md` is.
 */
data class NoteGraph(val notes: List<GraphNote>, val links: List<Pair<Int, Int>>) {

    companion object {
        /** The map of the notes [index] holds, which are the ones it shows. */
        fun of(index: NoteIndex): NoteGraph {
            val notes = index.notes.map { note ->
                GraphNote(
                    name = note.name,
                    title = note.title,
                    folder = if ('/' in note.path) note.path.substringBefore('/') else "",
                    path = note.path,
                )
            }
            val byName = notes.withIndex().associate { (i, note) -> note.name to i }
            val links = sortedSetOf(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
            index.notes.forEachIndexed { from, note ->
                for (target in note.links) {
                    val to = byName[target] ?: continue
                    if (to != from) links += minOf(from, to) to maxOf(from, to)
                }
            }
            return NoteGraph(notes, links.toList())
        }
    }
}
