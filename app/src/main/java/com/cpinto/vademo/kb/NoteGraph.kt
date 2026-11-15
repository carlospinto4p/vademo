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
        fun of(kb: KnowledgeBase): NoteGraph {
            val files = kb.allNotes().values
                .filter { it.parentFile != kb.root || it == kb.index }
                .sortedBy { kb.relativePath(it) }
            val parsed = files.map { FrontmatterParser.parse(it.readText()) }
            val notes = files.mapIndexed { i, file ->
                val path = kb.relativePath(file)
                GraphNote(
                    name = file.nameWithoutExtension,
                    title = parsed[i].frontmatter?.title ?: file.nameWithoutExtension,
                    folder = if ('/' in path) path.substringBefore('/') else "",
                    path = path,
                )
            }
            val byName = notes.withIndex().associate { (i, note) -> note.name to i }
            val links = sortedSetOf(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
            parsed.forEachIndexed { from, note ->
                for (target in WikiLinks.targets(note.body)) {
                    val to = byName[target.removeSuffix(".md")] ?: continue
                    if (to != from) links += minOf(from, to) to maxOf(from, to)
                }
            }
            return NoteGraph(notes, links.toList())
        }
    }
}
