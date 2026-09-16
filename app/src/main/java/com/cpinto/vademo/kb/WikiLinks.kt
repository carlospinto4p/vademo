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
 */
object WikiLinks {

    const val SCHEME = "vademo-note"

    private val LINK = Regex("""\[\[([^\]|#\n]+)(?:#[^\]|\n]*)?(?:\|([^\]\n]+))?]]""")

    fun toMarkdown(text: String): String {
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
                out.append(rewriteOutsideInlineCode(line))
            }
        }
        return out.toString()
    }

    fun targetOf(uri: String): String? {
        val prefix = "$SCHEME:"
        if (!uri.startsWith(prefix)) return null
        return URLDecoder.decode(uri.removePrefix(prefix), "UTF-8")
    }

    private fun rewriteOutsideInlineCode(line: String): String {
        // Split on backticks: even-indexed segments are prose, odd ones code.
        val segments = line.split('`')
        return segments.mapIndexed { i, segment ->
            if (i % 2 == 1 && i < segments.lastIndex) segment else rewrite(segment)
        }.joinToString("`")
    }

    private fun rewrite(prose: String): String = LINK.replace(prose) { match ->
        val target = match.groupValues[1].trim()
        val label = match.groupValues[2].ifBlank { target }.trim()
        val encoded = URLEncoder.encode(target, "UTF-8")
        "[${label.replace("[", "\\[").replace("]", "\\]")}]($SCHEME:$encoded)"
    }
}
