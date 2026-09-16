package com.cpinto.vademo.kb

import java.time.LocalDate

/** The YAML block at the top of a note, reduced to the flat keys the knowledge bases use. */
data class Frontmatter(val fields: Map<String, String>) {
    val title: String? get() = fields["title"]
    val type: String? get() = fields["type"]
    val status: String? get() = fields["status"]
    val created: String? get() = fields["created"]
    val updated: String? get() = fields["updated"]

    /** `tags: [a, b]` or `tags: a, b`. */
    val tags: List<String>
        get() = fields["tags"].orEmpty()
            .trim().removePrefix("[").removeSuffix("]")
            .split(',').map { it.trim().unquote() }.filter { it.isNotEmpty() }
}

data class ParsedNote(val frontmatter: Frontmatter?, val body: String)

object FrontmatterParser {

    private val FIELD = Regex("""^([A-Za-z_][\w-]*):\s*(.*)$""")

    fun parse(text: String): ParsedNote {
        val lines = text.lines()
        if (lines.firstOrNull()?.trimEnd() != "---") return ParsedNote(null, text)
        val end = lines.drop(1).indexOfFirst { it.trimEnd() == "---" }
        if (end < 0) return ParsedNote(null, text)

        val fields = linkedMapOf<String, String>()
        for (line in lines.subList(1, end + 1)) {
            val match = FIELD.matchEntire(line) ?: continue
            fields[match.groupValues[1]] = match.groupValues[2].stripComment().unquote()
        }
        val body = lines.drop(end + 2).joinToString("\n").trimStart('\n')
        return ParsedNote(Frontmatter(fields), body)
    }

    /**
     * Sets `updated:` to [date] when the note has that field. The knowledge-base
     * convention is to refresh it whenever the body changes. Notes without the
     * field are returned unchanged rather than gaining one.
     */
    fun withUpdated(text: String, date: LocalDate): String {
        val lines = text.lines().toMutableList()
        if (lines.firstOrNull()?.trimEnd() != "---") return text
        val end = lines.drop(1).indexOfFirst { it.trimEnd() == "---" }
        if (end < 0) return text
        for (i in 1..end) {
            if (lines[i].startsWith("updated:")) {
                lines[i] = "updated: $date"
                return lines.joinToString("\n")
            }
        }
        return text
    }

    /** Drops a trailing `# comment`, which YAML only treats as one after whitespace. */
    private fun String.stripComment(): String {
        val hash = Regex("""\s#""").find(this) ?: return trim()
        return substring(0, hash.range.first).trim()
    }
}

private fun String.unquote(): String =
    if (length >= 2 && (first() == '"' && last() == '"' || first() == '\'' && last() == '\'')) {
        substring(1, length - 1)
    } else {
        this
    }
