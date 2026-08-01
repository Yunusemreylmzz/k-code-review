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
        val text = GeminiClient().extractText(body)
        assertTrue(text.contains("summary"))
    }
}
