package com.cpinto.vademo.kb

import java.io.File

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
) {
    /** Lowercased once, the first time a search reads them, rather than on every keystroke. */
    val titleLower: String by lazy { title.lowercase() }
    val headingsLower: List<String> by lazy { headings.map { it.lowercase() } }
    val bodyLower: String by lazy { body.lowercase() }
}

/** A `[[target]]` in the note [from] that names no note. */
data class UnresolvedLink(val from: String, val target: String)

/**
 * Every note of a knowledge base read in one pass, with what follows from
 * their links: who links to each note, which links lead nowhere, and which
 * notes nothing links to. Held in memory by [NoteIndexCache], never written
 * to disk (`docs/kb-views-and-queries.md`, "The index").
 *
 * The notes are the ones the map of notes shows: `index.md` and every note
 * in a folder, not the repository files at the root, not `templates/`.
 */
class NoteIndex(val kbName: String, val notes: List<IndexedNote>) {

    private val byName: Map<String, IndexedNote> = notes.associateBy { it.name }
    private val byPath: Map<String, IndexedNote> by lazy { notes.associateBy { it.path } }

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

    /** Names of the [orphans], built once for search's `is:orphan`. */
    val orphanNames: Set<String> by lazy { orphans.mapTo(HashSet()) { it.name } }

    /**
     * Names held by more than one note file. The knowledge-base layout keeps
     * names unique, so a `[[link]]` to one of these opens either file.
     */
    val duplicateNames: Set<String> by lazy {
        notes.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    }

    /** Note name → the notes it links to that do not exist, built once for search. */
    val missingFrom: Map<String, List<String>> by lazy { unresolved.groupBy({ it.from }, { it.target }) }

    fun note(name: String): IndexedNote? = byName[name.removeSuffix(".md")]

    /** The notes linking to the note at [path], in path order. */
    fun linkedFrom(path: String): List<IndexedNote> =
        backlinks[path.substringAfterLast('/').removeSuffix(".md")].orEmpty().mapNotNull(byName::get)

    /**
     * The notes the note at [path] links to, each once, in the order first
     * linked; links to itself or to no note are left out.
     */
    fun linksTo(path: String): List<IndexedNote> {
        val note = byPath[path] ?: return emptyList()
        return note.links.distinct().filter { it != note.name }.mapNotNull(byName::get)
    }

    /**
     * The hub the note at [path] sits under: its topic's
     * `<topic>/<topic>.md`, or for that hub `index.md`. Null for `index.md`
     * itself, and when the knowledge base has no such note.
     */
    fun hubOf(path: String): IndexedNote? {
        if ('/' !in path) return null
        val topic = path.substringBefore('/')
        val hub = "$topic/$topic.md"
        return byPath[if (path == hub) "index.md" else hub]
    }

    /** Newest `updated:` first; notes without one last, by title. */
    fun recent(): List<IndexedNote> = notes.sortedWith(
        compareBy<IndexedNote> { it.updated == null }
            .thenByDescending { it.updated.orEmpty() }
            .thenBy { it.title.lowercase() },
    )

    /** Notes grouped by `type:`, largest group first; untyped notes last, under `null`. */
    fun byType(): List<Pair<String?, List<IndexedNote>>> = notes
        .groupBy { it.type }
        .map { (type, group) -> type to group.sortedBy { it.title.lowercase() } }
        .sortedWith(compareBy<Pair<String?, List<IndexedNote>>> { it.first == null }
            .thenByDescending { it.second.size }
            .thenBy { it.first })

    /** Every tag with the number of notes carrying it, most used first. */
    fun tagCounts(): List<Pair<String, Int>> = notes
        .flatMap { it.tags.distinct() }
        .groupingBy { it }.eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    private fun isHub(note: IndexedNote): Boolean =
        note.type == "hub" || note.path.substringBeforeLast('/', "").substringAfterLast('/') == note.name

    companion object {
        /**
         * The files the index reads, by path: `index.md` and every note in a
         * folder, both files of a name two folders hold included.
         */
        fun files(kb: KnowledgeBase): List<File> = kb.noteFiles()
            .filter { it.parentFile != kb.root || it == kb.index }
            .sortedBy { kb.relativePath(it) }

        fun of(kb: KnowledgeBase, files: List<File> = files(kb)): NoteIndex {
            val notes = files
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
