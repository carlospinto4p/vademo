package com.cpinto.vademo.data

/**
 * A knowledge base's clone address, read into its parts so Settings can show
 * it as a row (name, owner, host) rather than a line of text, and so two
 * spellings of one repository count as one.
 */
data class KbAddress(
    /** The address cloned from, as stored in [Settings.repoUrls]. */
    val url: String,
    val host: String,
    /** Everything between the host and the name: `o` in `github.com/o/kb-x`. Empty when there is none. */
    val owner: String,
    /** The folder it is cloned into, [Settings.folderName]. */
    val name: String,
) {
    val isGitHub: Boolean get() = host.equals("github.com", ignoreCase = true)

    /** The same for every spelling of one repository: case, `.git` and a final slash do not count. */
    val key: String get() = "${host.lowercase()}/${owner.lowercase()}/${name.lowercase()}"

    sealed interface Parsed {
        data class Valid(val address: KbAddress) : Parsed
        data class Invalid(val reason: String) : Parsed
    }

    companion object {
        private val NAME = Regex("[A-Za-z0-9._-]+")

        /**
         * Reads what was typed into an address to clone: `owner/name`,
         * `github.com/owner/name` and a full HTTPS address (with or without
         * `.git`) all work. A bare `owner/name` is on GitHub.
         */
        fun parse(input: String): Parsed {
            val text = input.trim()
            if (text.isEmpty()) return Parsed.Invalid("Type an address first.")
            if (text.any { it.isWhitespace() }) return Parsed.Invalid("An address has no spaces.")
            val scheme = text.substringBefore("://", missingDelimiterValue = "")
            if (scheme.isNotEmpty() && !scheme.equals("https", ignoreCase = true)) {
                return Parsed.Invalid("Only HTTPS addresses can be synced, not $scheme.")
            }
            if (text.startsWith("git@")) return Parsed.Invalid("Use the HTTPS address, not the SSH one.")
            val rest = text.substringAfter("://").trimEnd('/')
            val parts = rest.split('/')
            val withHost = if (scheme.isNotEmpty() || parts.first().contains('.')) parts else listOf("github.com") + parts
            if (withHost.size < 3 || withHost.any { it.isEmpty() }) {
                return Parsed.Invalid("Expected owner/name or an HTTPS clone address.")
            }
            val host = withHost.first()
            val name = withHost.last().removeSuffix(".git")
            val owner = withHost.subList(1, withHost.size - 1).joinToString("/")
            if (!NAME.matches(name) || withHost.subList(1, withHost.size - 1).any { !NAME.matches(it) }) {
                return Parsed.Invalid("That is not a repository address.")
            }
            val url = if (scheme.isEmpty()) "https://$host/$owner/$name.git" else text
            return Parsed.Valid(KbAddress(url, host, owner, name))
        }

        /** An address already stored, shown as it is even when [parse] would refuse it. */
        fun of(url: String): KbAddress = when (val parsed = parse(url)) {
            is Parsed.Valid -> parsed.address.copy(url = url)
            is Parsed.Invalid -> KbAddress(url, host = "", owner = "", name = Settings.folderName(url))
        }

        /**
         * Why [candidate] cannot join [urls], or `null` when it can: it is
         * already there, or another address there ends in the same name and
         * the two would be cloned into one folder.
         */
        fun conflict(urls: List<String>, candidate: KbAddress): String? {
            val existing = urls.map(::of)
            if (existing.any { it.key == candidate.key }) return "${candidate.name} is already in the list."
            val sameFolder = existing.firstOrNull { it.name == candidate.name }
                ?: return null
            return "${sameFolder.url} also ends in ${candidate.name}, and only one can be synced. Remove it first."
        }
    }
}
