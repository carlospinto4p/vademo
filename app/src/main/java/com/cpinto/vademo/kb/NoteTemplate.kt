package com.cpinto.vademo.kb

import java.text.Normalizer
import java.time.LocalDate

/**
 * Starting a note from one of a knowledge base's `templates/`.
 *
 * Only what the app knows is filled in: the title, today's date as
 * `created` and `updated`, the `# heading`, and the `Hub: [[<topic-hub>]]`
 * line when the folder has a hub note. Every other placeholder is left for
 * the person writing the note.
 */
object NoteTemplate {

    const val FOLDER = "templates"

    /** The placeholder the templates use for the folder's hub note. */
    private const val HUB_PLACEHOLDER = "[[<topic-hub>]]"

    fun fill(template: String, title: String, hub: String?, date: LocalDate): String {
        val lines = template.lines().toMutableList()
        val end = if (lines.firstOrNull()?.trimEnd() == "---") {
            lines.drop(1).indexOfFirst { it.trimEnd() == "---" }.let { if (it < 0) 0 else it + 1 }
        } else {
            0
        }
        for (i in 1 until end) {
            val line = lines[i]
            when {
                line.startsWith("title:") -> lines[i] = "title: ${yamlString(title)}"
                line.startsWith("created:") -> lines[i] = "created: $date"
                line.startsWith("updated:") -> lines[i] = "updated: $date"
            }
        }
        val bodyStart = if (end == 0) 0 else end + 1
        val heading = (bodyStart until lines.size).firstOrNull { lines[it].startsWith("# ") }
        if (heading != null) lines[heading] = "# $title"
        var text = lines.joinToString("\n")
        if (hub != null) text = text.replace(HUB_PLACEHOLDER, "[[$hub]]")
        return text
    }

    /** The kebab-case file name, without `.md`, the knowledge bases give a note with this [title]. */
    fun fileName(title: String): String =
        Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    /** True for a name the knowledge-base convention accepts: kebab-case letters and digits. */
    fun isValidName(name: String): Boolean = Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(name)

    /** [value] as a YAML scalar, quoted only when plain text would be read as something else. */
    private fun yamlString(value: String): String {
        val plain = value.isNotEmpty() &&
            value.first() !in "-?:,[]{}#&*!|>'\"%@`" &&
            ": " !in value && " #" !in value && !value.endsWith(":")
        return when {
            plain -> value
            '"' !in value -> "\"$value\""
            else -> "'" + value.replace("'", "''") + "'"
        }
    }
}
