package com.kcodereview.settings

import com.intellij.openapi.diagnostic.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches remote text files (API key, prompt) from GitHub or any raw URL.
 *
 * - GitHub blob URLs are automatically converted to raw.githubusercontent.com URLs.
 * - Results are cached in-memory with a [CACHE_TTL_MS] ms TTL to avoid repeated network calls.
 * - Thread-safe: uses ConcurrentHashMap for the cache.
 */
object RemoteConfigFetcher {

    private val log = Logger.getInstance(RemoteConfigFetcher::class.java)

    /** Cache TTL: 5 minutes. */
    private const val CACHE_TTL_MS = 5 * 60 * 1_000L

    private data class CacheEntry(val content: String, val fetchedAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches the text content of [url], returning the trimmed body.
     *
     * GitHub blob URLs like `https://github.com/user/repo/blob/main/file.txt`
     * are converted to their raw equivalent automatically.
     *
     * Results are cached for [CACHE_TTL_MS] ms. Call [invalidate] to force a re-fetch.
     *
     * @throws IllegalArgumentException if [url] is blank after trimming.
     * @throws IllegalStateException    if the server returns a non-2xx status or an empty body.
     */
    fun fetch(url: String): String {
        val rawUrl = toRawUrl(url.trim())
        require(rawUrl.isNotBlank()) { "URL cannot be blank." }

        // Serve from cache if still fresh.
        val cached = cache[rawUrl]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS) {
            log.debug("RemoteConfigFetcher: cache hit for $rawUrl")
            return cached.content
        }

        log.info("RemoteConfigFetcher: fetching $rawUrl")
        val request = HttpRequest.newBuilder()
            .uri(URI.create(rawUrl))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val status = response.statusCode()
        check(status in 200..299) {
            "Failed to fetch remote config from $rawUrl " +
                "(HTTP $status). Check that the URL is correct and the file is publicly accessible."
        }

        val content = response.body().trim()
        check(content.isNotBlank()) { "Remote config file is empty at $rawUrl" }

        cache[rawUrl] = CacheEntry(content, System.currentTimeMillis())
        return content
    }

    /**
     * Removes the cached entry for [url] (converted to raw form),
     * or clears the entire cache when [url] is null.
     */
    fun invalidate(url: String? = null) {
        if (url == null) {
            cache.clear()
        } else {
            cache.remove(toRawUrl(url.trim()))
        }
    }

    // -------------------------------------------------------------------------
    // URL normalisation
    // -------------------------------------------------------------------------

    /**
     * Converts a GitHub blob URL to its raw.githubusercontent.com equivalent.
     * Any other URL is returned unchanged.
     *
     * Example:
     *   `https://github.com/user/repo/blob/main/key.txt`
     *   → `https://raw.githubusercontent.com/user/repo/main/key.txt`
     */
    fun toRawUrl(url: String): String {
        val m = GITHUB_BLOB_RE.matchEntire(url) ?: return url
        val (user, repo, path) = m.destructured
        return "https://raw.githubusercontent.com/$user/$repo/$path"
    }

    private val GITHUB_BLOB_RE = Regex(
        """^https?://github\.com/([^/]+)/([^/]+)/blob/(.+)$""",
    )
}
