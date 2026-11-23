package com.cpinto.vademo.kb

/**
 * One note on the map. [folder] is its top-level folder, or `""` for the root
 * `index.md`; [type] is its frontmatter `type`, if any.
 */
data class GraphNote(val name: String, val title: String, val folder: String, val path: String, val type: String? = null) {
    /** A source note: in `sources/`, or of type `source`. The map can leave these out. */
    val isSource: Boolean get() = folder == SOURCES || type == SOURCES.removeSuffix("s")

    private companion object {
        const val SOURCES = "sources"
    }
}

/**
 * The notes of a knowledge base and the `[[links]]` between them, for drawing
 * as a map. A link is undirected here: `a → b` and `b → a` are one line.
 * Links to notes that do not exist, and links from a note to itself, are left
 * out. Repository files at the root (README, CLAUDE.md, the backlog) are not
 * notes, as on the browse screen; `index.md` is.
 */
data class NoteGraph(val notes: List<GraphNote>, val links: List<Pair<Int, Int>>) {

    /**
     * The notes at most [steps] links away from the note at [path], it
     * included, and the links among them; empty when no note is at [path].
     */
    fun around(path: String, steps: Int = 2): NoteGraph {
        val start = notes.indexOfFirst { it.path == path }
        if (start < 0) return NoteGraph(emptyList(), emptyList())
        val neighbours = List(notes.size) { mutableListOf<Int>() }
        for ((a, b) in links) {
            neighbours[a] += b
            neighbours[b] += a
        }
        val reached = sortedSetOf(start)
        var edge = listOf(start)
        repeat(steps) {
            edge = edge.flatMap { neighbours[it] }.filter { reached.add(it) }
        }
        val kept = reached.toList()
        val at = kept.withIndex().associate { (i, note) -> note to i }
        return NoteGraph(
            kept.map { notes[it] },
            links.mapNotNull { (a, b) -> at[a]?.let { p -> at[b]?.let { q -> p to q } } },
        )
    }

    companion object {
        /** The map of the notes [index] holds, which are the ones it shows. */
        fun of(index: NoteIndex): NoteGraph {
            val notes = index.notes.map { note ->
                GraphNote(
                    name = note.name,
                    title = note.title,
                    folder = if ('/' in note.path) note.path.substringBefore('/') else "",
                    path = note.path,
                    type = note.type,
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
