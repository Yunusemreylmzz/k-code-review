package com.kcodereview.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration

/**
 * LLM client for the OpenAI Chat Completions API **and any OpenAI-compatible endpoint**
 * (Ollama, Azure OpenAI, LM Studio, DashScope, etc.).
 */
class OpenAiLlmClient(
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
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt),
            ),
            "temperature" to 0.2,
            "max_tokens" to 4096,
            "response_format" to mapOf("type" to "json_object"),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpointUrl))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build()

        val (status, responseBody) = sender(request)
        check(status in 200..299) {
            "OpenAI API error HTTP $status: ${responseBody.take(500)}"
        }

        return extractText(responseBody)
    }

    companion object {
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
