package com.cpinto.vademo.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DecryptOnceTest {

    private val asked = mutableListOf<String>()

    @Test
    fun `the same stored token is decrypted once`() {
        val token = DecryptOnce { asked += it; "plain-$it" }
        repeat(3) { assertEquals("plain-a", token.plain("a")) }
        assertEquals(listOf("a"), asked)
    }

    @Test
    fun `a new stored token is decrypted again`() {
        val token = DecryptOnce { asked += it; "plain-$it" }
        token.plain("a")
        assertEquals("plain-b", token.plain("b"))
        assertEquals(listOf("a", "b"), asked)
    }

    @Test
    fun `a token that could not be decrypted is tried again next time`() {
        val token = DecryptOnce { asked += it; null }
        assertNull(token.plain("a"))
        assertNull(token.plain("a"))
        assertEquals(listOf("a", "a"), asked)
    }
}
