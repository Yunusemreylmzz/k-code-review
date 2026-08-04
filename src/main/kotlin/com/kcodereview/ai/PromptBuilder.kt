package com.kcodereview.ai

import com.kcodereview.model.ChangedFile
import com.kcodereview.model.CommitSnapshot
import com.kcodereview.settings.KCodeReviewSettings
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object PromptBuilder {

    fun systemPrompt(customPrompt: String? = null): String {
        val base = defaultSystemPrompt()
        val overlay = customPrompt ?: runCatching {
            KCodeReviewSettings.getInstance().getCustomPrompt()
        }.getOrDefault("")
        if (overlay.isBlank()) return base
        // Remote file is project rules — append, never replace the JSON review contract.
        return buildString {
            append(base)
            appendLine()
            appendLine()
            appendLine("### PROJECT RULES (additional constraints)")
            appendLine(overlay.trim())
        }
    }

    fun userPrompt(
        commit: CommitSnapshot,
        file: ChangedFile,
        maxCharsPerFile: Int = runCatching {
            KCodeReviewSettings.getInstance().maxCharsPerFile
        }.getOrDefault(12_000),
        includePatchContext: Boolean = runCatching {
            KCodeReviewSettings.getInstance().includePatchContext
        }.getOrDefault(true),
    ): String {
        val builder = StringBuilder()
        builder.appendLine("Commit: ${commit.hash}")
        builder.appendLine("Message: ${commit.message}")
        builder.appendLine("Author: ${commit.author}")
        builder.appendLine("File: ${file.path}")
        builder.appendLine("Change type: ${file.changeType}")
        builder.appendLine()

        val patch = file.patch?.trim().orEmpty()
        // Prefer diff-only when available — much faster and commit-scoped.
        if (includePatchContext && patch.isNotBlank() && patch.length >= 40) {
            builder.appendLine("### Diff patch (review these changes; use surrounding lines only as context)")
            builder.appendLine("```diff")
            builder.appendLine(truncate(patch, maxCharsPerFile))
            builder.appendLine("```")
            // Small local context window when the file is not huge.
            val contentBudget = (maxCharsPerFile / 3).coerceAtLeast(2_000)
            if (file.content.isNotBlank() && file.content.length <= contentBudget * 2) {
                builder.appendLine()
                builder.appendLine("### File excerpt (context)")
                builder.appendLine("```")
                builder.appendLine(truncate(file.content, contentBudget))
                builder.appendLine("```")
            }
        } else {
            builder.appendLine("### File content")
            builder.appendLine("```")
            builder.appendLine(truncate(file.content, maxCharsPerFile))
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
