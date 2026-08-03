package com.kcodereview.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * LLM client for the OpenAI Chat Completions API **and any OpenAI-compatible endpoint**
 * (Ollama, Azure OpenAI, LM Studio, etc.).
 *
 * Auth: `Authorization: Bearer <apiKey>` header.
 * Model: sent in the request body.
 */
class OpenAiLlmClient(
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
            "model" to modelName,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user",   "content" to userPrompt),
            ),
            "temperature" to 0.2,
            "response_format" to mapOf("type" to "json_object"),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpointUrl))
            .timeout(Duration.ofMinutes(2))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "OpenAI API error HTTP ${response.statusCode()}: ${response.body().take(500)}"
        }

        return extractText(response.body())
    }

    companion object {
        /**
         * Extracts the assistant message content from an OpenAI chat completions JSON response.
         * Exposed as a companion function so tests can call it without auth.
         */
        fun extractText(responseBody: String): String {
            val root = JsonParser.parseString(responseBody).asJsonObject
            val choices = root.getAsJsonArray("choices")
                ?: throw IllegalStateException("OpenAI response missing 'choices'")
            check(choices.size() > 0) { "OpenAI returned no choices" }
            val content = choices[0].asJsonObject
                .getAsJsonObject("message")
                .get("content")?.asString?.trim()
            require(!content.isNullOrBlank()) { "OpenAI returned empty content" }
            return content
        }
    }
}
