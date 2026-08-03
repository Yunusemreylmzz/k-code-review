package com.kcodereview.ai

/**
 * Common interface for all LLM provider clients.
 *
 * Implementations must be stateless (constructed per-call by [LlmClientFactory])
 * and thread-safe.
 */
interface LlmClient {
    /**
     * Sends [systemPrompt] and [userPrompt] to the LLM and returns the raw text response.
     * The response is expected to be a JSON string (reviews return structured JSON).
     *
     * @throws IllegalStateException if the HTTP response is not 2xx or the body is empty.
     */
    fun generate(systemPrompt: String, userPrompt: String): String
}
