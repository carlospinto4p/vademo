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

    val index: File? get() = File(root, "index.md").takeIf { it.isFile && isInside(root, it) }

    /** Top-level topic folders, excluding hidden directories and `templates/`. */
    fun folders(): List<Folder> = foldersIn(root)

    fun foldersIn(dir: File): List<Folder> =
        dir.listFiles().orEmpty()
            .filter { it.isDirectory && isVisible(it) && isInside(root, it) }
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

    /**
     * The note a diagram box's `ref` path names: a path from the repository
     * root, or a bare note name found in any folder, as tyfold finds a bare
     * file name anywhere in the project. Null when it names no note.
     *
     * A bare name is looked up in [notes], so resolving many refs can walk
     * the repository once and pass the result in.
     */
    fun noteAt(path: String, notes: () -> Map<String, File> = ::allNotes): File? {
        val direct = File(root, path.removePrefix("./"))
        if (direct.isFile && direct.extension == "md" && isInside(root, direct)) return direct
        return if ('/' in path) null else notes()[path.removeSuffix(".md")]
    }

    fun allNotes(): Map<String, File> =
        root.walkTopDown()
            .onEnter { it == root || (isVisible(it) && isInside(root, it)) }
            .filter { it.isFile && it.extension == "md" && isInside(root, it) }
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

    /**
     * The file at [relativePath]. Paths come from this class's own listings,
     * so one that leads outside the repository is a bug, not a missing note.
     */
    fun fileAt(relativePath: String): File = File(root, relativePath).also {
        require(isInside(root, it)) { "$relativePath is outside ${root.name}" }
    }

    /**
     * The file of the diagram called [name] in `.tyfold-graphs/`, or null when
     * [name] is not a plain file name. Names come from notes, which come from
     * GitHub, so a `../` in one must not reach a file outside that folder.
     */
    fun diagramFile(name: String): File? =
        if (name.isBlank() || '/' in name || '\\' in name || '\u0000' in name) null
        else File(File(root, Diagram.FOLDER), "$name.json").takeIf { isInside(root, it) }

    /** The note templates in `templates/`, by name. */
    fun templates(): List<File> =
        noteFiles(File(root, NoteTemplate.FOLDER)).sortedBy { it.name }

    /** The hub note's name for [dir], `<topic>/<topic>.md`, or null when it has none. */
    fun hubOf(dir: File): String? = dir.name.takeIf { File(dir, "${dir.name}.md").isFile }

    private fun noteFiles(dir: File): List<File> =
        dir.listFiles().orEmpty().filter { it.isFile && it.extension == "md" && isInside(root, it) }

    private fun isVisible(dir: File): Boolean =
        !dir.name.startsWith(".") && dir.name !in HIDDEN_DIRS

    private companion object {
        /** Not notes: templates are never linked or checked. */
        val HIDDEN_DIRS = setOf("templates")
    }
}

/**
 * Whether [file], once its symlinks are followed, is inside [root]. A clone
 * should hold no symlinks (`GitSync` turns them off), but one made before
 * that could, and following it would read or write the app's private files.
 */
fun isInside(root: File, file: File): Boolean =
    file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
