package com.cpinto.vademo.kb

import java.io.File

/**
 * The last [NoteIndex] built for each knowledge base, so opening a note,
 * a folder, search or the map does not read every note again.
 *
 * Each lookup lists the note files and compares their paths, sizes and
 * modification times with the ones the index was built from; any difference
 * (a sync, a save, a new or deleted note, whoever made it) builds the index
 * again. Listing costs one look at each file's details, not a read.
 */
class NoteIndexCache {

    private data class Stamp(val path: String, val modified: Long, val size: Long)

    private class Entry(val stamps: List<Stamp>, val index: NoteIndex)

    private val entries = mutableMapOf<File, Entry>()

    fun of(kb: KnowledgeBase): NoteIndex {
        val files = NoteIndex.files(kb)
        val stamps = files.map { Stamp(it.path, it.lastModified(), it.length()) }
        val key = kb.root.absoluteFile
        synchronized(entries) {
            entries[key]?.takeIf { it.stamps == stamps }?.let { return it.index }
        }
        // Stamped before reading, so a file changed while being read is read again next time.
        val index = NoteIndex.of(kb, files)
        synchronized(entries) { entries[key] = Entry(stamps, index) }
        return index
    }
}
