package com.kcodereview.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiClientTest {

    @Test
    fun `sanitizeApiKey strips whitespace from pasted keys`() {
        assertEquals(
            "AIzaSyAbCdEf",
            GeminiClient.sanitizeApiKey("  AIzaSyAb Cd\nEf  "),
        )
    }

    @Test
    fun `formatHttpError explains invalid credentials on 401`() {
        val msg = GeminiClient.formatHttpError(401, """{"error":{"status":"UNAUTHENTICATED"}}""")
        assertTrue(msg.contains("Google AI Studio"))
        assertTrue(msg.contains("AQ."))
    }

    @Test
    fun `formatHttpError explains temporary overload on 503`() {
        val msg = GeminiClient.formatHttpError(503, """{"error":{"status":"UNAVAILABLE","message":"high demand"}}""")
        assertTrue(msg.contains("temporarily unavailable"))
        assertTrue(msg.contains("Commit Anyway") || msg.contains("retry"))
    }

    @Test
    fun `isRetryable covers overload and rate limit`() {
        assertTrue(GeminiClient.isRetryable(503))
        assertTrue(GeminiClient.isRetryable(429))
        assertTrue(!GeminiClient.isRetryable(400))
    }

    @Test
    fun `extractText reads candidate parts`() {
        val body = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "{\"summary\":\"ok\",\"findings\":[]}"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val text = GeminiClient.extractText(body)
        assertTrue(text.contains("summary"))
    }

    @Test
    fun `extractText joins multiple parts`() {
        val body = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "part1"},
                      {"text": "part2"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val text = GeminiClient.extractText(body)
        assertTrue(text.contains("part1"))
        assertTrue(text.contains("part2"))
    }
}
