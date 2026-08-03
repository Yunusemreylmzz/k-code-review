package com.kcodereview.ai

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiClientTest {

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
