package com.kcodereview.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptBuilderTest {

    @Test
    fun `truncate keeps short text intact`() {
        assertEquals("abc", PromptBuilder.truncate("abc", 10))
    }

    @Test
    fun `truncate adds marker when over limit`() {
        val result = PromptBuilder.truncate("abcdefghij", 5)
        assertTrue(result.startsWith("abcde"))
        assertTrue(result.contains("truncated"))
    }

    @Test
    fun `default system prompt loads from resources`() {
        val prompt = PromptBuilder.defaultSystemPrompt()
        assertTrue(prompt.contains("SonarQube") || prompt.contains("senior") || prompt.contains("Principal"))
        assertTrue(prompt.contains("howToFix"))
        assertTrue(prompt.contains("fixedCode"))
    }

    @Test
    fun `project rules overlay is appended to default prompt`() {
        val prompt = PromptBuilder.systemPrompt("Custom review rules")
        assertTrue(prompt.contains("howToFix"))
        assertTrue(prompt.contains("PROJECT RULES"))
        assertTrue(prompt.contains("Custom review rules"))
    }
}
