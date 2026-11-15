package com.cpinto.vademo

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cpinto.vademo.kb.WikiLinks
import com.cpinto.vademo.ui.note.NoteTable
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A long link name such as `how-many-developers-run-many-coding-agents` wraps
 * at its dashes, in a paragraph and in a table cell, never inside a word.
 * Android does not break after a dash on its own, so it used to show
 * "develop" on one line and "ers" on the next.
 */
@RunWith(AndroidJUnit4::class)
class LinkLineBreakTest {

    @get:Rule
    val compose = createComposeRule()

    private val name = "how-many-developers-run-many-coding-agents"

    @Test
    fun longLinkNamesWrapAtTheirDashes() {
        val note = WikiLinks.toMarkdown(
            "The scorecard is in [[$name]] today.\n\n" +
                "| Criterion | Evidence |\n|---|---|\n| Demand | [[$name]] |\n",
        )
        compose.setContent {
            Markdown(
                content = note,
                components = markdownComponents(table = { NoteTable(it) }),
                modifier = Modifier.width(200.dp),
            )
        }
        compose.waitForIdle()

        val layouts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.firstOrNull()
            }
            .filter { name in it.layoutInput.text.text.replace("​", "") }
        assertTrue("expected the paragraph and the table cell, found ${layouts.size}", layouts.size >= 2)

        for (layout in layouts) {
            val text = layout.layoutInput.text.text
            val lines = (0 until layout.lineCount).map { text.substring(layout.getLineStart(it), layout.getLineEnd(it)) }
            assertTrue("the name should need more than one line: $lines", lines.size > 1)
            for (line in lines.dropLast(1)) {
                val end = line.trimEnd(' ', '​')
                assertTrue("line ends inside a word: $lines", end.endsWith('-') || line.endsWith(' '))
            }
        }
    }
}
