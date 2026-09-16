package com.cpinto.vademo.kb

import java.io.File

data class NoteSummary(
    val file: File,
    val name: String,
    val title: String,
    val type: String?,
    val status: String?,
)

data class Folder(val dir: File, val name: String, val noteCount: Int)

/**
 * A `kb-*` repository on disk, read with the layout from the knowledge-base
 * rule: `index.md` at the root, one folder per topic, note identity = file
 * name without `.md`, unique across the repository.
 */
class KnowledgeBase(val root: File) {

    val name: String get() = root.name

    val index: File? get() = File(root, "index.md").takeIf { it.isFile }

    /** Top-level topic folders, excluding hidden directories and `templates/`. */
    fun folders(): List<Folder> = foldersIn(root)

    fun foldersIn(dir: File): List<Folder> =
        dir.listFiles().orEmpty()
            .filter { it.isDirectory && isVisible(it) }
            .map { Folder(it, it.name, noteFiles(it).size) }
            .sortedBy { it.name }

    /** Notes directly inside [dir]: its hub note first, then by title. */
    fun notesIn(dir: File): List<NoteSummary> {
        val summaries = noteFiles(dir).map(::summary)
        val hub = summaries.filter { it.name == dir.name }
        return hub + (summaries - hub.toSet()).sortedBy { it.title.lowercase() }
    }

    /** Resolves a `[[target]]` link to a note file anywhere in the repository. */
    fun resolve(target: String): File? = allNotes()[target.removeSuffix(".md")]

    fun allNotes(): Map<String, File> =
        root.walkTopDown()
            .onEnter { it == root || isVisible(it) }
            .filter { it.isFile && it.extension == "md" }
            .associateBy { it.nameWithoutExtension }

    fun summary(file: File): NoteSummary {
        val front = FrontmatterParser.parse(file.readText()).frontmatter
        return NoteSummary(
            file = file,
            name = file.nameWithoutExtension,
            title = front?.title ?: file.nameWithoutExtension,
            type = front?.type,
            status = front?.status,
        )
    }

    /** Path of [file] relative to the repository root, with `/` separators. */
    fun relativePath(file: File): String = file.relativeTo(root).invariantSeparatorsPath

    fun fileAt(relativePath: String): File = File(root, relativePath)

    private fun noteFiles(dir: File): List<File> =
        dir.listFiles().orEmpty().filter { it.isFile && it.extension == "md" }

    private fun isVisible(dir: File): Boolean =
        !dir.name.startsWith(".") && dir.name !in HIDDEN_DIRS

    private companion object {
        /** Not notes: templates are never linked or checked. */
        val HIDDEN_DIRS = setOf("templates")
    }
}
