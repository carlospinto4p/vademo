package com.cpinto.vademo.kb

/**
 * One note as the index holds it. [links] are the note names it links to, in
 * order and without `.md`; [body] is the text after the frontmatter, kept so
 * search can read it without opening the file again.
 */
data class IndexedNote(
    val path: String,
    val name: String,
    val title: String,
    val type: String?,
    val status: String?,
    val tags: List<String>,
    val created: String?,
    val updated: String?,
    val headings: List<String>,
    val links: List<String>,
    val unverified: Int,
    val body: String,
)

/** A `[[target]]` in the note [from] that names no note. */
data class UnresolvedLink(val from: String, val target: String)

/**
 * Every note of a knowledge base read in one pass, with what follows from
 * their links: who links to each note, which links lead nowhere, and which
 * notes nothing links to. Built in memory and thrown away, never written to
 * disk (`docs/kb-views-and-queries.md`, "The index").
 *
 * The notes are the ones the map of notes shows: `index.md` and every note
 * in a folder, not the repository files at the root, not `templates/`.
 */
class NoteIndex(val kbName: String, val notes: List<IndexedNote>) {

    private val byName: Map<String, IndexedNote> = notes.associateBy { it.name }

    /** Note name → names of the notes linking to it, each once, in path order. */
    val backlinks: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        for (note in notes) {
            for (target in note.links.distinct()) {
                if (target != note.name && target in byName) getOrPut(target) { mutableListOf() } += note.name
            }
        }
    }

    val unresolved: List<UnresolvedLink> = notes.flatMap { note ->
        note.links.distinct().filter { it !in byName }.map { UnresolvedLink(note.name, it) }
    }

    /**
     * Notes nothing else links to, leaving out `index.md` and hubs as the
     * design says. A hub is `type: hub`, or `<topic>/<topic>.md` in the
     * knowledge-base layout.
     */
    val orphans: List<IndexedNote> = notes.filter { note ->
        note.name != "index" && !isHub(note) && backlinks[note.name].isNullOrEmpty()
    }

    fun note(name: String): IndexedNote? = byName[name.removeSuffix(".md")]

    private fun isHub(note: IndexedNote): Boolean =
        note.type == "hub" || note.path.substringBeforeLast('/', "").substringAfterLast('/') == note.name

    companion object {
        fun of(kb: KnowledgeBase): NoteIndex {
            val notes = kb.allNotes().values
                .filter { it.parentFile != kb.root || it == kb.index }
                .sortedBy { kb.relativePath(it) }
                .map { file ->
                    val parsed = FrontmatterParser.parse(file.readText())
                    val front = parsed.frontmatter
                    IndexedNote(
                        path = kb.relativePath(file),
                        name = file.nameWithoutExtension,
                        title = front?.title ?: file.nameWithoutExtension,
                        type = front?.type,
                        status = front?.status,
                        tags = front?.tags.orEmpty(),
                        created = front?.created,
                        updated = front?.updated,
                        headings = headings(parsed.body),
                        links = WikiLinks.targets(parsed.body).map { it.removeSuffix(".md") },
                        unverified = UNVERIFIED.findAll(parsed.body).count(),
                        body = parsed.body,
                    )
                }
            return NoteIndex(kb.name, notes)
        }

        private val HEADING = Regex("""^#{1,6}\s+(.+?)\s*#*\s*$""")
        private val UNVERIFIED = Regex("""\(unverified\)""")

        /** ATX headings (`## Title`), outside fenced code. */
        private fun headings(body: String): List<String> {
            var inFence = false
            return body.lines().mapNotNull { line ->
                if (line.trimStart().startsWith("```")) inFence = !inFence
                if (inFence) null else HEADING.matchEntire(line)?.groupValues?.get(1)
            }
        }
    }
}
