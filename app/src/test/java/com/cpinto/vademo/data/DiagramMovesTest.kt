package com.cpinto.vademo.data

import com.cpinto.vademo.kb.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DiagramMovesTest {

    @TempDir
    lateinit var root: File

    @Test
    fun `places are kept per knowledge base and diagram, and forgotten on request`() {
        val store = DiagramMoves(root)
        val moves = mapOf("api" to Position(12.5, -40.0))
        store.write("kb-a", "flow", moves)
        assertEquals(moves, store.read("kb-a", "flow"))
        assertEquals(emptyMap<String, Position>(), store.read("kb-b", "flow"))
        store.forget("kb-a", "flow")
        assertEquals(emptyMap<String, Position>(), store.read("kb-a", "flow"))
    }

    @Test
    fun `no places leaves no file behind, and a broken file reads as none`() {
        val store = DiagramMoves(root)
        store.write("kb-a", "flow", mapOf("api" to Position(1.0, 2.0)))
        store.write("kb-a", "flow", emptyMap())
        assertFalse(File(root, "kb-a/flow.json").exists())
        File(root, "kb-a/flow.json").writeText("{ not json")
        assertEquals(emptyMap<String, Position>(), store.read("kb-a", "flow"))
    }
}
