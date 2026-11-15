package com.cpinto.vademo.data

import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.URIish
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitHubOnlyCredentialsTest {

    private val credentials = GitHubOnlyCredentials("test-token")

    @Test
    fun `GitHub over https gets the token`() {
        val user = CredentialItem.Username()
        val password = CredentialItem.Password()
        assertTrue(credentials.get(URIish("https://github.com/example/kb-x.git"), user, password))
        assertEquals("oauth2", user.value)
        assertArrayEquals("test-token".toCharArray(), password.value)
    }

    @Test
    fun `any other address gets nothing`() {
        for (url in listOf(
            "https://gitlab.com/example/kb-x.git",
            "https://github.com.example.org/kb-x.git",
            "https://example.org/github.com/kb-x.git",
            "http://github.com/example/kb-x.git",
            "file:///tmp/kb-x.git",
        )) {
            val password = CredentialItem.Password()
            assertFalse(credentials.get(URIish(url), CredentialItem.Username(), password), url)
            assertNull(password.value, url)
        }
    }

    @Test
    fun `the host is compared without case`() {
        assertTrue(GitHubOnlyCredentials.isGitHub(URIish("https://GitHub.com/example/kb-x")))
    }
}
