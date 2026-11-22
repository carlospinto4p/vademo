package com.cpinto.vademo.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class GitHubRepoFinderTest {

    private fun repo(owner: String, name: String, isPrivate: Boolean = false) =
        """{"full_name":"$owner/$name","name":"$name","clone_url":"https://github.com/$owner/$name.git",""" +
            """"private":$isPrivate,"description":null,"owner":{"login":"$owner"},"forks":3}"""

    private fun page(vararg repos: String) = "[${repos.joinToString(",")}]"

    @Test
    fun `only kb- repositories are kept, public and private, by full name`() = runTest {
        val asked = mutableListOf<Pair<String, String>>()
        val finder = GitHubRepoFinder { url, token ->
            asked += url to token
            HttpAnswer(200, page(repo("me", "kb-zeta", isPrivate = true), repo("me", "website"), repo("org", "KB-alpha")))
        }
        val found = finder.find(" secret ") as RepoSearch.Found
        assertEquals(listOf("me/kb-zeta", "org/KB-alpha"), found.repos.map { it.fullName })
        assertEquals(listOf(true, false), found.repos.map { it.isPrivate })
        assertEquals(listOf(GitHubRepoFinder.FIRST_PAGE to "secret"), asked)
    }

    @Test
    fun `every page is read, following the next link`() = runTest {
        val second = "https://api.github.com/user/repos?per_page=100&page=2"
        val finder = GitHubRepoFinder { url, _ ->
            if (url == second) {
                HttpAnswer(200, page(repo("me", "kb-b")), """<${GitHubRepoFinder.FIRST_PAGE}>; rel="prev"""")
            } else {
                HttpAnswer(200, page(repo("me", "kb-a")), """<$second>; rel="next", <$second>; rel="last"""")
            }
        }
        assertEquals(listOf("me/kb-a", "me/kb-b"), (finder.find("t") as RepoSearch.Found).repos.map { it.fullName })
    }

    @Test
    fun `a next link that never ends stops after the last page allowed`() = runTest {
        var calls = 0
        val finder = GitHubRepoFinder { _, _ ->
            calls++
            HttpAnswer(200, page(repo("me", "kb-$calls")), """<https://api.github.com/again>; rel="next"""")
        }
        finder.find("t")
        assertEquals(GitHubRepoFinder.MAX_PAGES, calls)
    }

    @Test
    fun `a refused token, a limit and no network each say what happened`() = runTest {
        suspend fun failed(answer: () -> HttpAnswer): String =
            (GitHubRepoFinder { _, _ -> answer() }.find("t") as RepoSearch.Failed).message
        assertTrue(failed { HttpAnswer(401, "{}") }.contains("refused the token"))
        assertTrue(failed { HttpAnswer(403, """{"message":"API rate limit exceeded"}""") }.contains("limiting"))
        assertTrue(failed { HttpAnswer(403, "{}") }.contains("not allowed"))
        assertTrue(failed { HttpAnswer(500, "") }.contains("500"))
        assertTrue(failed { HttpAnswer(200, "<html>") }.contains("could not be read"))
        assertTrue(failed { throw IOException("no route") }.contains("no route"))
    }

    @Test
    fun `no token asks nothing`() = runTest {
        val finder = GitHubRepoFinder { _, _ -> error("asked GitHub") }
        assertTrue(finder.find("  ") is RepoSearch.Failed)
    }

    @Test
    fun `the next page is read from a Link header`() {
        assertEquals(
            "https://api.github.com/user/repos?page=3",
            GitHubRepoFinder.nextPage(
                """<https://api.github.com/user/repos?page=1>; rel="prev", <https://api.github.com/user/repos?page=3>; rel="next"""",
            ),
        )
        assertNull(GitHubRepoFinder.nextPage("""<https://api.github.com/user/repos?page=1>; rel="first""""))
        assertNull(GitHubRepoFinder.nextPage(null))
    }

    @Test
    fun `a repository listed twice is kept once`() {
        val one = GitHubRepo("me/kb-x", "kb-x", "https://github.com/me/kb-x.git")
        assertEquals(listOf(one), GitHubRepoFinder.knowledgeBases(listOf(one, one.copy(fullName = "Me/kb-x"))))
    }
}
