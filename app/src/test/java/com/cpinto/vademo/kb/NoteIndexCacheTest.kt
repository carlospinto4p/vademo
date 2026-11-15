package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NoteIndexCacheTest {

    @TempDir
    lateinit var root: File

    private val kb get() = KnowledgeBase(root)

    private fun note(path: String, body: String) =
        File(root, path).apply { parentFile!!.mkdirs() }.writeText(body)

    @Test
    fun `an unchanged knowledge base is not read again`() {
        note("ideas/a.md", "[[b]]")
        note("ideas/b.md", "")
        val cache = NoteIndexCache()
        assertSame(cache.of(kb), cache.of(kb))
    }

    @Test
    fun `an edited note is read again, even at the same size`() {
        note("ideas/a.md", "[[b]]")
        note("ideas/b.md", "")
        note("ideas/c.md", "")
        val cache = NoteIndexCache()
        val before = cache.of(kb)
        val a = File(root, "ideas/a.md")
        a.writeText("[[c]]")
        a.setLastModified(a.lastModified() + 2_000)
        val after = cache.of(kb)
        assertNotSame(before, after)
        assertEquals(listOf("a"), after.backlinks["c"])
        assertEquals(null, after.backlinks["b"])
    }

    @Test
    fun `a new or deleted note is noticed`() {
        note("ideas/a.md", "[[b]]")
        val cache = NoteIndexCache()
        assertEquals(listOf("b"), cache.of(kb).unresolved.map { it.target })
        note("ideas/b.md", "")
        assertEquals(emptyList<UnresolvedLink>(), cache.of(kb).unresolved)
        File(root, "ideas/b.md").delete()
        assertEquals(listOf("b"), cache.of(kb).unresolved.map { it.target })
    }

    @Test
    fun `each knowledge base keeps its own index`() {
        val other = File(root, "other").apply { mkdirs() }
        File(root, "first").mkdirs()
        File(root, "first/ideas").mkdirs()
        File(root, "first/ideas/a.md").writeText("")
        File(other, "ideas").mkdirs()
        File(other, "ideas/z.md").writeText("")
        val cache = NoteIndexCache()
        assertEquals(listOf("a"), cache.of(KnowledgeBase(File(root, "first"))).notes.map { it.name })
        assertEquals(listOf("z"), cache.of(KnowledgeBase(other)).notes.map { it.name })
        assertEquals(listOf("a"), cache.of(KnowledgeBase(File(root, "first"))).notes.map { it.name })
    }
}
