package com.cpinto.vademo.ui.diagram

import com.cpinto.vademo.kb.Ref
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ParseRefTest {

    @Test
    fun `a path, a path with a line, and nothing`() {
        assertEquals(Ref("consensus/raft.md", null), parseRef(" consensus/raft.md "))
        assertEquals(Ref("src/api.rs", 40), parseRef("src/api.rs:40"))
        assertNull(parseRef("   "))
    }

    @Test
    fun `a colon not followed by a number stays in the path`() {
        assertEquals(Ref("notes:draft.md", null), parseRef("notes:draft.md"))
    }
}
