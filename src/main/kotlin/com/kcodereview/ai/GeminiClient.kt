package com.kcodereview.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * LLM client for the Google Gemini generateContent API.
 *
 * The API key is appended as a `?key=` query parameter.
 * The model is embedded in [endpointUrl] (e.g. `.../models/gemini-2.0-flash:generateContent`).
 */
class GeminiClient(
    private val apiKey: String,
    private val endpointUrl: String,
    private val httpClient: HttpClient = sharedHttpClient,
    private val gson: Gson = Gson(),
) : LlmClient {

    override fun generate(systemPrompt: String, userPrompt: String): String {
        require(apiKey.isNotBlank()) {
            "LLM API key is not configured. Open Settings → Tools → K Code Review."
        }

        val baseUrl = endpointUrl.trimEnd('/')
        val url = if ('?' in baseUrl) "$baseUrl&key=$apiKey" else "$baseUrl?key=$apiKey"

        val body = JsonObject().apply {
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
                        "responseMimeType" to "application/json",
                    ),
                ),
            )
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Gemini API error HTTP ${response.statusCode()}: ${response.body().take(500)}"
        }

        return extractText(response.body())
    }

    // -------------------------------------------------------------------------
    // Companion — pure parsing logic, no I/O (testable without auth)
    // -------------------------------------------------------------------------

    companion object {

        /** Shared HttpClient — one per JVM is enough (thread-safe). */
        internal val sharedHttpClient: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        /**
         * Extracts the text content from a Gemini generateContent JSON response.
         * Exposed as a companion function so tests can call it without an API key.
         */
        fun extractText(responseBody: String): String {
            val root = JsonParser.parseString(responseBody).asJsonObject
            val candidates = root.getAsJsonArray("candidates")
                ?: throw IllegalStateException("Gemini response missing 'candidates'")
            check(candidates.size() > 0) { "Gemini returned no candidates" }
            val parts = candidates[0].asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
            val text = parts.joinToString("\n") { it.asJsonObject.get("text")?.asString.orEmpty() }.trim()
            require(text.isNotBlank()) { "Gemini returned empty text" }
            return text
        }
    }
}
