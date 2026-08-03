package com.kcodereview.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OpenAiLlmClientTest {

    @Test
    fun `extractText returns message content`() {
        val body = """
            {
              "choices": [
                {
                  "message": { "role": "assistant", "content": "{\"summary\":\"ok\",\"findings\":[]}" },
                  "finish_reason": "stop"
                }
              ]
            }
        """.trimIndent()
        val text = OpenAiLlmClient.extractText(body)
        assertEquals("{\"summary\":\"ok\",\"findings\":[]}", text)
    }

    @Test
    fun `extractText trims whitespace`() {
        val body = """
            {
              "choices": [
                {
                  "message": { "content": "  hello world  " },
                  "finish_reason": "stop"
                }
              ]
            }
        """.trimIndent()
        assertEquals("hello world", OpenAiLlmClient.extractText(body))
    }

    @Test
    fun `extractText throws on empty choices`() {
        assertThrows(IllegalStateException::class.java) {
            OpenAiLlmClient.extractText("""{"choices":[]}""")
        }
    }

    @Test
    fun `extractText throws on missing choices`() {
        assertThrows(IllegalStateException::class.java) {
            OpenAiLlmClient.extractText("""{"id":"chatcmpl-123"}""")
        }
    }
}
