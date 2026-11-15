package com.cpinto.vademo.data

import com.cpinto.vademo.kb.SavedQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SavedQueriesTest {

    private val drafts = SavedQuery("Drafts to review", "status:draft")
    private val raft = SavedQuery("Raft", "kb:distributed-systems tag:raft")

    @Test
    fun `encodes and decodes in the order saved`() {
        val text = SavedQueries.encode(listOf(drafts, raft))
        assertEquals(listOf(drafts, raft), SavedQueries.decode(text))
    }

    @Test
    fun `decoding nothing, or a damaged line, gives what can be read`() {
        assertEquals(emptyList<SavedQuery>(), SavedQueries.decode(null))
        assertEquals(emptyList<SavedQuery>(), SavedQueries.decode(""))
        assertEquals(listOf(drafts), SavedQueries.decode("no tab here\n\tno name\nno query\t\nDrafts to review\tstatus:draft"))
    }

    @Test
    fun `put adds last, or replaces a search of the same name where it stands`() {
        val two = SavedQueries.put(SavedQueries.put(emptyList(), drafts.name, drafts.query), raft.name, raft.query)
        assertEquals(listOf(drafts, raft), two)
        assertEquals(
            listOf(SavedQuery("drafts TO review", "status:draft type:concept"), raft),
            SavedQueries.put(two, "drafts TO review", "status:draft type:concept"),
        )
    }

    @Test
    fun `put keeps each search on one line, and ignores a blank name or query`() {
        val saved = SavedQueries.put(emptyList(), " Two\tlines\n", "type:question\r\nstatus:draft ")
        assertEquals(listOf(SavedQuery("Two lines", "type:question status:draft")), saved)
        assertEquals(saved, SavedQueries.decode(SavedQueries.encode(saved)))
        assertEquals(emptyList<SavedQuery>(), SavedQueries.put(emptyList(), " ", "status:draft"))
        assertEquals(emptyList<SavedQuery>(), SavedQueries.put(emptyList(), "Empty", "\n"))
    }

    @Test
    fun `remove takes out the search with that name`() {
        assertEquals(listOf(raft), SavedQueries.remove(listOf(drafts, raft), drafts.name))
        assertEquals(listOf(drafts, raft), SavedQueries.remove(listOf(drafts, raft), "Missing"))
    }

    @Test
    fun `a search opened in a knowledge base keeps to it, unless the saved one names its own`() {
        assertEquals("status:draft", SavedQueries.applied(drafts, ""))
        assertEquals("kb:business status:draft", SavedQueries.applied(drafts, "kb:business "))
        assertEquals(raft.query, SavedQueries.applied(raft, "kb:business "))
        // Only the knowledge-base filter is kept from where the search started.
        assertEquals("kb:business status:draft", SavedQueries.applied(drafts, "kb:business tag:x words"))
    }
}
