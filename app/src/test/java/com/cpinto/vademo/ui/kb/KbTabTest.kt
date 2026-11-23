package com.cpinto.vademo.ui.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KbTabTest {

    @Test
    fun `a knowledge base opens on the tab it was left on`() {
        assertEquals(KbTab.Notes, firstTab(KbTab.Notes, mainDiagram = "overview"))
        assertEquals(KbTab.Diagrams, firstTab(KbTab.Diagrams, mainDiagram = null))
    }

    @Test
    fun `the first time it opens on its main diagram, else on the map`() {
        assertEquals(KbTab.Diagrams, firstTab(null, mainDiagram = "overview"))
        assertEquals(KbTab.Map, firstTab(null, mainDiagram = null))
    }

    @Test
    fun `a stored tab is read back by name, and anything else is no tab`() {
        KbTab.entries.forEach { assertEquals(it, KbTab.parse(it.name)) }
        assertNull(KbTab.parse("Folders"))
        assertNull(KbTab.parse(null))
    }
}
