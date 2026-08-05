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

    fun post(apiUrl: String, jsonBody: String): Boolean {
        val url = apiUrl.trim()
        if (url.isBlank()) return false
        return runCatching {
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
            val ok = response.statusCode() in 200..299
            if (ok) {
                log.info("Review log posted OK (HTTP ${response.statusCode()}) → $url")
            } else {
                log.warn(
                    "Review log POST failed HTTP ${response.statusCode()} → $url: " +
                        response.body().take(300),
                )
            }
            ok
        }.onFailure {
            log.warn("Review log POST error → $url: ${it.message}")
        }.getOrDefault(false)
    }
}
