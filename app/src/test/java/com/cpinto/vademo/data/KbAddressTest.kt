package com.cpinto.vademo.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KbAddressTest {

    private fun valid(input: String): KbAddress {
        val parsed = KbAddress.parse(input)
        assertTrue(parsed is KbAddress.Parsed.Valid, "$input: $parsed")
        return (parsed as KbAddress.Parsed.Valid).address
    }

    private fun reason(input: String): String {
        val parsed = KbAddress.parse(input)
        assertTrue(parsed is KbAddress.Parsed.Invalid, "$input: $parsed")
        return (parsed as KbAddress.Parsed.Invalid).reason
    }

    @Test
    fun `owner and name alone is a GitHub address`() {
        val address = valid("  carlospinto4p/kb-business ")
        assertEquals("https://github.com/carlospinto4p/kb-business.git", address.url)
        assertEquals("carlospinto4p", address.owner)
        assertEquals("kb-business", address.name)
        assertTrue(address.isGitHub)
    }

    @Test
    fun `a host with no scheme gets https`() {
        assertEquals("https://github.com/o/kb-x.git", valid("github.com/o/kb-x").url)
        assertEquals("https://gitlab.com/group/sub/kb-x.git", valid("gitlab.com/group/sub/kb-x").url)
    }

    @Test
    fun `a full address is kept as typed`() {
        val address = valid("https://gitlab.com/group/sub/kb-x/")
        assertEquals("https://gitlab.com/group/sub/kb-x/", address.url)
        assertEquals("group/sub", address.owner)
        assertEquals("kb-x", address.name)
    }

    @Test
    fun `spellings of one repository share a key`() {
        val keys = listOf(
            "https://github.com/O/kb-x.git",
            "https://github.com/o/kb-x",
            "o/KB-x",
            "github.com/o/kb-x/",
        ).map { valid(it).key }.toSet()
        assertEquals(1, keys.size)
    }

    @Test
    fun `what cannot be cloned over https is refused with a reason`() {
        assertEquals("Type an address first.", reason("  "))
        assertTrue(reason("kb-x").contains("owner/name"))
        assertTrue(reason("http://github.com/o/kb-x").contains("HTTPS"))
        assertTrue(reason("ssh://git@github.com/o/kb-x").contains("HTTPS"))
        assertTrue(reason("git@github.com:o/kb-x.git").contains("SSH"))
        assertTrue(reason("o/kb x").contains("spaces"))
        assertTrue(reason("https://github.com/o/kb-x?tab=readme").contains("not a repository"))
    }

    @Test
    fun `a stored address that cannot be read still has a name`() {
        val address = KbAddress.of("file:///sdcard/kb-local")
        assertEquals("kb-local", address.name)
        assertEquals("", address.host)
    }

    @Test
    fun `an address already listed is refused, in any spelling`() {
        val listed = listOf("https://github.com/o/kb-x.git")
        assertEquals("kb-x is already in the list.", KbAddress.conflict(listed, valid("O/kb-x")))
    }

    @Test
    fun `an address ending in a listed name is refused, naming the other`() {
        val listed = listOf("https://github.com/a/kb-x.git")
        val conflict = KbAddress.conflict(listed, valid("b/kb-x"))
        assertTrue(conflict!!.contains("https://github.com/a/kb-x.git"))
        assertNull(KbAddress.conflict(listed, valid("b/kb-y")))
    }
}
