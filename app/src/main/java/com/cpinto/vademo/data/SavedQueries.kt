package com.cpinto.vademo.data

import com.cpinto.vademo.kb.NoteSearch
import com.cpinto.vademo.kb.SavedQuery

/**
 * The searches the user saved, kept in the app's settings, never in a
 * knowledge base: sync pushes everything inside a clone.
 *
 * Stored as one line per search, `name<TAB>query`, in the order saved.
 */
object SavedQueries {

    fun decode(text: String?): List<SavedQuery> =
        text.orEmpty().lines().mapNotNull { line ->
            val name = line.substringBefore('\t', "")
            val query = line.substringAfter('\t', "")
            if (name.isBlank() || query.isBlank()) null else SavedQuery(name, query)
        }

    fun encode(queries: List<SavedQuery>): String =
        queries.joinToString("\n") { "${it.name}\t${it.query}" }

    /**
     * [queries] with [name] saved as [query]. A search with the same name, in
     * any case, is replaced where it stands; otherwise the new one goes last.
     * Tabs and line breaks become spaces, since they separate the stored fields.
     */
    fun put(queries: List<SavedQuery>, name: String, query: String): List<SavedQuery> {
        val saved = SavedQuery(oneLine(name), oneLine(query))
        if (saved.name.isEmpty() || saved.query.isEmpty()) return queries
        val at = queries.indexOfFirst { it.name.equals(saved.name, ignoreCase = true) }
        return if (at < 0) queries + saved else queries.toMutableList().apply { set(at, saved) }
    }

    fun remove(queries: List<SavedQuery>, name: String): List<SavedQuery> = queries.filterNot { it.name == name }

    /**
     * The text to search for when [saved] is picked in a search that started
     * as [start]. A search opened from a knowledge base starts with its `kb:`
     * filter; that filter is kept unless the saved search names a knowledge
     * base of its own.
     */
    fun applied(saved: SavedQuery, start: String): String {
        if (NoteSearch.parse(saved.query).filters.any { it is NoteSearch.Filter.Kb }) return saved.query
        val scope = start.split(Regex("""\s+""")).filter { it.startsWith("kb:", ignoreCase = true) && it.length > 3 }
        return (scope + saved.query).joinToString(" ")
    }

    private fun oneLine(text: String) = text.replace(Regex("""[\t\r\n]+"""), " ").trim()
}
