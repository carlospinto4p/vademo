package com.cpinto.vademo.kb

/** A search with a name: a query string for [NoteSearch]. */
data class SavedQuery(val name: String, val query: String)

/**
 * The numbers on a knowledge base's Health tab (`docs/kb-views-and-queries.md`,
 * "Views"). Each one is a search, so tapping a number lists the notes it
 * counts. Open questions and the inbox follow the knowledge-base rule: an
 * answered question is `reviewed`, and `inbox/` holds raw captures.
 */
object Health {

    val CHECKS = listOf(
        SavedQuery("Orphans", "is:orphan"),
        SavedQuery("Broken links", "is:broken"),
        SavedQuery("Unverified figures", "is:unverified"),
        SavedQuery("Open questions", "type:question status:draft"),
        SavedQuery("Waiting in the inbox", "folder:inbox"),
        SavedQuery("Duplicate names", "is:duplicate"),
    )

    /** How many notes of [index] each check finds, in [CHECKS] order. */
    fun counts(index: NoteIndex): List<Pair<SavedQuery, Int>> =
        CHECKS.map { it to NoteSearch.search(listOf(index), it.query).size }
}
