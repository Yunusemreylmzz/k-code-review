package com.kcodereview.ai

import com.kcodereview.model.ChangedFile
import com.kcodereview.model.CommitSnapshot
import com.kcodereview.settings.KCodeReviewSettings
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object PromptBuilder {

    fun systemPrompt(customPrompt: String? = null): String {
        val custom = customPrompt ?: runCatching {
            KCodeReviewSettings.getInstance().getCustomPrompt()
        }.getOrDefault("")
        if (custom.isNotBlank()) return custom.trim()
        return defaultSystemPrompt()
    }

    fun userPrompt(
        commit: CommitSnapshot,
        file: ChangedFile,
        maxCharsPerFile: Int = runCatching {
            KCodeReviewSettings.getInstance().maxCharsPerFile
        }.getOrDefault(40_000),
        includePatchContext: Boolean = runCatching {
            KCodeReviewSettings.getInstance().includePatchContext
        }.getOrDefault(true),
    ): String {
        val truncatedContent = truncate(file.content, maxCharsPerFile)
        val builder = StringBuilder()
        builder.appendLine("Commit: ${commit.hash}")
        builder.appendLine("Message: ${commit.message}")
        builder.appendLine("Author: ${commit.author}")
        builder.appendLine("File: ${file.path}")
        builder.appendLine("Change type: ${file.changeType}")
        builder.appendLine()
        builder.appendLine("### File content")
        builder.appendLine("```")
        builder.appendLine(truncatedContent)
        builder.appendLine("```")
        if (includePatchContext && !file.patch.isNullOrBlank()) {
            builder.appendLine()
            builder.appendLine("### Diff patch")
            builder.appendLine("```diff")
            builder.appendLine(truncate(file.patch, maxCharsPerFile / 2))
            builder.appendLine("```")
        }
        return builder.toString()
    }

    fun truncate(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "\n\n/* ... truncated for review size limits ... */"
    }

    fun defaultSystemPrompt(): String {
        val stream = PromptBuilder::class.java.getResourceAsStream("/prompts/default-review-prompt.txt")
            ?: return FALLBACK_PROMPT
        return stream.use { InputStreamReader(it, StandardCharsets.UTF_8).readText() }
    }

    private const val FALLBACK_PROMPT =
        "You are a senior code reviewer. Return JSON with summary and findings array."
}
