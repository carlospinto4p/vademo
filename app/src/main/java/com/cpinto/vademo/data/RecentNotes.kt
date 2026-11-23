package com.cpinto.vademo.data

/**
 * The notes last opened in a knowledge base, newest first, for its
 * "Continue reading" strip. Kept in the app's settings, never in the
 * knowledge base: sync pushes everything inside a clone.
 *
 * Stored as one line per note path.
 */
object RecentNotes {

    /** How many are kept per knowledge base. */
    const val KEEP = 5

    fun decode(text: String?): List<String> = text.orEmpty().lines().filter { it.isNotBlank() }

    fun encode(paths: List<String>): String = paths.joinToString("\n")

    /** [paths] after opening the note at [path]: it moves to the front, and the oldest beyond [KEEP] drop off. */
    fun opened(paths: List<String>, path: String): List<String> = (listOf(path) + paths.filter { it != path }).take(KEEP)
}
