package com.cpinto.vademo.data

import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/**
 * The GitHub token as JGit's HTTPS password, given only when the address
 * JGit asks for is GitHub over HTTPS. JGit names the address it is talking
 * to, so this holds for a clone's `origin` as well as for the URL in
 * Settings. Any other host is asked without the token and never sees it.
 *
 * GitHub accepts any non-empty user name ("oauth2") with a personal access
 * token; checked on a real phone against github.com, 2026-11-15.
 */
class GitHubOnlyCredentials(token: String) : CredentialsProvider() {

    private val token = UsernamePasswordCredentialsProvider("oauth2", token)

    override fun isInteractive(): Boolean = false

    override fun supports(vararg items: CredentialItem): Boolean = token.supports(*items)

    override fun get(uri: URIish?, vararg items: CredentialItem): Boolean =
        uri != null && isGitHub(uri) && token.get(uri, *items)

    companion object {
        fun isGitHub(uri: URIish): Boolean =
            uri.scheme.equals("https", ignoreCase = true) && uri.host.equals("github.com", ignoreCase = true)
    }
}
