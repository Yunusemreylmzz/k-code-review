package com.kcodereview.log

/**
 * Git identity + repository metadata used in review log payloads.
 */
data class GitProjectInfo(
    val username: String,
    val userEmail: String,
    val repoName: String,
    val repoOwner: String,
    val repoFullName: String,
    val remoteUrl: String,
    val branch: String,
) {
    companion object {
        val EMPTY = GitProjectInfo(
            username = "",
            userEmail = "",
            repoName = "",
            repoOwner = "",
            repoFullName = "",
            remoteUrl = "",
            branch = "",
        )

        /**
         * Parses owner/repo from common remote URL forms:
         * - https://github.com/owner/repo.git
         * - git@github.com:owner/repo.git
         * - ssh://git@gitlab.com/owner/repo.git
         */
        fun parseRemote(remoteUrl: String): Pair<String, String> {
            val raw = remoteUrl.trim()
            if (raw.isBlank()) return "" to ""

            // git@host:owner/repo.git
            val scp = Regex("""^git@[^:]+:(.+?)(?:\.git)?$""").find(raw)
            if (scp != null) {
                return splitOwnerRepo(scp.groupValues[1])
            }

            // ssh://git@host/owner/repo.git  OR  https://host/owner/repo.git
            val path = raw
                .removePrefix("ssh://")
                .removePrefix("git://")
                .removePrefix("https://")
                .removePrefix("http://")
                .substringAfter('/')
                .removeSuffix(".git")
                .trim('/')

            return splitOwnerRepo(path)
        }

        private fun splitOwnerRepo(path: String): Pair<String, String> {
            val parts = path.split('/').filter { it.isNotBlank() }
            if (parts.isEmpty()) return "" to ""
            if (parts.size == 1) return "" to parts[0]
            val repo = parts.last()
            val owner = parts.dropLast(1).joinToString("/")
            return owner to repo
        }
    }
}
