package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WikiLinksTest {

    @Test
    fun `plain link uses the note name as text`() {
        assertEquals("See [ideas](vademo-note:ideas).", WikiLinks.toMarkdown("See [[ideas]]."))
    }

    @Test
    fun `piped link uses the text after the pipe`() {
        assertEquals(
            "[the scorecard](vademo-note:business-idea-scorecard)",
            WikiLinks.toMarkdown("[[business-idea-scorecard|the scorecard]]"),
        )
    }

    @Test
    fun `heading anchor is dropped from the target`() {
        assertEquals("[tyfold](vademo-note:tyfold)", WikiLinks.toMarkdown("[[tyfold#Scorecard]]"))
    }

    @Test
    fun `several links on one line are all rewritten`() {
        assertEquals(
            "[a](vademo-note:a) [b](vademo-note:b)",
            WikiLinks.toMarkdown("[[a]] [[b]]"),
        )
    }

    @Test
    fun `links inside inline code are left alone`() {
        val text = "Write `[[file-name]]` to link, like [[ideas]]."
        assertEquals(
            "Write `[[file-name]]` to link, like [ideas](vademo-note:ideas).",
            WikiLinks.toMarkdown(text),
        )
    }

    @Test
    fun `links inside fenced code blocks are left alone`() {
        val text = "```\n[[inside]]\n```\n[[outside]]"
        assertEquals("```\n[[inside]]\n```\n[outside](vademo-note:outside)", WikiLinks.toMarkdown(text))
    }

    @Test
    fun `targets lists linked note names without anchors or labels`() {
        assertEquals(listOf("a", "b", "c"), WikiLinks.targets("[[a]] then [[b|shown]]\nand [[c#Heading]]"))
    }

    @Test
    fun `targets skip links quoted as code`() {
        val text = "Write `[[file-name]]`.\n```\n[[inside]]\n```\n[[outside]]"
        assertEquals(listOf("outside"), WikiLinks.targets(text))
    }

    @Test
    fun `targetOf decodes the note name`() {
        assertEquals("src-some report", WikiLinks.targetOf("vademo-note:src-some+report"))
    }

    @Test
    fun `targetOf ignores ordinary urls`() {
        assertNull(WikiLinks.targetOf("https://github.com"))
    }
}
