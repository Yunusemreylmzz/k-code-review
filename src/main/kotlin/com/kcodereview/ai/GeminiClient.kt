package com.kcodereview.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * LLM client for the Google Gemini generateContent API.
 *
 * Supports both legacy Standard keys (`AIza…`) and new AI Studio auth keys (`AQ.…`).
 * Auth is sent as `x-goog-api-key` (preferred) and, on 401, retried with a URL-encoded
 * `?key=` query parameter for maximum compatibility.
 * Transient 503 / 429 responses are retried with short backoff (fail-fast overall).
 */
class GeminiClient(
    private val apiKey: String,
    private val endpointUrl: String,
    private val requestTimeout: Duration = HttpTransport.llmRequestTimeout,
    private val gson: Gson = Gson(),
    private val maxAttempts: Int = 2,
    private val sleeper: (Long) -> Unit = { ms -> Thread.sleep(ms) },
    private val sender: (HttpRequest) -> Pair<Int, String> = { req ->
        val res = HttpTransport.send(req)
        res.statusCode() to res.body()
    },
) : LlmClient {

    override fun generate(systemPrompt: String, userPrompt: String): String {
        val sanitizedKey = sanitizeApiKey(apiKey)
        require(sanitizedKey.isNotBlank()) {
            "LLM API key is not configured. Open Settings → Tools → K Code Review."
        }

        val bodyJson = gson.toJson(buildRequestBody(systemPrompt, userPrompt))
        var lastStatus = 0
        var lastBody = ""

        repeat(maxAttempts) { attempt ->
            val (status, body) = sendWithAuthFallback(sanitizedKey, bodyJson)
            lastStatus = status
            lastBody = body

            if (status in 200..299) {
                return extractText(body)
            }
            if (!isRetryable(status) || attempt == maxAttempts - 1) {
                check(false) { formatHttpError(status, body) }
            }
            // Short backoff: 400ms, 800ms
            sleeper((1L shl attempt) * 400L)
        }
        check(false) { formatHttpError(lastStatus, lastBody) }
        error("unreachable")
    }

    private fun sendWithAuthFallback(key: String, bodyJson: String): Pair<Int, String> {
        val headerResponse = sender(buildRequest(key, bodyJson, useQueryParam = false))
        if (headerResponse.first !in listOf(401, 403)) {
            return headerResponse
        }
        return sender(buildRequest(key, bodyJson, useQueryParam = true))
    }

    private fun buildRequestBody(systemPrompt: String, userPrompt: String): JsonObject =
        JsonObject().apply {
            add(
                "contents",
                gson.toJsonTree(
                    listOf(
                        mapOf(
                            "role" to "user",
                            "parts" to listOf(mapOf("text" to "$systemPrompt\n\n---\n\n$userPrompt")),
                        ),
                    ),
                ),
            )
            add(
                "generationConfig",
                gson.toJsonTree(
                    mapOf(
                        "temperature" to 0.2,
                        "maxOutputTokens" to 4096,
                        "responseMimeType" to "application/json",
                    ),
                ),
            )
        }

    private fun buildRequest(key: String, bodyJson: String, useQueryParam: Boolean): HttpRequest {
        val base = endpointUrl.trim()
        val uri = if (useQueryParam) {
            val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8)
            val sep = if ('?' in base) '&' else '?'
            URI.create("$base${sep}key=$encoded")
        } else {
            URI.create(base)
        }

        val builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))

        if (!useQueryParam) {
            builder.header("x-goog-api-key", key)
        }
        return builder.build()
    }

    companion object {

        /** @deprecated Use [HttpTransport.sharedHttpClient] — kept for tests / other clients. */
        @Deprecated("Use HttpTransport.sharedHttpClient", ReplaceWith("HttpTransport.sharedHttpClient"))
        internal val sharedHttpClient = HttpTransport.sharedHttpClient

        fun isRetryable(status: Int): Boolean = status == 429 || status == 503 || status == 502 || status == 504

        fun sanitizeApiKey(raw: String): String =
            raw.trim()
                .replace("\u200B", "")
                .replace("\uFEFF", "")
                .replace(Regex("\\s+"), "")

        fun formatHttpError(status: Int, body: String): String {
            val snippet = body.take(400)
            if (status == 401 || status == 403) {
                return buildString {
                    append("Gemini authentication failed (HTTP $status). ")
                    append("Paste a Google AI Studio key (Settings → Tools → K Code Review). ")
                    append("New keys start with \"AQ.\"; legacy keys start with \"AIza\". ")
                    append("Make sure you clicked Apply after pasting. ")
                    append("Server said: $snippet")
                }
            }
            if (status == 404 && body.contains("no longer available", ignoreCase = true)) {
                return buildString {
                    append("This Gemini model is not available for your API key/project (HTTP 404). ")
                    append("Select a newer model such as \"Gemini 3.5 Flash\" or \"Gemini Flash (latest)\" ")
                    append("in Settings → Tools → K Code Review. ")
                    append("Server said: $snippet")
                }
            }
            if (status == 429) {
                return buildString {
                    append("Gemini quota exceeded (HTTP 429). ")
                    append("Free-tier quota for this model may be 0 (common for Gemini 2.5 Pro/Flash on new keys). ")
                    append("Switch to \"Gemini 3.5 Flash\" or \"Gemini 3.6 Flash\" in Settings → Tools → K Code Review. ")
                    append("Details: https://ai.google.dev/gemini-api/docs/rate-limits — ")
                    append(snippet)
                }
            }
            if (status == 503 || status == 502 || status == 504) {
                return buildString {
                    append("Gemini is temporarily unavailable (HTTP $status) — high demand / outage. ")
                    append("Wait a minute and retry, or switch model to \"Gemini Flash (latest)\" / \"Gemini 3.1 Flash Lite\". ")
                    append("You can also Commit Anyway if you need to proceed now. ")
                    append("Server said: $snippet")
                }
            }
            return "Gemini API error HTTP $status: $snippet"
        }

        fun extractText(responseBody: String): String {
            val root = JsonParser.parseString(responseBody).asJsonObject
            val candidates = root.getAsJsonArray("candidates")
                ?: throw IllegalStateException("Gemini response missing 'candidates'")
            check(candidates.size() > 0) { "Gemini returned no candidates" }
            val candidate = candidates[0].asJsonObject
            val finish = candidate.get("finishReason")?.asString
            if (finish == "MAX_TOKENS") {
                // Still try to parse partial JSON if present.
            }
            val content = candidate.getAsJsonObject("content")
                ?: throw IllegalStateException("Gemini response missing content (finishReason=$finish)")
            val parts = content.getAsJsonArray("parts")
                ?: throw IllegalStateException("Gemini response missing parts (finishReason=$finish)")
            val text = parts.joinToString("\n") { it.asJsonObject.get("text")?.asString.orEmpty() }.trim()
            require(text.isNotBlank()) { "Gemini returned empty text (finishReason=$finish)" }
            return text
        }
    }
}
