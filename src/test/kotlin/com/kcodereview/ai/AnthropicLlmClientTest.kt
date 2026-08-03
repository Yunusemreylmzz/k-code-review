package com.kcodereview.ai

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnthropicLlmClientTest {

    @Test
    fun `extractText returns text block content`() {
        val body = """
            {
              "content": [
                { "type": "text", "text": "{\"summary\":\"ok\",\"findings\":[]}" }
              ]
            }
        """.trimIndent()
        val text = AnthropicLlmClient.extractText(body)
        assertTrue(text.contains("summary"))
    }

    @Test
    fun `extractText joins multiple text blocks`() {
        val body = """
            {
              "content": [
                { "type": "text", "text": "block1" },
                { "type": "text", "text": "block2" }
              ]
            }
        """.trimIndent()
        val text = AnthropicLlmClient.extractText(body)
        assertTrue(text.contains("block1"))
        assertTrue(text.contains("block2"))
    }

    @Test
    fun `extractText ignores non-text blocks`() {
        val body = """
            {
              "content": [
                { "type": "tool_use", "id": "x" },
                { "type": "text", "text": "hello" }
              ]
            }
        """.trimIndent()
        val text = AnthropicLlmClient.extractText(body)
        assertTrue(text == "hello")
    }

    @Test
    fun `extractText throws on empty content`() {
        assertThrows(IllegalStateException::class.java) {
            AnthropicLlmClient.extractText("""{"content":[]}""")
        }
    }
}
