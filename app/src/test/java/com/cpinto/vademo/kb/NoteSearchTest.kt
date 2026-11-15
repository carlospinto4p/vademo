package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteSearchTest {

    private fun note(
        name: String,
        title: String = name,
        type: String? = null,
        status: String? = null,
        tags: List<String> = emptyList(),
        updated: String? = null,
        headings: List<String> = emptyList(),
        links: List<String> = emptyList(),
        unverified: Int = 0,
        body: String = "",
    ) = IndexedNote(
        path = "topic/$name.md", name = name, title = title, type = type, status = status, tags = tags,
        created = null, updated = updated, headings = headings, links = links, unverified = unverified, body = body,
    )

    private val consensus = NoteIndex(
        "kb-distributed-systems",
        listOf(
            note("index", title = "Distributed systems", type = "hub", links = listOf("raft", "quorum")),
            note(
                "raft", title = "Raft", type = "algorithm", status = "draft", tags = listOf("consensus"),
                updated = "2026-11-08", headings = listOf("Leader election"), links = listOf("quorum"),
                body = "Raft elects a leader by majority vote.",
            ),
            note(
                "quorum", title = "Quorum", type = "concept", status = "reviewed", tags = listOf("Consensus"),
                updated = "2026-09-02", links = listOf("raft"), unverified = 1,
                body = "A majority of the nodes. Used by leader election in raft.",
            ),
            note("leader", title = "Leaders and followers", type = "question", updated = "2026-08-30"),
        ),
    )

    private val business = NoteIndex(
        "kb-business",
        listOf(note("pricing", title = "Pricing a raft of plans", type = "idea", updated = "2026-10-01")),
    )

    private fun names(query: String, indexes: List<NoteIndex> = listOf(consensus)) =
        NoteSearch.search(indexes, query).map { it.note.name }

    @Test
    fun `an empty query finds nothing`() {
        assertEquals(emptyList<String>(), names(""))
        assertEquals(emptyList<String>(), names("   "))
    }

    @Test
    fun `every word must be found, ignoring case`() {
        assertEquals(listOf("raft", "quorum"), names("MAJORITY"))
        assertEquals(listOf("raft"), names("majority vote"))
        assertEquals(emptyList<String>(), names("majority paxos"))
    }

    @Test
    fun `a title match ranks above a heading match, which ranks above the body`() {
        // "leader": the title of `leader`, a heading of `raft`, the body of `quorum`.
        assertEquals(listOf("leader", "raft", "quorum"), names("leader"))
    }

    @Test
    fun `ties go to the most recently updated note, then a note with no date`() {
        assertEquals(listOf("raft", "quorum"), names("majority"))
        assertEquals(listOf("raft", "quorum", "leader", "index"), names("updated:>2000") + names("type:hub"))
        assertEquals(listOf("raft", "index"), names("links:quorum"))
    }

    @Test
    fun `type, status and tag filter on the frontmatter, ignoring case`() {
        assertEquals(listOf("quorum"), names("type:Concept"))
        assertEquals(listOf("raft"), names("status:draft"))
        assertEquals(listOf("raft", "quorum"), names("tag:CONSENSUS"))
        assertEquals(listOf("quorum"), names("tag:consensus nodes"))
    }

    @Test
    fun `updated compares ISO dates, and a bare date is a prefix`() {
        assertEquals(listOf("raft"), names("updated:>2026-09-02"))
        assertEquals(listOf("raft", "quorum"), names("updated:>=2026-09-02"))
        assertEquals(listOf("leader"), names("updated:<2026-09-01"))
        assertEquals(listOf("quorum", "leader"), names("updated:<=2026-09-02"))
        assertEquals(listOf("quorum"), names("updated:2026-09"))
        // A note without `updated:` never matches.
        assertEquals(emptyList<String>(), names("updated:>2000 type:hub"))
    }

    @Test
    fun `kb names a knowledge base with or without its kb- prefix`() {
        val both = listOf(consensus, business)
        assertEquals(listOf("raft", "pricing", "quorum"), names("raft", both))
        assertEquals(listOf("raft", "quorum"), names("raft kb:distributed-systems", both))
        assertEquals(listOf("pricing"), names("raft kb:kb-business", both))
        assertEquals(listOf("kb-business"), NoteSearch.search(both, "kb:business pricing").map { it.kb })
    }

    @Test
    fun `links keeps the notes linking to a note`() {
        assertEquals(listOf("raft", "index"), names("links:quorum"))
        assertEquals(listOf("raft", "index"), names("links:quorum.md"))
    }

    @Test
    fun `is finds orphans and unverified notes, and nothing for an unknown word`() {
        assertEquals(listOf("leader"), names("is:orphan"))
        assertEquals(listOf("quorum"), names("is:unverified"))
        assertEquals(emptyList<String>(), names("is:lost"))
    }

    @Test
    fun `an unknown key or an empty value is a word to find`() {
        val query = NoteSearch.parse("http://x.org type: Raft")
        assertEquals(listOf("http://x.org", "type:", "raft"), query.words)
        assertEquals(emptyList<NoteSearch.Filter>(), query.filters)
    }
}
