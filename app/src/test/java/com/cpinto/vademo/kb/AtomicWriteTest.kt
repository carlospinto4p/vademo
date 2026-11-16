package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class AtomicWriteTest {

    @TempDir
    lateinit var tmp: File

    @Test
    fun `writes a new file and replaces an old one, leaving no temporary file`() {
        val file = File(tmp, "a/b/note.md")
        file.writeAtomically { it.write("first".toByteArray()) }
        file.writeAtomically { it.write("second".toByteArray()) }

        assertEquals("second", file.readText())
        assertEquals(listOf("note.md"), file.parentFile.list()!!.toList())
    }

    @Test
    fun `a write that fails halfway leaves the old content and no temporary file`() {
        val file = File(tmp, "note.md").apply { writeText("old") }

        assertThrows<IOException> {
            file.writeAtomically {
                it.write("half".toByteArray())
                throw IOException("disk full")
            }
        }

        assertEquals("old", file.readText())
        assertEquals(listOf("note.md"), tmp.list()!!.toList())
    }

    @Test
    fun `a knowledge base writes through a folder outside itself`() {
        val root = File(tmp, "kb-x").apply { mkdirs() }
        val kb = KnowledgeBase(root)
        val note = File(root, "ideas/new.md")

        kb.write(note, "text")

        assertEquals("text", note.readText())
        // Only the note itself: a temporary file left in the clone would be pushed by the next sync.
        assertEquals(listOf("ideas/new.md"), root.walkTopDown().filter { it.isFile }.map(kb::relativePath).toList())
        assertTrue(File(tmp, "kb-x.writing").list().isNullOrEmpty())
    }

    @Test
    fun `a knowledge base refuses to write outside itself`() {
        val root = File(tmp, "kb-x").apply { mkdirs() }

        assertThrows<IllegalArgumentException> { KnowledgeBase(root).write(File(tmp, "outside.md"), "text") }
        assertFalse(File(tmp, "outside.md").exists())
    }

    @Test
    fun `a new diagram is written whole`() {
        val root = File(tmp, "kb-x").apply { mkdirs() }

        val name = createDiagram(root, "Flow", Diagram.Direction.LR)

        assertEquals(listOf("$name.json"), File(root, Diagram.FOLDER).list()!!.toList())
        assertEquals("Flow", Diagram.parse(File(root, "${Diagram.FOLDER}/$name.json").readText()).title)
    }
}
