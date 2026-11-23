package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MainDiagramTest {

    @Test
    fun `the first diagram the index embeds is the main one`() {
        val index = "# Business\n\n![[overview]]\n\nLater: ![[scoring]]\n"
        assertEquals("overview", mainDiagram(index, listOf("scoring", "overview", "costs")))
    }

    @Test
    fun `an embed of a diagram that cannot be read is passed over`() {
        val index = "![[broken]]\n\n![[scoring]]\n"
        assertEquals("scoring", mainDiagram(index, listOf("costs", "scoring")))
    }

    @Test
    fun `with no embed in the index the only diagram is the main one`() {
        assertEquals("scoring", mainDiagram("# Business\n", listOf("scoring")))
        assertEquals("scoring", mainDiagram(null, listOf("scoring")))
    }

    @Test
    fun `several diagrams and none embedded, or none at all, give no main diagram`() {
        assertNull(mainDiagram("# Business\n", listOf("costs", "scoring")))
        assertNull(mainDiagram("![[overview]]", emptyList()))
    }
}
