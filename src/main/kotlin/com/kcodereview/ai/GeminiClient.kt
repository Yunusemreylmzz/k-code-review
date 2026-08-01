package com.kcodereview.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kcodereview.settings.KCodeReviewSettings
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GeminiClient(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build(),
    private val gson: Gson = Gson(),
) {

    fun generate(systemPrompt: String, userPrompt: String): String {
        val settings = KCodeReviewSettings.getInstance()
        val apiKey = settings.getApiKey()
        require(apiKey.isNotBlank()) {
            "Gemini API key is not configured. Open Settings → Tools → K Code Review."
        }

        val model = settings.geminiModel
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val body = JsonObject().apply {
            add("contents", gson.toJsonTree(
                listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(
                            mapOf("text" to "$systemPrompt\n\n---\n\n$userPrompt"),
                        ),
                    ),
                ),
            ))
            add("generationConfig", gson.toJsonTree(
                mapOf(
                    "temperature" to 0.2,
                    "responseMimeType" to "application/json",
                ),
            ))
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "Gemini API error HTTP ${response.statusCode()}: ${response.body().take(500)}",
            )
        }

        return extractText(response.body())
    }

    fun extractText(responseBody: String): String {
        val root = JsonParser.parseString(responseBody).asJsonObject
        val candidates = root.getAsJsonArray("candidates")
            ?: throw IllegalStateException("Gemini response missing candidates")
        if (candidates.size() == 0) {
            throw IllegalStateException("Gemini returned no candidates")
        }
        val parts = candidates[0].asJsonObject
            .getAsJsonObject("content")
            .getAsJsonArray("parts")
        val text = parts.joinToString("\n") { part ->
            part.asJsonObject.get("text")?.asString.orEmpty()
        }.trim()
        require(text.isNotBlank()) { "Gemini returned empty text" }
        return text
    }
}
