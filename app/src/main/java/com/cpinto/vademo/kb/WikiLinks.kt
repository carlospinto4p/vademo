package com.cpinto.vademo.kb

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * `[[note-name]]` and `[[note-name|text shown]]` links between notes.
 *
 * Standard markdown renderers do not know this syntax, so before rendering a
 * note its wiki links are rewritten into ordinary links on the [SCHEME]
 * scheme, which the app intercepts. Links inside inline code or fenced code
 * blocks are left alone, because notes and rules quote the syntax itself.
 *
 * `![[name]]` shows the diagram `.tyfold-graphs/<name>.json` in the note, as
 * the knowledge bases' rules say ("Diagrams" in `.claude/rules/knowledge-base.md`).
 * It is not a link to a note. On a line of its own it becomes a [NotePart.Embed]
 * (see [parts]); inside a sentence it becomes a link on [DIAGRAM_SCHEME].
 */
object WikiLinks {

    const val SCHEME = "vademo-note"
    const val DIAGRAM_SCHEME = "vademo-diagram"

    private val LINK = Regex("""(?<!!)\[\[([^\]|#\n]+)(?:#[^\]|\n]*)?(?:\|([^\]\n]+))?]]""")
    private val EMBED = Regex("""!\[\[([^\]|#\n]+)(?:\|([^\]\n]+))?]]""")

    /** A note's body cut at the lines that only embed a diagram. */
    sealed interface NotePart {
        data class Markdown(val text: String) : NotePart
        data class Embed(val name: String) : NotePart
    }

    fun parts(text: String): List<NotePart> {
        val out = mutableListOf<NotePart>()
        val run = mutableListOf<String>()
        fun flush() {
            if (run.joinToString("").isNotBlank()) out += NotePart.Markdown(run.joinToString("\n"))
            run.clear()
        }
        var inFence = false
        for (line in text.lines()) {
            if (line.trimStart().startsWith("```")) inFence = !inFence
            val embed = if (inFence) null else EMBED.matchEntire(line.trim())
            if (embed != null) {
                flush()
                out += NotePart.Embed(embed.groupValues[1].trim())
            } else {
                run += line
            }
        }
        flush()
        return out
    }

    fun toMarkdown(text: String): String = mapProse(text, ::rewrite)

    /** Note names that [text] links to, in order, without anchors; code is skipped like in [toMarkdown]. */
    fun targets(text: String): List<String> {
        val found = mutableListOf<String>()
        mapProse(text) { prose ->
            LINK.findAll(prose).forEach { found += it.groupValues[1].trim() }
            prose
        }
        return found
    }

    /** Applies [transform] to every part of [text] outside fenced and inline code. */
    private fun mapProse(text: String, transform: (String) -> String): String {
        val out = StringBuilder()
        var inFence = false
        text.lines().forEachIndexed { index, line ->
            if (index > 0) out.append('\n')
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                out.append(line)
            } else if (inFence) {
                out.append(line)
            } else {
                out.append(mapOutsideInlineCode(line, transform))
            }
        }
        return out.toString()
    }

    fun targetOf(uri: String): String? = decode(uri, SCHEME)

    /** The diagram name of a link made from an `![[name]]` inside a sentence. */
    fun diagramOf(uri: String): String? = decode(uri, DIAGRAM_SCHEME)

    private fun decode(uri: String, scheme: String): String? {
        val prefix = "$scheme:"
        if (!uri.startsWith(prefix)) return null
        return URLDecoder.decode(uri.removePrefix(prefix), "UTF-8")
    }

    private fun mapOutsideInlineCode(line: String, transform: (String) -> String): String {
        // Split on backticks: even-indexed segments are prose, odd ones code.
        val segments = line.split('`')
        return segments.mapIndexed { i, segment ->
            if (i % 2 == 1 && i < segments.lastIndex) segment else transform(segment)
        }.joinToString("`")
    }

    private fun rewrite(prose: String): String {
        val embedsDone = EMBED.replace(prose) { link(it, DIAGRAM_SCHEME) }
        return LINK.replace(embedsDone) { link(it, SCHEME) }
    }

    private fun link(match: MatchResult, scheme: String): String {
        val target = match.groupValues[1].trim()
        val label = match.groupValues[2].ifBlank { target }.trim()
        val encoded = URLEncoder.encode(target, "UTF-8")
        return "[${label.replace("[", "\\[").replace("]", "\\]")}]($scheme:$encoded)"
    }
}
