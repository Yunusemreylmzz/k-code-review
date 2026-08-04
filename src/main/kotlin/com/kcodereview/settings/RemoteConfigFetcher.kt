package com.kcodereview.settings

import com.intellij.openapi.diagnostic.Logger
import com.kcodereview.ai.HttpTransport
import java.net.URI
import java.net.http.HttpRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches remote text files (prompt overlays) with a short timeout.
 * Failures are cached briefly so reviews never hang repeatedly on a bad URL.
 */
object RemoteConfigFetcher {

    private val log = Logger.getInstance(RemoteConfigFetcher::class.java)

    private const val CACHE_TTL_MS = 5 * 60 * 1_000L
    private const val NEGATIVE_CACHE_TTL_MS = 60_000L

    private data class CacheEntry(val content: String?, val fetchedAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Fetches [url] or returns cached value. Throws on hard failure (caller may catch).
     * Blank [url] is rejected.
     */
    fun fetch(url: String): String {
        val rawUrl = toRawUrl(url.trim())
        require(rawUrl.isNotBlank()) { "URL cannot be blank." }

        val cached = cache[rawUrl]
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.fetchedAt
            val ttl = if (cached.content == null) NEGATIVE_CACHE_TTL_MS else CACHE_TTL_MS
            if (age < ttl) {
                val body = cached.content
                if (body != null) {
                    log.debug("RemoteConfigFetcher: cache hit for $rawUrl")
                    return body
                }
                error("Remote prompt previously failed (cached). Clear prompt URL or retry in a minute.")
            }
        }

        log.info("RemoteConfigFetcher: fetching $rawUrl")
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(rawUrl))
                .timeout(HttpTransport.configRequestTimeout)
                .GET()
                .header("Accept", "text/plain,*/*")
                .build()

            val response = HttpTransport.send(request)
            val status = response.statusCode()
            check(status in 200..299) {
                "Failed to fetch remote config from $rawUrl (HTTP $status)."
            }
            val content = response.body().trim()
            check(content.isNotBlank()) { "Remote config file is empty at $rawUrl" }
            cache[rawUrl] = CacheEntry(content, System.currentTimeMillis())
            content
        } catch (ex: Exception) {
            cache[rawUrl] = CacheEntry(null, System.currentTimeMillis())
            throw ex
        }
    }

    /** Soft fetch for review path — never throws; returns "" on failure. */
    fun fetchOrEmpty(url: String): String =
        runCatching { fetch(url) }
            .onFailure { log.warn("RemoteConfigFetcher: skipped remote prompt: ${it.message}") }
            .getOrDefault("")

    fun invalidate(url: String? = null) {
        if (url == null) {
            cache.clear()
        } else {
            cache.remove(toRawUrl(url.trim()))
        }
    }

    fun toRawUrl(url: String): String {
        val m = GITHUB_BLOB_RE.matchEntire(url) ?: return url
        val (user, repo, path) = m.destructured
        return "https://raw.githubusercontent.com/$user/$repo/$path"
    }

    private val GITHUB_BLOB_RE = Regex(
        """^https?://github\.com/([^/]+)/([^/]+)/blob/(.+)$""",
    )
}
