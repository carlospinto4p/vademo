package com.cpinto.vademo.kb

/**
 * A note that matched a search, in the knowledge base [kb]. [missing] are the
 * notes it links to that do not exist, so a list of broken links can name them.
 */
data class SearchHit(val kb: String, val note: IndexedNote, val score: Int, val missing: List<String> = emptyList())

/**
 * Search over [NoteIndex]es, as `docs/kb-views-and-queries.md` ("Queries")
 * designs it. A query is words and filters separated by spaces, all of which
 * must hold:
 *
 * - `type:`, `status:`, `tag:` match the frontmatter, ignoring case.
 * - `updated:2026-09-01`, `updated:2026-09` (a prefix), or with `>`, `>=`,
 *   `<`, `<=` in front, compared as ISO dates. Notes without `updated:` fail.
 * - `kb:` names a knowledge base, with or without its `kb-` prefix.
 * - `links:quorum` keeps the notes linking to `quorum`.
 * - `folder:inbox` keeps the notes inside that folder, at any depth.
 * - `is:orphan` (nothing links to it), `is:unverified` (has the marker),
 *   `is:broken` (links to a note that does not exist).
 *
 * Anything else is a word to find in the title, a heading or the body.
 * Matches rank by where the words are, title first, then headings, then
 * body; ties go to the most recently updated note, then by title.
 */
object NoteSearch {

    sealed interface Filter {
        data class Type(val value: String) : Filter
        data class Status(val value: String) : Filter
        data class Tag(val value: String) : Filter
        data class Updated(val op: String, val date: String) : Filter
        data class Kb(val name: String) : Filter
        data class Links(val target: String) : Filter
        data class InFolder(val path: String) : Filter
        data class Is(val what: String) : Filter
    }

    data class Query(val words: List<String>, val filters: List<Filter>) {
        val isEmpty: Boolean get() = words.isEmpty() && filters.isEmpty()

        /** Nothing asked beyond which knowledge base, so the search screen offers saved searches instead. */
        val asksNothing: Boolean get() = words.isEmpty() && filters.all { it is Filter.Kb }
    }

    private val UPDATED = Regex("""^(>=|<=|>|<)?(.+)$""")
    private val SPACES = Regex("""\s+""")

    fun parse(text: String): Query {
        val words = mutableListOf<String>()
        val filters = mutableListOf<Filter>()
        for (token in text.split(SPACES).filter { it.isNotEmpty() }) {
            val key = token.substringBefore(':', "").lowercase()
            val value = token.substringAfter(':', "")
            val filter = if (value.isEmpty()) null else when (key) {
                "type" -> Filter.Type(value)
                "status" -> Filter.Status(value)
                "tag" -> Filter.Tag(value)
                "updated" -> UPDATED.matchEntire(value)!!.let {
                    Filter.Updated(it.groupValues[1], it.groupValues[2])
                }
                "kb" -> Filter.Kb(value)
                "links" -> Filter.Links(value.removeSuffix(".md"))
                "folder" -> Filter.InFolder(value.trim('/'))
                "is" -> Filter.Is(value.lowercase())
                else -> null
            }
            if (filter != null) filters += filter else words += token.lowercase()
        }
        return Query(words, filters)
    }

    /** Notes of [indexes] matching [text], best first. An empty query finds nothing. */
    fun search(indexes: List<NoteIndex>, text: String): List<SearchHit> {
        val query = parse(text)
        if (query.isEmpty) return emptyList()
        return indexes.flatMap { index ->
            index.notes.mapNotNull { note ->
                val noteMissing = index.missingFrom[note.name].orEmpty()
                if (!query.filters.all { matches(it, index, note, noteMissing) }) return@mapNotNull null
                score(note, query.words)?.let { SearchHit(index.kbName, note, it, noteMissing) }
            }
        }.sortedWith(
            compareByDescending<SearchHit> { it.score }
                .thenByDescending { it.note.updated.orEmpty() }
                .thenBy { it.note.title.lowercase() },
        )
    }

    private fun matches(
        filter: Filter,
        index: NoteIndex,
        note: IndexedNote,
        missing: List<String>,
    ): Boolean =
        when (filter) {
            is Filter.Type -> note.type.equals(filter.value, ignoreCase = true)
            is Filter.Status -> note.status.equals(filter.value, ignoreCase = true)
            is Filter.Tag -> note.tags.any { it.equals(filter.value, ignoreCase = true) }
            is Filter.Updated -> note.updated?.let { updated ->
                when (filter.op) {
                    ">" -> updated > filter.date
                    ">=" -> updated >= filter.date
                    "<" -> updated < filter.date
                    "<=" -> updated <= filter.date
                    else -> updated.startsWith(filter.date)
                }
            } ?: false
            is Filter.Kb -> index.kbName.equals(filter.name, ignoreCase = true) ||
                index.kbName.equals("kb-" + filter.name, ignoreCase = true)
            is Filter.Links -> filter.target in note.links
            is Filter.InFolder -> note.path.startsWith(filter.path + "/", ignoreCase = true)
            is Filter.Is -> when (filter.what) {
                "orphan" -> note.name in index.orphanNames
                "unverified" -> note.unverified > 0
                "broken" -> missing.isNotEmpty()
                else -> false
            }
        }

    /**
     * Null when a word is missing; otherwise 100 per word in the title, 10 in
     * a heading, 1 in the body. [parse] lowercases the words, so they are
     * looked for in the note's lowercased text.
     */
    private fun score(note: IndexedNote, words: List<String>): Int? {
        var total = 0
        for (word in words) {
            var points = 0
            if (word in note.titleLower) points += 100
            if (note.headingsLower.any { word in it }) points += 10
            if (word in note.bodyLower) points += 1
            if (points == 0) return null
            total += points
        }
        return total
    }
}
