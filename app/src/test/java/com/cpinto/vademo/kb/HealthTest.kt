package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HealthTest {

    @TempDir
    lateinit var root: File

    private fun write(path: String, text: String) {
        File(root, path).apply { parentFile!!.mkdirs() }.writeText(text)
    }

    private fun front(type: String, status: String) = "---\ntype: $type\nstatus: $status\n---\n"

    @Test
    fun `counts each check on a knowledge base read from disk`() {
        write("index.md", "[[consensus]]")
        write("consensus/consensus.md", "[[raft]] [[why-leaders]] [[answered]] [[old-question]]")
        write("consensus/raft.md", front("algorithm", "draft") + "[[consensus]] [[paxos]] (unverified) (unverified)")
        write("consensus/why-leaders.md", front("question", "draft") + "[[consensus]]")
        write("consensus/answered.md", front("question", "reviewed") + "[[consensus]]")
        write("consensus/old-question.md", front("question", "outdated") + "[[consensus]]")
        write("consensus/lonely.md", front("concept", "draft") + "[[nowhere]]")
        write("inbox/a-capture.md", "Read this later.")
        write("templates/question.md", front("question", "draft") + "[[template-link]]")

        val counts = Health.counts(NoteIndex.of(KnowledgeBase(root)))
            .associate { (check, n) -> check.name to n }

        assertEquals(
            mapOf(
                // Nothing links to `lonely` or to the inbox capture.
                "Orphans" to 2,
                // `raft` → paxos and `lonely` → nowhere; templates are not notes.
                "Broken links" to 2,
                // One note, however many markers it has.
                "Unverified figures" to 1,
                // Answered (`reviewed`) and `outdated` questions are not open.
                "Open questions" to 1,
                "Waiting in the inbox" to 1,
            ),
            counts,
        )
    }

    @Test
    fun `every check is a query the search understands as filters only`() {
        for (check in Health.CHECKS) {
            val query = NoteSearch.parse(check.query)
            assertEquals(emptyList<String>(), query.words, check.name)
        }
    }
}
