package com.cpinto.vademo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** A repository the token can see, as GitHub's `GET /user/repos` describes it. */
@Serializable
data class GitHubRepo(
    @SerialName("full_name") val fullName: String,
    val name: String,
    @SerialName("clone_url") val cloneUrl: String,
    @SerialName("private") val isPrivate: Boolean = false,
    val description: String? = null,
    val archived: Boolean = false,
)

sealed interface RepoSearch {
    /** The token's knowledge bases, public and private, by full name. */
    data class Found(val repos: List<GitHubRepo>) : RepoSearch
    data class Failed(val message: String) : RepoSearch
}

/** One answer from GitHub's API: its status, body and `Link` header. */
data class HttpAnswer(val status: Int, val body: String, val link: String? = null)

fun interface HttpGet {
    @Throws(IOException::class)
    fun get(url: String, token: String): HttpAnswer
}

/**
 * Finds the knowledge bases a GitHub token can reach: every repository whose
 * name starts with [PREFIX], owned by the token's account, shared with it, or
 * in one of its organisations, public and private alike.
 */
class GitHubRepoFinder internal constructor(private val http: HttpGet) {

    @Inject constructor() : this(UrlConnectionGet)

    suspend fun find(token: String): RepoSearch = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext RepoSearch.Failed("Enter a token first.")
        val repos = mutableListOf<GitHubRepo>()
        var next: String? = FIRST_PAGE
        var pages = 0
        while (next != null && pages < MAX_PAGES) {
            val answer = try {
                http.get(next, token.trim())
            } catch (e: IOException) {
                return@withContext RepoSearch.Failed("Could not reach GitHub: ${e.message ?: e.javaClass.simpleName}")
            }
            failure(answer)?.let { return@withContext RepoSearch.Failed(it) }
            try {
                repos += JSON.decodeFromString(ListSerializer(GitHubRepo.serializer()), answer.body)
            } catch (e: SerializationException) {
                return@withContext RepoSearch.Failed("GitHub's answer could not be read.")
            } catch (e: IllegalArgumentException) {
                return@withContext RepoSearch.Failed("GitHub's answer could not be read.")
            }
            next = nextPage(answer.link)
            pages++
        }
        RepoSearch.Found(knowledgeBases(repos))
    }

    companion object {
        /** What names a knowledge-base repository, as in `kb-distributed-systems`. */
        const val PREFIX = "kb-"

        /** 100 per page, the most GitHub gives; [MAX_PAGES] caps an account with thousands. */
        internal const val FIRST_PAGE =
            "https://api.github.com/user/repos?per_page=100&sort=full_name&affiliation=owner,collaborator,organization_member"
        internal const val MAX_PAGES = 10

        private val JSON = Json { ignoreUnknownKeys = true }

        /** The repositories named like a knowledge base, once each, by full name. */
        fun knowledgeBases(repos: List<GitHubRepo>): List<GitHubRepo> = repos
            .filter { it.name.startsWith(PREFIX, ignoreCase = true) }
            .distinctBy { it.fullName.lowercase() }
            .sortedBy { it.fullName.lowercase() }

        /** The `rel="next"` address in a `Link` header, or `null` on the last page. */
        fun nextPage(link: String?): String? = link?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull { part -> part.split(';').drop(1).any { it.trim() == "rel=\"next\"" } }
            ?.substringAfter('<')
            ?.substringBefore('>')

        /** What to tell the person when GitHub did not answer with the list, or `null` when it did. */
        internal fun failure(answer: HttpAnswer): String? = when {
            answer.status in 200..299 -> null
            answer.status == 401 -> "GitHub refused the token. Check that it is typed right and has not expired."
            answer.status == 429 || (answer.status == 403 && answer.body.contains("rate limit", ignoreCase = true)) ->
                "GitHub is limiting how often it is asked. Try again in a few minutes."
            answer.status == 403 -> "The token is not allowed to list repositories."
            else -> "GitHub answered ${answer.status} instead of the list."
        }
    }
}

/** [HttpGet] over the platform's [HttpURLConnection], which needs no extra library. */
private object UrlConnectionGet : HttpGet {
    override fun get(url: String, token: String): HttpAnswer {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpAnswer(status, body, connection.getHeaderField("Link"))
        } finally {
            connection.disconnect()
        }
    }
}
