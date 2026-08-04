package com.kcodereview.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration

/**
 * LLM client for the Anthropic Messages API.
 */
class AnthropicLlmClient(
    private val apiKey: String,
    private val endpointUrl: String,
    private val modelName: String,
    private val requestTimeout: Duration = HttpTransport.llmRequestTimeout,
    private val gson: Gson = Gson(),
    private val sender: (HttpRequest) -> Pair<Int, String> = { req ->
        val res = HttpTransport.send(req)
        res.statusCode() to res.body()
    },
) : LlmClient {

    override fun generate(systemPrompt: String, userPrompt: String): String {
        require(apiKey.isNotBlank()) {
            "LLM API key is not configured. Open Settings → Tools → K Code Review."
        }

        val body = mapOf(
            "model" to modelName,
            "max_tokens" to 4096,
            "system" to systemPrompt,
            "messages" to listOf(
                mapOf("role" to "user", "content" to userPrompt),
            ),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpointUrl))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build()

        val (status, responseBody) = sender(request)
        check(status in 200..299) {
            "Anthropic API error HTTP $status: ${responseBody.take(500)}"
        }

        return extractText(responseBody)
    }

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"

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
