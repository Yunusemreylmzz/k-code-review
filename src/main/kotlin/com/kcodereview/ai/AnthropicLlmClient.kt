package com.kcodereview.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * LLM client for the Anthropic Messages API.
 *
 * Auth: `x-api-key` header + `anthropic-version` header.
 * System prompt: top-level `system` field (not inside messages).
 * Model: sent in the request body.
 *
 * Endpoint: https://api.anthropic.com/v1/messages
 */
class AnthropicLlmClient(
    private val apiKey: String,
    private val endpointUrl: String,
    private val modelName: String,
    private val httpClient: HttpClient = GeminiClient.sharedHttpClient,
    private val gson: Gson = Gson(),
) : LlmClient {

    override fun generate(systemPrompt: String, userPrompt: String): String {
        require(apiKey.isNotBlank()) {
            "LLM API key is not configured. Open Settings → Tools → K Code Review."
        }

        val body = mapOf(
            "model"      to modelName,
            "max_tokens" to 8192,
            "system"     to systemPrompt,
            "messages"   to listOf(
                mapOf("role" to "user", "content" to userPrompt),
            ),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpointUrl))
            .timeout(Duration.ofMinutes(2))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Anthropic API error HTTP ${response.statusCode()}: ${response.body().take(500)}"
        }

        return extractText(response.body())
    }

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"

        /**
         * Extracts the text content from an Anthropic Messages JSON response.
         * Exposed as a companion function so tests can call it without auth.
         */
        fun extractText(responseBody: String): String {
            val root = JsonParser.parseString(responseBody).asJsonObject
            val content = root.getAsJsonArray("content")
                ?: throw IllegalStateException("Anthropic response missing 'content'")
            check(content.size() > 0) { "Anthropic returned no content blocks" }
            val text = content.asSequence()
                .map { it.asJsonObject }
                .filter { it.get("type")?.asString == "text" }
                .joinToString("\n") { it.get("text")?.asString.orEmpty() }
                .trim()
            require(text.isNotBlank()) { "Anthropic returned empty text" }
            return text
        }
    }
}
