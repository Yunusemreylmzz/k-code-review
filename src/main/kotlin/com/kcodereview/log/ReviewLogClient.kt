package com.kcodereview.log

import com.intellij.openapi.diagnostic.Logger
import com.kcodereview.ai.HttpTransport
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration

/**
 * POSTs review log JSON to the user-configured API URL.
 * Failures are logged and never thrown to callers (fire-and-forget).
 */
object ReviewLogClient {

    private val log = Logger.getInstance(ReviewLogClient::class.java)

    val requestTimeout: Duration = Duration.ofSeconds(8)

  /**
     * Settings test: POST sample JSON and throw on failure (non-2xx or network error).
     * @return HTTP status code on success
     */
    fun probe(apiUrl: String, jsonBody: String): Int {
        val url = apiUrl.trim()
        require(url.isNotBlank()) { "Log API URL cannot be blank." }
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Log API URL must start with http:// or https://"
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json, */*")
            .header("User-Agent", "K-Code-Review")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        val response = HttpTransport.send(request)
        val status = response.statusCode()
        if (status !in 200..299) {
            error(
                "Log API POST failed (HTTP $status): ${response.body().take(200)}",
            )
        }
        log.info("Review log probe OK (HTTP $status) → $url")
        return status
    }

    fun post(apiUrl: String, jsonBody: String): Boolean =
        runCatching { probe(apiUrl, jsonBody); true }
            .onFailure { log.warn("Review log POST error → ${apiUrl.trim()}: ${it.message}") }
            .getOrDefault(false)
}
